package com.example.system.service;

import com.example.system.config.ApplicationOperationMode;
import com.example.system.config.AppProperties;
import com.example.system.config.CooperativeShutdownGate;
import com.example.system.config.ModbusProperties;
import com.example.system.domain.WorkstationSlot;
import com.example.system.dto.DashboardActiveAlertSlot;
import com.example.system.dto.DashboardSlotDto;
import com.example.system.dto.DashboardWorkstationDto;
import com.example.system.dto.ModbusTrafficSnapshot;
import com.example.system.modbus.ModbusMasterService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicReference;

@Service
public class OrchestrationService {

    private static final Logger log = LoggerFactory.getLogger(OrchestrationService.class);

    private final ModbusProperties modbus;
    private final ModbusMasterService modbusMaster;
    private final CooperativeShutdownGate cooperativeShutdownGate;
    private final ApplicationOperationMode applicationOperationMode;
    private final EventPersistenceService persistence;
    private final SimulationInputService simulationInputService;
    private final WorkstationService workstationService;
    private final AppProperties appProperties;
    private final DashboardPlaybackSyncService dashboardPlaybackSyncService;
    private final DashboardAlertAudioDirectorService dashboardAlertAudioDirectorService;

    private final ConcurrentMap<Long, ChannelFsm> channelBySlotId = new ConcurrentHashMap<>();
    private final ConcurrentMap<Long, Long> openEventIdsBySlotId = new ConcurrentHashMap<>();
    private final AtomicReference<ModbusTrafficSnapshot> modbusTrafficSnapshot =
            new AtomicReference<>(ModbusTrafficSnapshot.initial());

    public OrchestrationService(
            ModbusProperties modbus,
            ModbusMasterService modbusMaster,
            CooperativeShutdownGate cooperativeShutdownGate,
            ApplicationOperationMode applicationOperationMode,
            EventPersistenceService persistence,
            SimulationInputService simulationInputService,
            WorkstationService workstationService,
            AppProperties appProperties,
            DashboardPlaybackSyncService dashboardPlaybackSyncService,
            DashboardAlertAudioDirectorService dashboardAlertAudioDirectorService) {
        this.modbus = modbus;
        this.modbusMaster = modbusMaster;
        this.cooperativeShutdownGate = cooperativeShutdownGate;
        this.applicationOperationMode = applicationOperationMode;
        this.persistence = persistence;
        this.simulationInputService = simulationInputService;
        this.workstationService = workstationService;
        this.appProperties = appProperties;
        this.dashboardPlaybackSyncService = dashboardPlaybackSyncService;
        this.dashboardAlertAudioDirectorService = dashboardAlertAudioDirectorService;
    }

    @Scheduled(fixedDelayString = "${system.modbus.poll-interval-ms:5000}")
    public void poll() {
        if (cooperativeShutdownGate.isShuttingDown()) {
            return;
        }
        List<WorkstationSlot> slots = workstationService.getOrchestrationSlots();
        if (slots.isEmpty()) {
            channelBySlotId.clear();
            modbusTrafficSnapshot.set(ModbusTrafficSnapshot.idleNow());
            return;
        }
        int maxBit = slots.stream().mapToInt(WorkstationSlot::getInputBitIndex).max().orElse(0);
        int bitCount = Math.max(modbus.getInputRegisterCount() * 16, maxBit + 1);
        boolean readTx;
        boolean readRx;
        boolean[] bits;
        if (applicationOperationMode.isMaintenanceMode()) {
            bits = simulationInputService.readBits(bitCount);
            readTx = true;
            readRx = true;
        } else {
            var readResult = modbusMaster.readInputBitsWithOutcome(bitCount);
            bits = readResult.bits();
            readTx = readResult.attempted();
            readRx = readResult.responseOk();
            if (readResult.attempted() && !readResult.responseOk()) {
                log.warn("Modbus input read failed this poll.");
            }
        }

        Set<Long> activeSlotIds = Set.copyOf(slots.stream().map(WorkstationSlot::getId).toList());
        channelBySlotId.keySet().removeIf(id -> !activeSlotIds.contains(id));

        // read bits → FSM (dashboard / persistence / alerts) → relay writes → Modbus TX/RX lamp snapshot (last)
        for (WorkstationSlot slot : slots) {
            int idx = slot.getInputBitIndex();
            boolean level = idx >= 0 && idx < bits.length && bits[idx];
            ChannelFsm fsm = channelBySlotId.computeIfAbsent(slot.getId(), id -> new ChannelFsm());
            fsm.onSample(level, () -> openLine(slot), () -> closeLine(slot));
        }

        int writesTransmitted = 0;
        int writesFailed = 0;
        if (modbusMaster.isConnected()) {
            for (WorkstationSlot slot : slots) {
                int idx = slot.getInputBitIndex();
                boolean energized = idx >= 0 && idx < bits.length && bits[idx];
                var wr =
                        modbusMaster.writeRelayOutput(
                                slot.getOutputSlaveId(), slot.getOutputChannel(), energized, true);
                if (wr.transmitted()) {
                    writesTransmitted++;
                    if (!wr.responseOk()) {
                        writesFailed++;
                        log.warn(
                                "Relay write failed (slave={} relay={}). Loop skipping because write failed — remaining relay writes skipped this poll.",
                                slot.getOutputSlaveId(),
                                slot.getOutputChannel());
                        break;
                    }
                }
            }
        }

        boolean writeTx = writesTransmitted > 0;
        boolean writeRx = writesTransmitted > 0 && writesFailed == 0;
        modbusTrafficSnapshot.set(
                new ModbusTrafficSnapshot(
                        System.currentTimeMillis(), readTx, readRx, writeTx, writeRx));
    }

    private void openLine(WorkstationSlot slot) {
        Long id =
                persistence.openEvent(
                        slot.getId(),
                        slot.getInputBitIndex(),
                        slot.getOutputSlaveId(),
                        slot.getOutputChannel());
        openEventIdsBySlotId.put(slot.getId(), id);
        log.info(
                "OPEN slotId={} inputBit={} -> slave={} relay={} eventId={}",
                slot.getId(),
                slot.getInputBitIndex(),
                slot.getOutputSlaveId(),
                slot.getOutputChannel(),
                id);
    }

    private void closeLine(WorkstationSlot slot) {
        Long openEventId = openEventIdsBySlotId.remove(slot.getId());
        Long closeEventId = null;
        if (openEventId != null) {
            closeEventId =
                    persistence.insertCloseEvent(
                            slot.getId(),
                            slot.getInputBitIndex(),
                            slot.getOutputSlaveId(),
                            slot.getOutputChannel());
        }
        log.info(
                "CLOSED slotId={} inputBit={} -> slave={} relay={} openEventId={} closeEventId={}",
                slot.getId(),
                slot.getInputBitIndex(),
                slot.getOutputSlaveId(),
                slot.getOutputChannel(),
                openEventId,
                closeEventId);
    }

    @Transactional(readOnly = true)
    public List<DashboardWorkstationDto> buildDashboardWorkstations() {
        List<DashboardWorkstationDto> out = new ArrayList<>();
        for (var workstation : workstationService.findAllOrdered()) {
            if (!workstation.isEnabled()) {
                continue;
            }
            List<DashboardSlotDto> cells = new ArrayList<>();
            for (WorkstationSlot s : workstationService.getSlotsOrderedElq(workstation)) {
                ChannelFsm f = channelBySlotId.get(s.getId());
                boolean active = f != null && f.isInputActive();
                String audioUrl = s.getAudioPath();
                if (audioUrl != null && audioUrl.isBlank()) {
                    audioUrl = null;
                }
                cells.add(
                        new DashboardSlotDto(
                                s.getId(),
                                s.getRole(),
                                s.getInputBitIndex(),
                                s.getOutputSlaveId(),
                                s.getOutputChannel(),
                                active,
                                active ? openEventIdsBySlotId.get(s.getId()) : null,
                                audioUrl));
            }
            out.add(
                    new DashboardWorkstationDto(
                            workstation.getId(),
                            workstation.getName() != null ? workstation.getName() : "",
                            cells));
        }
        return out;
    }

    public boolean isModbusConnected() {
        return modbusMaster.isConnected();
    }

    public String getModbusLastError() {
        return modbusMaster.getLastError();
    }

    /** Payload for {@code GET /api/dashboard} (workstations + Modbus status). */
    @Transactional(readOnly = true)
    public Map<String, Object> buildDashboardApiResponse() {
        Map<String, Object> body = new HashMap<>();
        List<DashboardWorkstationDto> workstations = buildDashboardWorkstations();
        body.put("workstations", workstations);
        body.put("modbusConnected", isModbusConnected());
        body.put("modbusError", getModbusLastError());
        body.put("mode", applicationOperationMode.getCurrentMode().name());
        body.put("alertRepeatIntervalMinutes", appProperties.getDashboardAlertRepeatIntervalMinutes());
        body.put("alertMaxRepeats", appProperties.getDashboardAlertMaxRepeats());
        body.put("alertGapMs", appProperties.getDashboardAlertGapMs());
        body.put("serverTimeMs", System.currentTimeMillis());

        ModbusTrafficSnapshot traffic = modbusTrafficSnapshot.get();
        Map<String, Object> modbusTraffic = new HashMap<>();
        modbusTraffic.put("pollCompletedAtMs", traffic.pollCompletedAtMs());
        modbusTraffic.put("readTx", traffic.readTx());
        modbusTraffic.put("readRx", traffic.readRx());
        modbusTraffic.put("writeTx", traffic.writeTx());
        modbusTraffic.put("writeRx", traffic.writeRx());
        body.put("modbusTraffic", modbusTraffic);

        List<DashboardActiveAlertSlot> alertSlots = new ArrayList<>();
        for (DashboardWorkstationDto w : workstations) {
            for (DashboardSlotDto s : w.slots()) {
                if (s.active() && s.audioUrl() != null && !s.audioUrl().isBlank() && s.openEventId() != null) {
                    alertSlots.add(
                            new DashboardActiveAlertSlot(
                                    String.valueOf(s.slotId()),
                                    s.audioUrl(),
                                    String.valueOf(s.openEventId()),
                                    s.inputBitIndex()));
                }
            }
        }
        body.put(
                "alertAudio",
                dashboardAlertAudioDirectorService.reconcileAndBuildInstruction(
                        alertSlots, System.currentTimeMillis()));
        body.put("alertPlaybackLastEnded", dashboardPlaybackSyncService.lastClipEndSnapshot());
        return body;
    }
}
