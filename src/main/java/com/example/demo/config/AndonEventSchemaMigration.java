package com.example.demo.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Renames {@code start_time} → {@code event_time} and copies data if Hibernate added an empty
 * {@code event_time} beside legacy {@code start_time}.
 */
@Component
@Order(1)
public class AndonEventSchemaMigration implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AndonEventSchemaMigration.class);

    private final JdbcTemplate jdbc;

    public AndonEventSchemaMigration(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void run(ApplicationArguments args) {
        try {
            List<String> colNames =
                    jdbc.query(
                            "SELECT COLUMN_NAME FROM INFORMATION_SCHEMA.COLUMNS "
                                    + "WHERE UPPER(TABLE_NAME) = 'ANDON_EVENT_LOG'",
                            (rs, row) -> rs.getString(1).toUpperCase());
            boolean hasStart = colNames.stream().anyMatch("START_TIME"::equals);
            boolean hasEvent = colNames.stream().anyMatch("EVENT_TIME"::equals);
            if (hasStart && !hasEvent) {
                jdbc.execute("ALTER TABLE andon_event_log RENAME COLUMN start_time TO event_time");
                log.info("Renamed column andon_event_log.start_time -> event_time");
            } else if (hasStart && hasEvent) {
                jdbc.update("UPDATE andon_event_log SET event_time = start_time WHERE event_time IS NULL");
                jdbc.execute("ALTER TABLE andon_event_log DROP COLUMN start_time");
                log.info("Merged start_time into event_time and dropped start_time");
            }
            jdbc.execute("ALTER TABLE andon_event_log DROP COLUMN IF EXISTS open_event_id");
        } catch (Exception e) {
            log.debug("Andon event_time migration skipped: {}", e.getMessage());
        }
    }
}
