package com.example.system.service;

import com.example.system.domain.Workstation;
import com.example.system.domain.WorkstationRole;
import com.example.system.domain.WorkstationSlot;
import com.example.system.dto.SlotAudioPatch;
import com.example.system.repository.WorkstationRepository;
import com.example.system.repository.WorkstationSlotRepository;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

@Service
public class WorkstationService {

    private final WorkstationRepository workstationRepository;
    private final WorkstationSlotRepository slotRepository;

    private final AtomicReference<List<WorkstationSlot>> orchestrationSlots =
            new AtomicReference<>(List.of());

    public WorkstationService(WorkstationRepository workstationRepository, WorkstationSlotRepository slotRepository) {
        this.workstationRepository = workstationRepository;
        this.slotRepository = slotRepository;
    }

    @PostConstruct
    void loadCache() {
        refreshOrchestrationCache();
    }

    @Transactional(readOnly = true)
    public List<Workstation> findAllOrdered() {
        return workstationRepository.findAllByOrderBySortOrderAscIdAsc();
    }

    @Transactional(readOnly = true)
    public int nextSortOrder() {
        List<Workstation> list = findAllOrdered();
        if (list.isEmpty()) {
            return 0;
        }
        return list.get(list.size() - 1).getSortOrder() + 1;
    }

    @Transactional(readOnly = true)
    public Optional<Workstation> findByIdWithSlots(Long id) {
        return workstationRepository.findByIdWithSlots(id);
    }

    /** Slots used by Modbus orchestration (enabled workstations only). */
    public List<WorkstationSlot> getOrchestrationSlots() {
        return orchestrationSlots.get();
    }

    public void refreshOrchestrationCache() {
        List<WorkstationSlot> list = slotRepository.findSlotsForOrchestration();
        orchestrationSlots.set(Collections.unmodifiableList(list));
    }

    @Transactional
    public void deleteById(Long id) {
        workstationRepository.deleteById(id);
        refreshOrchestrationCache();
    }

    @Transactional
    public Workstation save(Workstation entity) {
        Workstation saved = workstationRepository.save(entity);
        refreshOrchestrationCache();
        return saved;
    }

    @Transactional
    public Workstation saveWithThreeSlots(
            Workstation workstation,
            int engBit,
            int engSlave,
            int engRelay,
            SlotAudioPatch engAudio,
            int leadBit,
            int leadSlave,
            int leadRelay,
            SlotAudioPatch leadAudio,
            int qcBit,
            int qcSlave,
            int qcRelay,
            SlotAudioPatch qcAudio) {
        Map<WorkstationRole, WorkstationSlot> byRole = new EnumMap<>(WorkstationRole.class);
        for (WorkstationSlot s : workstation.getSlots()) {
            byRole.put(s.getRole(), s);
        }
        upsertSlot(workstation, byRole, WorkstationRole.ENGINEER, engBit, engSlave, engRelay, engAudio);
        upsertSlot(workstation, byRole, WorkstationRole.LEADER, leadBit, leadSlave, leadRelay, leadAudio);
        upsertSlot(workstation, byRole, WorkstationRole.QUALITY, qcBit, qcSlave, qcRelay, qcAudio);
        return save(workstation);
    }

    /**
     * Reuses existing {@link WorkstationSlot} rows when updating so Hibernate issues UPDATEs instead of
     * DELETE+INSERT. Replacing the collection would insert new rows before deletes flush, violating
     * unique constraints on {@code input_bit_index} / output while old rows still exist.
     */
    private static void upsertSlot(
            Workstation ws,
            Map<WorkstationRole, WorkstationSlot> byRole,
            WorkstationRole role,
            int bit,
            int slave,
            int relay,
            SlotAudioPatch audio) {
        WorkstationSlot s = byRole.get(role);
        if (s == null) {
            s = new WorkstationSlot();
            s.setWorkstation(ws);
            s.setRole(role);
            ws.getSlots().add(s);
        }
        s.setInputBitIndex(bit);
        s.setOutputSlaveId(slave);
        s.setOutputChannel(relay);
        String path = audio.audioPath();
        s.setAudioPath(path != null && !path.isBlank() ? path : null);
        if (audio.touchOriginalName()) {
            String name = audio.audioOriginalName();
            s.setAudioOriginalName(name != null && !name.isBlank() ? name.trim() : null);
        }
    }

    @Transactional(readOnly = true)
    public Optional<WorkstationSlot> findConflictingSlotForInputBit(int bit, Long excludeSlotId) {
        return slotRepository.findConflictingSlotByInputBit(bit, excludeSlotId);
    }

    @Transactional(readOnly = true)
    public Optional<WorkstationSlot> findConflictingSlotForOutput(int slave, int channel, Long excludeSlotId) {
        return slotRepository.findConflictingSlotByOutput(slave, channel, excludeSlotId);
    }

    /** Slots in role order (Engineer, Leader, Quality Controller), then by id when roles match. */
    public List<WorkstationSlot> getSlotsOrderedElq(Workstation w) {
        List<WorkstationSlot> list = new ArrayList<>(w.getSlots());
        list.sort(
                Comparator.comparingInt((WorkstationSlot s) -> s.getRole().ordinal())
                        .thenComparingLong(s -> s.getId() == null ? 0L : s.getId()));
        return list;
    }

    @Transactional
    public void moveAfter(Long workstationId, Long afterWorkstationId) {
        List<Workstation> ordered = new ArrayList<>(findAllOrdered());
        if (ordered.isEmpty()) {
            return;
        }
        Workstation target = null;
        for (Workstation w : ordered) {
            if (w.getId().equals(workstationId)) {
                target = w;
                break;
            }
        }
        if (target == null) {
            return;
        }
        ordered.remove(target);

        int insertIdx = 0;
        if (afterWorkstationId != null) {
            insertIdx = ordered.size();
            for (int i = 0; i < ordered.size(); i++) {
                if (ordered.get(i).getId().equals(afterWorkstationId)) {
                    insertIdx = i + 1;
                    break;
                }
            }
        }
        ordered.add(insertIdx, target);

        for (int i = 0; i < ordered.size(); i++) {
            Workstation w = ordered.get(i);
            if (w.getSortOrder() != i) {
                w.setSortOrder(i);
                workstationRepository.save(w);
            }
        }
        refreshOrchestrationCache();
    }
}
