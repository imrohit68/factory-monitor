package com.example.system.repository;

import com.example.system.domain.EventRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface EventLogRepository extends JpaRepository<EventRecord, Long> {

    /** Events whose {@link EventRecord#getEventTime()} falls in {@code [startInclusive, endExclusive)}. */
    List<EventRecord> findByEventTimeGreaterThanEqualAndEventTimeLessThanOrderByEventTimeAsc(
            Instant startInclusive, Instant endExclusive);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from EventRecord e where e.eventTime < :before")
    int deleteByEventTimeBefore(@Param("before") Instant before);
}
