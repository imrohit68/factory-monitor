package com.example.system.web;

import com.example.system.config.ApplicationOperationMode;
import com.example.system.dto.DashboardPlaybackSyncClearDto;
import com.example.system.dto.DashboardPlaybackSyncDto;
import com.example.system.service.OrchestrationService;
import com.example.system.service.DashboardPlaybackSyncService;
import com.example.system.service.SimulationInputService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/dashboard")
@RequiredArgsConstructor
public class DashboardApiController {

    private final OrchestrationService orchestration;
    private final ApplicationOperationMode applicationOperationMode;
    private final SimulationInputService simulationInputService;
    private final DashboardPlaybackSyncService dashboardPlaybackSyncService;

    @GetMapping
    public ResponseEntity<Map<String, Object>> dashboard() {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore().mustRevalidate())
                .body(orchestration.buildDashboardApiResponse());
    }

    @PostMapping("/playback-sync")
    public ResponseEntity<Void> publishPlaybackSync(@RequestBody DashboardPlaybackSyncDto body) {
        dashboardPlaybackSyncService.publish(body);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/playback-sync/clear")
    public ResponseEntity<Void> clearPlaybackSync(@RequestBody DashboardPlaybackSyncClearDto body) {
        dashboardPlaybackSyncService.clear(body);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/simulation/toggle/{slotId}")
    public ResponseEntity<Void> toggleSimulation(@PathVariable Long slotId) {
        if (!applicationOperationMode.isMaintenanceMode()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        try {
            simulationInputService.toggleBitForSlot(slotId);
            return ResponseEntity.noContent().build();
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }
}
