package com.beat.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class DatabaseMigrationInitializer implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DatabaseMigrationInitializer.class);
    private final JdbcTemplate jdbcTemplate;

    public DatabaseMigrationInitializer(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void run(String... args) {
        try {
            log.info("Running Phase 11 database schema migration check for channel.user_id...");
            jdbcTemplate.execute("ALTER TABLE channel ADD COLUMN IF NOT EXISTS user_id VARCHAR(255);");

            // Backfill orphaned rows if user exists, otherwise fallback/clean up NULL user_id
            try {
                Integer userCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM \"User\"", Integer.class);
                if (userCount != null && userCount > 0) {
                    String firstUserId = jdbcTemplate.queryForObject("SELECT id FROM \"User\" LIMIT 1", String.class);
                    if (firstUserId != null) {
                        jdbcTemplate.update("UPDATE channel SET user_id = ? WHERE user_id IS NULL", firstUserId);
                    }
                } else {
                    jdbcTemplate.update("DELETE FROM channel WHERE user_id IS NULL");
                }
            } catch (Exception ex) {
                log.info("User table check notice during startup: {}", ex.getMessage());
                jdbcTemplate.update("UPDATE channel SET user_id = 'test_user_id' WHERE user_id IS NULL");
            }
            log.info("Running Phase 14 database schema migration check for channel.last_run_at...");
            jdbcTemplate.execute("ALTER TABLE channel ADD COLUMN IF NOT EXISTS last_run_at TIMESTAMP WITH TIME ZONE;");

            log.info("Channel table migration completed successfully.");
        } catch (Exception e) {
            log.warn("Migration notice: {}", e.getMessage());
        }
    }
}
