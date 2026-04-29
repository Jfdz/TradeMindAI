package com.tradingsaas.marketdata.config;

import static org.junit.jupiter.api.Assertions.assertTrue;

import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.testcontainers.DockerClientFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.PostgreSQLContainer;

class FlywayMigrationTest {

    @Test
    void migrationsCreateExpectedTablesAndIndexes() {
        Assumptions.assumeTrue(DockerClientFactory.instance().isDockerAvailable(), "Docker is required for migration validation");

        try (PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
                .withDatabaseName("trading_saas")
                .withUsername("trading_user")
                .withPassword("dev_password_change_in_prod")) {
            postgres.start();

            Flyway flyway = Flyway.configure()
                    .dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                    .locations("classpath:db/migration")
                    .schemas("market_data")
                    .createSchemas(true)
                    .load();
            flyway.migrate();

            DataSource dataSource = new org.springframework.jdbc.datasource.DriverManagerDataSource(
                    postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
            JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);

            assertTrue(tableExists(jdbcTemplate, "symbols"));
            assertTrue(tableExists(jdbcTemplate, "stock_prices"));
            assertTrue(tableExists(jdbcTemplate, "technical_indicators"));
            assertTrue(tableExists(jdbcTemplate, "market_data_outbox"));
            assertTrue(indexExists(jdbcTemplate, "idx_symbols_active"));
            assertTrue(indexExists(jdbcTemplate, "idx_stock_prices_symbol_date"));
            assertTrue(indexExists(jdbcTemplate, "idx_technical_indicators_symbol_date"));
            assertTrue(indexExists(jdbcTemplate, "idx_market_data_outbox_unpublished_created_at"));
        }
    }

    private static boolean tableExists(JdbcTemplate jdbcTemplate, String tableName) {
        Integer count = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM information_schema.tables
                WHERE table_schema = 'market_data'
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
                WHERE schemaname = 'market_data'
                  AND indexname = ?
                """,
                Integer.class,
                indexName);
        return count != null && count > 0;
    }
}
