package com.example.system.service;

import com.example.system.config.AppProperties;
import com.example.system.repository.EventLogRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Service
@RequiredArgsConstructor
public class EventLogPurgeService {

    private static final Logger log = LoggerFactory.getLogger(EventLogPurgeService.class);

    private final EventLogRepository eventLogRepository;
    private final AppProperties appProperties;

    /** Once per day at 03:00 in the JVM default time zone. */
    @Scheduled(cron = "0 0 3 * * *")
    @Transactional
    public void purgeExpiredEventRows() {
        int days = appProperties.getEventLogPurgeRetentionDays();
        if (days <= 0) {
            return;
        }
        Instant cutoff = Instant.now().minus(days, ChronoUnit.DAYS);
        int removed = eventLogRepository.deleteByEventTimeBefore(cutoff);
        if (removed > 0) {
            log.info("Removed {} event_log row(s) older than {} days (strictly before {})", removed, days, cutoff);
        }
    }
}
