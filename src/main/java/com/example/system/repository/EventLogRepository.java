package com.example.system.repository;

import com.example.system.domain.EventRecord;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;

public interface EventLogRepository extends JpaRepository<EventRecord, Long> {

    /** Events whose {@link EventRecord#getEventTime()} falls in {@code [startInclusive, endExclusive)}. */
    List<EventRecord> findByEventTimeGreaterThanEqualAndEventTimeLessThanOrderByEventTimeAsc(
            Instant startInclusive, Instant endExclusive);
}
