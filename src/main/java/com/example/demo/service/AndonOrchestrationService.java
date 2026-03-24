package com.example.demo.service;

import com.example.demo.config.ModbusProperties;
import com.example.demo.domain.WorkstationSlot;
import com.example.demo.dto.DashboardSlotDto;
import com.example.demo.dto.DashboardWorkstationDto;
import com.example.demo.modbus.ModbusMasterService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.stream.Collectors;

@Service
public class AndonOrchestrationService {

    private static final Logger log = LoggerFactory.getLogger(AndonOrchestrationService.class);

    private final ModbusProperties modbus;
    private final ModbusMasterService modbusMaster;
    private final AndonPersistenceService persistence;
    private final WorkstationService workstationService;

    private final ConcurrentMap<Long, ChannelFsm> channelBySlotId = new ConcurrentHashMap<>();
    private final ConcurrentMap<Long, Long> openEventIdsBySlotId = new ConcurrentHashMap<>();

    public AndonOrchestrationService(
            ModbusProperties modbus,
            ModbusMasterService modbusMaster,
            AndonPersistenceService persistence,
            WorkstationService workstationService) {
        this.modbus = modbus;
        this.modbusMaster = modbusMaster;
        this.persistence = persistence;
        this.workstationService = workstationService;
    }

    @Scheduled(fixedDelayString = "${andon.modbus.poll-interval-ms:5000}")
    public void poll() {
        List<WorkstationSlot> slots = workstationService.getOrchestrationSlots();
        if (slots.isEmpty()) {
            channelBySlotId.clear();
            return;
        }
        int maxBit = slots.stream().mapToInt(WorkstationSlot::getInputBitIndex).max().orElse(0);
        int bitCount = Math.max(modbus.getInputRegisterCount() * 16, maxBit + 1);
        boolean[] bits = modbusMaster.readInputBits(bitCount);

        Set<Long> activeSlotIds = slots.stream().map(WorkstationSlot::getId).collect(Collectors.toSet());
        channelBySlotId.keySet().removeIf(id -> !activeSlotIds.contains(id));

        for (WorkstationSlot slot : slots) {
            int idx = slot.getInputBitIndex();
            boolean level = idx >= 0 && idx < bits.length && bits[idx];
            ChannelFsm fsm = channelBySlotId.computeIfAbsent(slot.getId(), id -> new ChannelFsm());
            final WorkstationSlot s = slot;
            fsm.onSample(level, () -> openLine(s), () -> closeLine(s));
        }
    }

    void openLine(WorkstationSlot slot) {
        Long id =
                persistence.openEvent(
                        slot.getId(),
                        slot.getInputBitIndex(),
                        slot.getOutputSlaveId(),
                        slot.getOutputChannel());
        openEventIdsBySlotId.put(slot.getId(), id);
        modbusMaster.writeRelayOutput(slot.getOutputSlaveId(), slot.getOutputChannel(), true);
        log.info(
                "OPEN slotId={} inputBit={} -> slave={} relay={} eventId={}",
                slot.getId(),
                slot.getInputBitIndex(),
                slot.getOutputSlaveId(),
                slot.getOutputChannel(),
                id);
    }

    void closeLine(WorkstationSlot slot) {
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
        modbusMaster.writeRelayOutput(slot.getOutputSlaveId(), slot.getOutputChannel(), false);
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
}
