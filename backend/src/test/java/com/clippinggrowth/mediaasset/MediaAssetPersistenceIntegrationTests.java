package com.clippinggrowth.mediaasset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

import java.lang.reflect.Field;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.EntityManager;
import org.hibernate.annotations.Immutable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

@Testcontainers
@SpringBootTest
class MediaAssetPersistenceIntegrationTests {

    private static final String VALID_SHA256 = "a".repeat(64);

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18-alpine");

    @Autowired
    private MediaAssetRepository mediaAssetRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private EntityManager entityManager;

    @BeforeEach
    void setUp() {
        jdbcTemplate.update("DELETE FROM media_assets");
        jdbcTemplate.update("DELETE FROM source_videos");
        jdbcTemplate.update("DELETE FROM creators");
    }

    @Test
    void v4AppliesTheExactMediaAssetTableShapeAndOwnershipIndex() {
        Long recordedMigration = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM flyway_schema_history
                WHERE version = '4'
                  AND script = 'V4__create_media_assets.sql'
                  AND success
                """, Long.class);
        assertThat(recordedMigration).isEqualTo(1L);

        List<Map<String, Object>> columns = jdbcTemplate.queryForList("""
                SELECT column_name, data_type, character_maximum_length, is_nullable
                FROM information_schema.columns
                WHERE table_schema = 'public'
                  AND table_name = 'media_assets'
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
                        tuple("source_video_id", "uuid", null, "NO"),
                        tuple("role", "character varying", 32, "NO"),
                        tuple("storage_key", "character varying", 255, "NO"),
                        tuple("original_filename", "character varying", 255, "YES"),
                        tuple("content_type", "character varying", 255, "YES"),
                        tuple("size_bytes", "bigint", null, "NO"),
                        tuple("sha256", "character varying", 64, "NO"),
                        tuple("created_at", "timestamp with time zone", null, "NO"));

        Map<String, Object> foreignKey = jdbcTemplate.queryForMap("""
                SELECT constraint_name, delete_rule
                FROM information_schema.referential_constraints
                WHERE constraint_schema = 'public'
                  AND constraint_name = 'media_assets_source_video_fk'
                """);
        assertThat(foreignKey)
                .containsEntry("constraint_name", "media_assets_source_video_fk")
                .containsEntry("delete_rule", "RESTRICT");

        String ownershipIndex = jdbcTemplate.queryForObject("""
                SELECT indexdef
                FROM pg_indexes
                WHERE schemaname = 'public'
                  AND tablename = 'media_assets'
                  AND indexname = 'media_assets_source_video_role_unique'
                """, String.class);
        assertThat(ownershipIndex)
                .contains("UNIQUE")
                .contains("(source_video_id, role)");
    }

    @Test
    @Transactional
    void persistsGeneratedImmutableMediaAssetAndStoresOriginalAsAString() throws Exception {
        UUID sourceVideoId = insertOwnedSourceVideo();
        MediaAsset asset = new MediaAsset(
                sourceVideoId,
                MediaAssetRole.ORIGINAL,
                MediaAssetStorageKey.forId(UUID.randomUUID()),
                "recording.mp4",
                "video/mp4",
                2048,
                VALID_SHA256);

        mediaAssetRepository.saveAndFlush(asset);

        assertThat(asset.getId()).isNotNull();
        assertThat(asset.getCreatedAt()).isNotNull();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT role FROM media_assets WHERE id = ?", String.class, asset.getId()))
                .isEqualTo("ORIGINAL");

        Field contentType = MediaAsset.class.getDeclaredField("contentType");
        contentType.setAccessible(true);
        contentType.set(asset, "application/octet-stream");
        entityManager.flush();
        entityManager.clear();

        MediaAsset reloaded = mediaAssetRepository.findById(asset.getId()).orElseThrow();
        assertThat(reloaded.getSourceVideoId()).isEqualTo(sourceVideoId);
        assertThat(reloaded.getRole()).isEqualTo(MediaAssetRole.ORIGINAL);
        assertThat(reloaded.getStorageKey()).startsWith("media-assets/");
        assertThat(reloaded.getOriginalFilename()).isEqualTo("recording.mp4");
        assertThat(reloaded.getContentType()).isEqualTo("video/mp4");
        assertThat(reloaded.getSizeBytes()).isEqualTo(2048);
        assertThat(reloaded.getSha256()).isEqualTo(VALID_SHA256);
        assertThat(MediaAsset.class).hasAnnotation(Immutable.class);
        assertThat(Arrays.stream(MediaAsset.class.getDeclaredFields())
                        .map(field -> field.getAnnotation(Column.class))
                        .filter(java.util.Objects::nonNull))
                .allMatch(column -> !column.updatable());
    }

    @Test
    void persistsNullableOriginalFilenameAndContentType() {
        UUID sourceVideoId = insertOwnedSourceVideo();
        MediaAsset asset = new MediaAsset(
                sourceVideoId,
                MediaAssetRole.ORIGINAL,
                MediaAssetStorageKey.forId(UUID.randomUUID()),
                null,
                null,
                1,
                VALID_SHA256);

        MediaAsset persisted = mediaAssetRepository.saveAndFlush(asset);

        assertThat(persisted.getOriginalFilename()).isNull();
        assertThat(persisted.getContentType()).isNull();
    }

    @Test
    void databaseRejectsInvalidRoleBlankMetadataNonpositiveSizeAndInvalidSha256() {
        UUID sourceVideoId = insertOwnedSourceVideo();

        assertInvalidAsset(sourceVideoId, "FUTURE", "key-role", null, null, 1, VALID_SHA256,
                "media_assets_role_valid");
        assertInvalidAsset(sourceVideoId, "ORIGINAL", " \t ", null, null, 1, VALID_SHA256,
                "media_assets_storage_key_not_blank");
        assertInvalidAsset(sourceVideoId, "ORIGINAL", "key-filename", " \t ", null, 1,
                VALID_SHA256, "media_assets_original_filename_not_blank");
        assertInvalidAsset(sourceVideoId, "ORIGINAL", "key-type", null, " \t ", 1,
                VALID_SHA256, "media_assets_content_type_not_blank");
        assertInvalidAsset(sourceVideoId, "ORIGINAL", "key-size", null, null, 0, VALID_SHA256,
                "media_assets_size_bytes_positive");
        assertInvalidAsset(sourceVideoId, "ORIGINAL", "key-hash-uppercase", null, null, 1,
                "A".repeat(64), "media_assets_sha256_lowercase_hex");
        assertInvalidAsset(sourceVideoId, "ORIGINAL", "key-hash-length", null, null, 1,
                "a".repeat(63), "media_assets_sha256_lowercase_hex");
    }

    @Test
    void databaseEnforcesSourceVideoForeignKeyAndRestrictsParentDeletion() {
        assertThatThrownBy(() -> insertMediaAsset(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "ORIGINAL",
                "missing-source-video",
                null,
                null,
                1,
                VALID_SHA256))
                .hasMessageContaining("media_assets_source_video_fk");

        UUID sourceVideoId = insertOwnedSourceVideo();
        insertMediaAsset(
                UUID.randomUUID(), sourceVideoId, "ORIGINAL", "referenced", null, null, 1,
                VALID_SHA256);

        assertThatThrownBy(() -> jdbcTemplate.update(
                "DELETE FROM source_videos WHERE id = ?", sourceVideoId))
                .hasMessageContaining("media_assets_source_video_fk");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM source_videos WHERE id = ?", Long.class, sourceVideoId))
                .isEqualTo(1L);
    }

    @Test
    void databaseEnforcesUniqueStorageKeyWithoutMakingSha256Unique() {
        UUID firstSourceVideoId = insertOwnedSourceVideo();
        UUID secondSourceVideoId = insertOwnedSourceVideo();
        insertMediaAsset(
                UUID.randomUUID(), firstSourceVideoId, "ORIGINAL", "same-key", null, null, 1,
                VALID_SHA256);

        assertThatThrownBy(() -> insertMediaAsset(
                UUID.randomUUID(), secondSourceVideoId, "ORIGINAL", "same-key", null, null, 1,
                VALID_SHA256))
                .hasMessageContaining("media_assets_storage_key_unique");

        insertMediaAsset(
                UUID.randomUUID(), secondSourceVideoId, "ORIGINAL", "different-key", null, null, 1,
                VALID_SHA256);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM media_assets WHERE sha256 = ?", Long.class, VALID_SHA256))
                .isEqualTo(2L);
    }

    @Test
    void databaseAllowsOnlyOneOriginalPerSourceVideo() {
        UUID sourceVideoId = insertOwnedSourceVideo();
        insertMediaAsset(
                UUID.randomUUID(), sourceVideoId, "ORIGINAL", "first", null, null, 1,
                VALID_SHA256);

        assertThatThrownBy(() -> insertMediaAsset(
                UUID.randomUUID(), sourceVideoId, "ORIGINAL", "second", null, null, 1,
                VALID_SHA256))
                .hasMessageContaining("media_assets_source_video_role_unique");
    }

    private UUID insertOwnedSourceVideo() {
        UUID creatorId = UUID.randomUUID();
        UUID sourceVideoId = UUID.randomUUID();
        Instant timestamp = Instant.parse("2026-08-17T12:00:00Z");
        jdbcTemplate.update("""
                INSERT INTO creators (id, name, created_at, updated_at)
                VALUES (?, 'Creator', ?, ?)
                """, creatorId, Timestamp.from(timestamp), Timestamp.from(timestamp));
        jdbcTemplate.update("""
                INSERT INTO source_videos
                    (id, creator_id, title, origin_url, created_at, updated_at)
                VALUES (?, ?, 'Source video', NULL, ?, ?)
                """, sourceVideoId, creatorId, Timestamp.from(timestamp), Timestamp.from(timestamp));
        return sourceVideoId;
    }

    private void assertInvalidAsset(
            UUID sourceVideoId,
            String role,
            String storageKey,
            String originalFilename,
            String contentType,
            long sizeBytes,
            String sha256,
            String constraintName) {
        assertThatThrownBy(() -> insertMediaAsset(
                UUID.randomUUID(),
                sourceVideoId,
                role,
                storageKey,
                originalFilename,
                contentType,
                sizeBytes,
                sha256))
                .hasMessageContaining(constraintName);
    }

    private void insertMediaAsset(
            UUID id,
            UUID sourceVideoId,
            String role,
            String storageKey,
            String originalFilename,
            String contentType,
            long sizeBytes,
            String sha256) {
        jdbcTemplate.update("""
                INSERT INTO media_assets (
                    id, source_video_id, role, storage_key, original_filename,
                    content_type, size_bytes, sha256, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP)
                """,
                id,
                sourceVideoId,
                role,
                storageKey,
                originalFilename,
                contentType,
                sizeBytes,
                sha256);
    }
}
