package com.clippinggrowth;

import static org.assertj.core.api.Assertions.assertThat;

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
                WHERE version = '1'
                  AND script = 'V1__initialize_schema.sql'
                  AND success
                """, Long.class);
        assertThat(recordedMigrations).isEqualTo(1L);
    }
}
