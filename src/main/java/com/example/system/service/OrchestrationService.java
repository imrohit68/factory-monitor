package com.example.system.service;

import com.example.system.config.ApplicationOperationMode;
import com.example.system.config.AppProperties;
import com.example.system.config.ModbusProperties;
import com.example.system.domain.WorkstationSlot;
import com.example.system.dto.DashboardSlotDto;
import com.example.system.dto.DashboardWorkstationDto;
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

@Service
public class OrchestrationService {

    private static final Logger log = LoggerFactory.getLogger(OrchestrationService.class);

    private final ModbusProperties modbus;
    private final ModbusMasterService modbusMaster;
    private final ApplicationOperationMode applicationOperationMode;
    private final EventPersistenceService persistence;
    private final SimulationInputService simulationInputService;
    private final WorkstationService workstationService;
    private final AppProperties appProperties;

    private final ConcurrentMap<Long, ChannelFsm> channelBySlotId = new ConcurrentHashMap<>();
    private final ConcurrentMap<Long, Long> openEventIdsBySlotId = new ConcurrentHashMap<>();

    public OrchestrationService(
            ModbusProperties modbus,
            ModbusMasterService modbusMaster,
            ApplicationOperationMode applicationOperationMode,
            EventPersistenceService persistence,
            SimulationInputService simulationInputService,
            WorkstationService workstationService,
            AppProperties appProperties) {
        this.modbus = modbus;
        this.modbusMaster = modbusMaster;
        this.applicationOperationMode = applicationOperationMode;
        this.persistence = persistence;
        this.simulationInputService = simulationInputService;
        this.workstationService = workstationService;
        this.appProperties = appProperties;
    }

    @Scheduled(fixedDelayString = "${system.modbus.poll-interval-ms:5000}")
    public void poll() {
        List<WorkstationSlot> slots = workstationService.getOrchestrationSlots();
        if (slots.isEmpty()) {
            channelBySlotId.clear();
            return;
        }
        int maxBit = slots.stream().mapToInt(WorkstationSlot::getInputBitIndex).max().orElse(0);
        int bitCount = Math.max(modbus.getInputRegisterCount() * 16, maxBit + 1);
        boolean[] bits =
                applicationOperationMode.isMaintenanceMode()
                        ? simulationInputService.readBits(bitCount)
                        : modbusMaster.readInputBits(bitCount);

        Set<Long> activeSlotIds = Set.copyOf(slots.stream().map(WorkstationSlot::getId).toList());
        channelBySlotId.keySet().removeIf(id -> !activeSlotIds.contains(id));

        if (modbusMaster.isConnected()) {
            for (WorkstationSlot slot : slots) {
                int idx = slot.getInputBitIndex();
                boolean energized = idx >= 0 && idx < bits.length && bits[idx];
                modbusMaster.writeRelayOutput(
                        slot.getOutputSlaveId(), slot.getOutputChannel(), energized, true);
            }
        }

        for (WorkstationSlot slot : slots) {
            int idx = slot.getInputBitIndex();
            boolean level = idx >= 0 && idx < bits.length && bits[idx];
            ChannelFsm fsm = channelBySlotId.computeIfAbsent(slot.getId(), id -> new ChannelFsm());
            fsm.onSample(level, () -> openLine(slot), () -> closeLine(slot));
        }
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
        body.put("workstations", buildDashboardWorkstations());
        body.put("modbusConnected", isModbusConnected());
        body.put("modbusError", getModbusLastError());
        body.put("mode", applicationOperationMode.getCurrentMode().name());
        body.put("alertRepeatIntervalMinutes", appProperties.getDashboardAlertRepeatIntervalMinutes());
        body.put("alertMaxRepeats", appProperties.getDashboardAlertMaxRepeats());
        return body;
    }
}
