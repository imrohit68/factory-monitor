package com.example.demo.config;

import com.example.demo.domain.Workstation;
import com.example.demo.repository.WorkstationRepository;
import com.example.demo.service.WorkstationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Seeds the original 3×16 default grid when {@code workstation} is empty: 16 columns, each with
 * Engineer / Leader / Quality slots mapping input bits 0–47 to relays 1–48 in order (relays 1–32 on
 * {@link AndonAppProperties#getDefaultRelaySlaveFirst()}, 33–48 on {@link AndonAppProperties#getDefaultRelaySlaveSecond()}).
 */
@Service
public class DefaultWorkstationSeedService {

    private static final Logger log = LoggerFactory.getLogger(DefaultWorkstationSeedService.class);

    /** Columns in the default factory grid (16 × 3 roles = 48 slots). */
    public static final int DEFAULT_COLUMN_COUNT = 16;

    private final AndonAppProperties app;
    private final WorkstationRepository workstationRepository;
    private final WorkstationService workstationService;

    public DefaultWorkstationSeedService(
            AndonAppProperties app,
            WorkstationRepository workstationRepository,
            WorkstationService workstationService) {
        this.app = app;
        this.workstationRepository = workstationRepository;
        this.workstationService = workstationService;
    }

    @Transactional
    public void ensureDefaultWorkstations() {
        if (!app.isSeedDefaultWorkstations()) {
            return;
        }
        if (workstationRepository.count() > 0) {
            return;
        }
        int first = app.getDefaultRelaySlaveFirst();
        int second = app.getDefaultRelaySlaveSecond();
        if (first == second) {
            log.warn(
                    "Skipping default workstation seed: andon.default-relay-slave-first and "
                            + "andon.default-relay-slave-second must differ (both are {}).",
                    first);
            return;
        }
        for (int k = 0; k < DEFAULT_COLUMN_COUNT; k++) {
            Workstation w = new Workstation();
            w.setName("WC" + (k + 1));
            w.setSortOrder(k);
            w.setEnabled(true);
            // E: bits 0–15 → relays 1–16; L: bits 16–31 → relays 17–32; Q: bits 32–47 → relays 33–48
            workstationService.saveWithThreeSlots(
                    w,
                    k,
                    first,
                    k + 1,
                    null,
                    16 + k,
                    first,
                    k + 17,
                    null,
                    32 + k,
                    second,
                    k + 1,
                    null);
        }
        log.info(
                "Seeded {} default workstations (48 inputs → relays 1–32 on slave {}, 33–48 on slave {}).",
                DEFAULT_COLUMN_COUNT,
                first,
                second);
    }

}
