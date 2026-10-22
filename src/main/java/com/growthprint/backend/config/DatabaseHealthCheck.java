package com.growthprint.backend.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;

@Component
@ConditionalOnProperty(name = "DATABASE_URL")
public class DatabaseHealthCheck {

    private static final Logger log = LoggerFactory.getLogger(DatabaseHealthCheck.class);
    private final JdbcTemplate jdbcTemplate;

    public DatabaseHealthCheck(DataSource dataSource) {
        this.jdbcTemplate = new JdbcTemplate(dataSource);
    }

    @EventListener(ApplicationReadyEvent.class)
    public void checkDatabaseConnection() {
        try {
            Integer result = jdbcTemplate.queryForObject("SELECT 1", Integer.class);
            if (result != null && result == 1) {
                log.info("Database connectivity check: SUCCESS - PostgreSQL connection is working");
            } else {
                log.warn("Database connectivity check: UNEXPECTED - SELECT 1 returned unexpected result");
            }
        } catch (Exception e) {
            log.error("Database connectivity check: FAILED - Unable to execute SELECT 1", e);
            throw new IllegalStateException("Database connection check failed. Please verify DATABASE_URL, DATABASE_USERNAME, and DATABASE_PASSWORD are correctly configured.", e);
        }
    }
}
