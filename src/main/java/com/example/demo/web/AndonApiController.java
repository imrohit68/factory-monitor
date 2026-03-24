package com.example.demo.web;

import com.example.demo.domain.AndonEvent;
import com.example.demo.domain.AndonEventStatus;
import com.example.demo.repository.AndonEventRepository;
import com.example.demo.service.AndonOrchestrationService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/andon")
public class AndonApiController {

    private final AndonOrchestrationService orchestration;
    private final AndonEventRepository events;

    public AndonApiController(AndonOrchestrationService orchestration, AndonEventRepository events) {
        this.orchestration = orchestration;
        this.events = events;
    }

    @GetMapping("/dashboard")
    public Map<String, Object> dashboard() {
        Map<String, Object> body = new HashMap<>();
        body.put("workstations", orchestration.buildDashboardWorkstations());
        body.put("modbusConnected", orchestration.isModbusConnected());
        body.put("modbusError", orchestration.getModbusLastError());
        return body;
    }

    @GetMapping("/events/recent")
    public List<AndonEvent> recentEvents() {
        return events.findTop50ByOrderByEventTimeDesc();
    }

    /**
     * Cumulative call duration (seconds) by relay output slave: each CLOSED row pairs with the latest
     * prior OPEN on the same {@code mapping_id}; only closes with {@code event_time} in the window count.
     */
    @GetMapping("/stats/downtime-by-output-slave")
    public List<Map<String, Object>> downtimeByOutputSlave(@RequestParam(defaultValue = "30") int days) {
        Instant since = Instant.now().minus(days, ChronoUnit.DAYS);
        return events.sumDowntimeByOutputSlaveSince(
                        AndonEventStatus.OPEN.name(), AndonEventStatus.CLOSED.name(), since)
                .stream()
                .map(row -> {
                    Map<String, Object> m = new HashMap<>();
                    m.put("outputSlaveId", row[0]);
                    m.put("totalSeconds", row[1]);
                    return m;
                })
                .toList();
    }
}
