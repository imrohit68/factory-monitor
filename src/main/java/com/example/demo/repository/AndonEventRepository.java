package com.example.demo.repository;

import com.example.demo.domain.AndonEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface AndonEventRepository extends JpaRepository<AndonEvent, Long> {

    List<AndonEvent> findTop50ByOrderByEventTimeDesc();

    /** Events whose {@link AndonEvent#getEventTime()} falls in {@code [startInclusive, endExclusive)}. */
    List<AndonEvent> findByEventTimeGreaterThanEqualAndEventTimeLessThanOrderByEventTimeAsc(
            Instant startInclusive, Instant endExclusive);

    /**
     * Sum of call durations (CLOSED.time − OPEN.time) per output slave for closes with
     * {@code event_time >= since}. Each CLOSED row pairs with the latest OPEN on the same
     * {@code mapping_id} with strictly earlier {@code event_time}.
     */
    @Query(
            nativeQuery = true,
            value =
                    "SELECT c.output_slave_id, COALESCE(SUM(DATEDIFF('SECOND', o.event_time, c.event_time)), 0) "
                            + "FROM andon_event_log c "
                            + "INNER JOIN andon_event_log o ON o.id = ( "
                            + "  SELECT o2.id FROM andon_event_log o2 "
                            + "  WHERE o2.mapping_id = c.mapping_id AND o2.status = :openStatus "
                            + "    AND o2.event_time < c.event_time "
                            + "  ORDER BY o2.event_time DESC, o2.id DESC FETCH FIRST 1 ROW ONLY ) "
                            + "WHERE c.status = :closedStatus AND c.event_time >= :since "
                            + "GROUP BY c.output_slave_id")
    List<Object[]> sumDowntimeByOutputSlaveSince(
            @Param("openStatus") String openStatus,
            @Param("closedStatus") String closedStatus,
            @Param("since") Instant since);
}
