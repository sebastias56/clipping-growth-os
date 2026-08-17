package com.clippinggrowth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import jakarta.persistence.EntityManagerFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;

@Testcontainers
@SpringBootTest
class ClippingGrowthApplicationTests {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18-alpine");

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    @BeforeEach
    void setUp() {
        jdbcTemplate.update("DELETE FROM media_assets");
        jdbcTemplate.update("DELETE FROM source_videos");
        jdbcTemplate.update("DELETE FROM creators");
    }

    @Test
    void contextLoadsWithPostgreSqlAndAppliesFlywayMigration() {
        assertThat(POSTGRES.isRunning()).isTrue();
        assertThat(entityManagerFactory.isOpen()).isTrue();

        String databaseName = jdbcTemplate.queryForObject("SELECT current_database()", String.class);
        assertThat(databaseName).isEqualTo(POSTGRES.getDatabaseName());

        Boolean verificationTableExists = jdbcTemplate.queryForObject("""
                SELECT to_regclass('public.schema_verification') IS NOT NULL
                """, Boolean.class);
        assertThat(verificationTableExists).isTrue();

        Long verificationRows = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM schema_verification WHERE id = 1
                """, Long.class);
        assertThat(verificationRows).isEqualTo(1L);

        Long recordedMigrations = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM flyway_schema_history
                WHERE ((version = '1' AND script = 'V1__initialize_schema.sql')
                    OR (version = '2' AND script = 'V2__create_creators.sql')
                    OR (version = '3' AND script = 'V3__create_source_videos.sql')
                    OR (version = '4' AND script = 'V4__create_media_assets.sql'))
                  AND success
                """, Long.class);
        assertThat(recordedMigrations).isEqualTo(4L);
    }

    @Test
    void creatorMigrationCreatesExpectedPostgreSqlStructureAndBlankNameConstraint() {
        List<Map<String, Object>> columns = jdbcTemplate.queryForList("""
                SELECT column_name, data_type, character_maximum_length, is_nullable
                FROM information_schema.columns
                WHERE table_schema = 'public'
                  AND table_name = 'creators'
                ORDER BY ordinal_position
                """);

        assertThat(columns)
                .extracting(
                        column -> column.get("column_name"),
                        column -> column.get("data_type"),
                        column -> column.get("character_maximum_length"),
                        column -> column.get("is_nullable"))
                .containsExactly(
                        tuple("id", "uuid", null, "NO"),
                        tuple("name", "character varying", 120, "NO"),
                        tuple("created_at", "timestamp with time zone", null, "NO"),
                        tuple("updated_at", "timestamp with time zone", null, "NO"));

        assertThatThrownBy(() -> jdbcTemplate.update("""
                INSERT INTO creators (id, name, created_at, updated_at)
                VALUES (?, E' \t ', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """, UUID.randomUUID()))
                .hasMessageContaining("creators_name_not_blank");
    }

    @Test
    void sourceVideoMigrationCreatesExpectedStructureForeignKeyAndListingIndex() {
        List<Map<String, Object>> columns = jdbcTemplate.queryForList("""
                SELECT column_name, data_type, character_maximum_length, is_nullable
                FROM information_schema.columns
                WHERE table_schema = 'public'
                  AND table_name = 'source_videos'
                ORDER BY ordinal_position
                """);

        assertThat(columns)
                .extracting(
                        column -> column.get("column_name"),
                        column -> column.get("data_type"),
                        column -> column.get("character_maximum_length"),
                        column -> column.get("is_nullable"))
                .containsExactly(
                        tuple("id", "uuid", null, "NO"),
                        tuple("creator_id", "uuid", null, "NO"),
                        tuple("title", "character varying", 300, "NO"),
                        tuple("origin_url", "character varying", 2048, "YES"),
                        tuple("created_at", "timestamp with time zone", null, "NO"),
                        tuple("updated_at", "timestamp with time zone", null, "NO"));

        Map<String, Object> foreignKey = jdbcTemplate.queryForMap("""
                SELECT constraint_name, delete_rule
                FROM information_schema.referential_constraints
                WHERE constraint_schema = 'public'
                  AND constraint_name = 'source_videos_creator_fk'
                """);
        assertThat(foreignKey)
                .containsEntry("constraint_name", "source_videos_creator_fk")
                .containsEntry("delete_rule", "RESTRICT");

        String indexDefinition = jdbcTemplate.queryForObject("""
                SELECT indexdef
                FROM pg_indexes
                WHERE schemaname = 'public'
                  AND tablename = 'source_videos'
                  AND indexname = 'source_videos_creator_created_id_idx'
                """, String.class);
        assertThat(indexDefinition)
                .contains("(creator_id, created_at DESC, id DESC)");
    }

    @Test
    void sourceVideoDatabaseConstraintsRejectInvalidOwnershipAndBlankValues() {
        UUID creatorId = UUID.randomUUID();
        insertCreator(creatorId);

        assertThatThrownBy(() -> insertSourceVideo(
                UUID.randomUUID(), UUID.randomUUID(), "Missing Creator", null))
                .hasMessageContaining("source_videos_creator_fk");
        assertThatThrownBy(() -> insertSourceVideo(
                UUID.randomUUID(), creatorId, " \t ", null))
                .hasMessageContaining("source_videos_title_not_blank");
        assertThatThrownBy(() -> insertSourceVideo(
                UUID.randomUUID(), creatorId, "Blank URL", " \t "))
                .hasMessageContaining("source_videos_origin_url_not_blank");
    }

    @Test
    void creatorDeletionIsRestrictedWhileReferencedBySourceVideo() {
        UUID creatorId = UUID.randomUUID();
        insertCreator(creatorId);
        insertSourceVideo(UUID.randomUUID(), creatorId, "Referenced", null);

        assertThatThrownBy(() -> jdbcTemplate.update(
                "DELETE FROM creators WHERE id = ?", creatorId))
                .hasMessageContaining("source_videos_creator_fk");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM creators WHERE id = ?", Long.class, creatorId))
                .isEqualTo(1L);
    }

    @Test
    void duplicateSourceVideoTitlesAndOriginUrlsAreAllowed() {
        UUID creatorId = UUID.randomUUID();
        insertCreator(creatorId);
        insertSourceVideo(
                UUID.randomUUID(), creatorId, "Duplicate", "https://example.com/video");
        insertSourceVideo(
                UUID.randomUUID(), creatorId, "Duplicate", "https://example.com/video");

        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM source_videos", Long.class))
                .isEqualTo(2L);
    }

    private void insertCreator(UUID creatorId) {
        jdbcTemplate.update("""
                INSERT INTO creators (id, name, created_at, updated_at)
                VALUES (?, 'Creator', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """, creatorId);
    }

    private void insertSourceVideo(UUID id, UUID creatorId, String title, String originUrl) {
        jdbcTemplate.update("""
                INSERT INTO source_videos
                    (id, creator_id, title, origin_url, created_at, updated_at)
                VALUES (?, ?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """, id, creatorId, title, originUrl);
    }
}
