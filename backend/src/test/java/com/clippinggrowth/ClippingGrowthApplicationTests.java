package com.clippinggrowth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import jakarta.persistence.EntityManagerFactory;
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
                    OR (version = '2' AND script = 'V2__create_creators.sql'))
                  AND success
                """, Long.class);
        assertThat(recordedMigrations).isEqualTo(2L);
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
}
