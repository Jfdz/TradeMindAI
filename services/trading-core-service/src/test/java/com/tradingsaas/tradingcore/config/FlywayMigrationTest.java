package com.tradingsaas.tradingcore.config;

import static org.junit.jupiter.api.Assertions.assertTrue;

import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.PostgreSQLContainer;

class FlywayMigrationTest {

    @Test
    void migrationsCreateSubscriptionUsageLedgerTableAndIndexes() {
        Assumptions.assumeTrue(DockerClientFactory.instance().isDockerAvailable(), "Docker is required for migration validation");

        try (PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
                .withDatabaseName("trading_saas")
                .withUsername("trading_user")
                .withPassword("dev_password_change_in_prod")) {
            postgres.start();

            Flyway flyway = Flyway.configure()
                    .dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                    .locations("classpath:db/migration")
                    .schemas("trading_core")
                    .createSchemas(true)
                    .load();
            flyway.migrate();

            DataSource dataSource = new org.springframework.jdbc.datasource.DriverManagerDataSource(
                    postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
            JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);

            assertTrue(tableExists(jdbcTemplate, "subscription_usage_ledger"));
            assertTrue(indexExists(jdbcTemplate, "idx_subscription_usage_ledger_user_occurred_at"));
            assertTrue(indexExists(jdbcTemplate, "idx_subscription_usage_ledger_feature_occurred_at"));
        }
    }

    private static boolean tableExists(JdbcTemplate jdbcTemplate, String tableName) {
        Integer count = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM information_schema.tables
                WHERE table_schema = 'trading_core'
                  AND table_name = ?
                """,
                Integer.class,
                tableName);
        return count != null && count > 0;
    }

    private static boolean indexExists(JdbcTemplate jdbcTemplate, String indexName) {
        Integer count = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM pg_indexes
                WHERE schemaname = 'trading_core'
                  AND indexname = ?
                """,
                Integer.class,
                indexName);
        return count != null && count > 0;
    }
}
