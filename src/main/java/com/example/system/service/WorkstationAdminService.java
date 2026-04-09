package com.example.system.service;

import com.example.system.config.ModbusProperties;
import com.example.system.domain.Workstation;
import com.example.system.domain.WorkstationRole;
import com.example.system.domain.WorkstationSlot;
import com.example.system.dto.SlotAudioPatch;
import com.example.system.dto.WorkstationForm;
import com.example.system.dto.WorkstationFormPageData;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class WorkstationAdminService {

    private final WorkstationService workstationService;
    private final ModbusProperties modbusProperties;
    private final AudioStorageService audioStorageService;

    /** Slot lists keyed by workstation id for the admin list table. */
    public Map<Long, List<WorkstationSlot>> slotRowsForWorkstations(List<Workstation> workstations) {
        Map<Long, List<WorkstationSlot>> slotRows = new LinkedHashMap<>();
        for (Workstation w : workstations) {
            slotRows.put(w.getId(), workstationService.getSlotsOrderedElq(w));
        }
        return slotRows;
    }

    public WorkstationFormPageData formPageData(Long currentWorkstationId, Long selectedPlaceAfterId) {
        int maxBit = modbusProperties.getInputRegisterCount() * 16 - 1;
        int maxRelay = modbusProperties.getRelayChannelsPerSlave();
        List<Workstation> all = workstationService.findAllOrdered();
        List<Workstation> placementOptions =
                all.stream()
                        .filter(w -> currentWorkstationId == null || !w.getId().equals(currentWorkstationId))
                        .toList();
        return new WorkstationFormPageData(
                maxBit, maxRelay, placementOptions, !placementOptions.isEmpty(), selectedPlaceAfterId);
    }

    /** Populate form from entity for edit. */
    public WorkstationForm formFromWorkstation(Workstation w) {
        WorkstationForm form = new WorkstationForm();
        form.setId(w.getId());
        form.setName(w.getName());
        Map<WorkstationRole, WorkstationSlot> byRole = new EnumMap<>(WorkstationRole.class);
        for (WorkstationSlot s : w.getSlots()) {
            byRole.put(s.getRole(), s);
        }
        WorkstationSlot e = byRole.get(WorkstationRole.ENGINEER);
        WorkstationSlot l = byRole.get(WorkstationRole.LEADER);
        WorkstationSlot q = byRole.get(WorkstationRole.QUALITY);
        if (e != null) {
            form.setEngInputBit(e.getInputBitIndex());
            form.setEngSlave(e.getOutputSlaveId());
            form.setEngRelay(e.getOutputChannel());
            form.setCurrentEngAudioPath(e.getAudioPath());
            form.setCurrentEngAudioOriginalName(e.getAudioOriginalName());
        }
        if (l != null) {
            form.setLeadInputBit(l.getInputBitIndex());
            form.setLeadSlave(l.getOutputSlaveId());
            form.setLeadRelay(l.getOutputChannel());
            form.setCurrentLeadAudioPath(l.getAudioPath());
            form.setCurrentLeadAudioOriginalName(l.getAudioOriginalName());
        }
        if (q != null) {
            form.setQcInputBit(q.getInputBitIndex());
            form.setQcSlave(q.getOutputSlaveId());
            form.setQcRelay(q.getOutputChannel());
            form.setCurrentQcAudioPath(q.getAudioPath());
            form.setCurrentQcAudioOriginalName(q.getAudioOriginalName());
        }
        form.setPlaceAfterWorkstationId(defaultPlaceAfterForEdit(w.getId()));
        return form;
    }

    /**
     * Restores {@code current*AudioPath} and {@code current*AudioOriginalName} from the database so the edit form
     * still shows the right labels after a failed POST (those fields are not bound from the request).
     */
    public void refreshFormAudioFieldsFromDb(WorkstationForm form) {
        if (form.getId() == null) {
            return;
        }
        workstationService
                .findByIdWithSlots(form.getId())
                .ifPresent(
                        w -> {
                            WorkstationForm fresh = formFromWorkstation(w);
                            form.setCurrentEngAudioPath(fresh.getCurrentEngAudioPath());
                            form.setCurrentEngAudioOriginalName(fresh.getCurrentEngAudioOriginalName());
                            form.setCurrentLeadAudioPath(fresh.getCurrentLeadAudioPath());
                            form.setCurrentLeadAudioOriginalName(fresh.getCurrentLeadAudioOriginalName());
                            form.setCurrentQcAudioPath(fresh.getCurrentQcAudioPath());
                            form.setCurrentQcAudioOriginalName(fresh.getCurrentQcAudioOriginalName());
                        });
    }

    public Long defaultPlaceAfterForEdit(Long id) {
        List<Workstation> list = workstationService.findAllOrdered();
        Long prevId = null;
        for (Workstation w : list) {
            if (w.getId().equals(id)) {
                return prevId;
            }
            prevId = w.getId();
        }
        return null;
    }

    /** Full validation for save (bits, in-form uniqueness, cross-workstation conflicts). */
    public Optional<String> validateWorkstationForm(WorkstationForm form) {
        int maxBit = modbusProperties.getInputRegisterCount() * 16 - 1;
        Optional<String> err = validateBits(form, maxBit);
        if (err.isPresent()) {
            return err;
        }
        err = validateUniqueOutputsWithinForm(form);
        if (err.isPresent()) {
            return err;
        }

        Map<WorkstationRole, Long> excludeIds = new EnumMap<>(WorkstationRole.class);
        if (form.getId() != null) {
            workstationService
                    .findByIdWithSlots(form.getId())
                    .ifPresent(
                            ws -> {
                                for (WorkstationSlot s : ws.getSlots()) {
                                    excludeIds.put(s.getRole(), s.getId());
                                }
                            });
        }
        return validateUniqueness(form, excludeIds);
    }

    /**
     * Saves workstation from admin form; returns empty on success or a user-facing error message.
     * Does not validate — call {@link #validateWorkstationForm(WorkstationForm)} first.
     */
    @Transactional
    public Optional<String> persistWorkstationFromForm(
            WorkstationForm form,
            MultipartFile engAudioFile,
            MultipartFile leadAudioFile,
            MultipartFile qcAudioFile) {
        Workstation ws;
        if (form.getId() == null) {
            ws = new Workstation();
        } else {
            ws =
                    workstationService
                            .findByIdWithSlots(form.getId())
                            .orElseThrow(() -> new IllegalArgumentException("Unknown id"));
        }

        ws.setName(form.getName() != null && !form.getName().isBlank() ? form.getName().trim() : null);
        if (form.getId() == null) {
            ws.setSortOrder(workstationService.nextSortOrder());
        }
        ws.setEnabled(true);

        String prevE = null;
        String prevL = null;
        String prevQ = null;
        if (form.getId() != null) {
            for (WorkstationSlot s : ws.getSlots()) {
                switch (s.getRole()) {
                    case ENGINEER -> prevE = s.getAudioPath();
                    case LEADER -> prevL = s.getAudioPath();
                    case QUALITY -> prevQ = s.getAudioPath();
                }
            }
        }
        boolean isNew = form.getId() == null;
        SlotAudioPatch engAudio;
        SlotAudioPatch leadAudio;
        SlotAudioPatch qcAudio;
        try {
            engAudio = resolveSlotAudio(form.isClearEngAudio(), engAudioFile, prevE, isNew);
            leadAudio = resolveSlotAudio(form.isClearLeadAudio(), leadAudioFile, prevL, isNew);
            qcAudio = resolveSlotAudio(form.isClearQcAudio(), qcAudioFile, prevQ, isNew);
        } catch (IOException e) {
            return Optional.of("Could not save audio file: " + e.getMessage());
        }

        Workstation saved =
                workstationService.saveWithThreeSlots(
                        ws,
                        form.getEngInputBit(),
                        form.getEngSlave(),
                        form.getEngRelay(),
                        engAudio,
                        form.getLeadInputBit(),
                        form.getLeadSlave(),
                        form.getLeadRelay(),
                        leadAudio,
                        form.getQcInputBit(),
                        form.getQcSlave(),
                        form.getQcRelay(),
                        qcAudio);
        workstationService.moveAfter(saved.getId(), form.getPlaceAfterWorkstationId());
        return Optional.empty();
    }

    private SlotAudioPatch resolveSlotAudio(boolean clear, MultipartFile upload, String previousPath, boolean isNew)
            throws IOException {
        if (clear) {
            return new SlotAudioPatch(null, null, true);
        }
        String uploaded = audioStorageService.storeUpload(upload);
        if (uploaded != null) {
            return new SlotAudioPatch(uploaded, sanitizeAudioOriginalFilename(upload.getOriginalFilename()), true);
        }
        if (!isNew && previousPath != null && !previousPath.isBlank()) {
            return new SlotAudioPatch(previousPath, null, false);
        }
        return new SlotAudioPatch(null, null, false);
    }

    static String sanitizeAudioOriginalFilename(String originalFilename) {
        if (originalFilename == null || originalFilename.isBlank()) {
            return "audio";
        }
        String base = originalFilename.replace('\\', '/');
        int slash = base.lastIndexOf('/');
        if (slash >= 0) {
            base = base.substring(slash + 1);
        }
        base = base.trim();
        if (base.isBlank() || base.contains("..") || base.indexOf('/') >= 0 || base.indexOf('\\') >= 0) {
            return "audio";
        }
        if (base.length() > 255) {
            base = base.substring(0, 255);
        }
        return base;
    }

    private Optional<String> validateBits(WorkstationForm form, int maxBit) {
        int[] bits = {form.getEngInputBit(), form.getLeadInputBit(), form.getQcInputBit()};
        String[] names = {"Engineer", "Leader", "Quality Controller"};
        for (int i = 0; i < bits.length; i++) {
            if (bits[i] < 0 || bits[i] > maxBit) {
                return Optional.of(names[i] + " input bit must be 0–" + maxBit);
            }
        }
        if (bits[0] == bits[1] || bits[0] == bits[2] || bits[1] == bits[2]) {
            return Optional.of(
                    "Each input bit can only be used once. Engineering, Leader, and Quality Controller must each use a different bit so one physical input is not wired to multiple roles.");
        }
        int maxRelay = modbusProperties.getRelayChannelsPerSlave();
        int[] relays = {form.getEngRelay(), form.getLeadRelay(), form.getQcRelay()};
        for (int r : relays) {
            if (r < 1 || r > maxRelay) {
                return Optional.of("Relay number must be 1–" + maxRelay + " on each slave");
            }
        }
        int[] slaves = {form.getEngSlave(), form.getLeadSlave(), form.getQcSlave()};
        for (int s : slaves) {
            if (s < 1 || s > 247) {
                return Optional.of("Modbus slave must be 1–247");
            }
        }
        return Optional.empty();
    }

    private Optional<String> validateUniqueOutputsWithinForm(WorkstationForm form) {
        int es = form.getEngSlave();
        int er = form.getEngRelay();
        int ls = form.getLeadSlave();
        int lr = form.getLeadRelay();
        int qs = form.getQcSlave();
        int qr = form.getQcRelay();
        if (es == ls && er == lr) {
            return Optional.of(
                    "Engineering and Leader both use the same output (Modbus slave "
                            + es
                            + ", relay "
                            + er
                            + "). Each role needs its own relay so one stack light or buzzer is not driven by multiple inputs.");
        }
        if (es == qs && er == qr) {
            return Optional.of(
                    "Engineering and Quality Controller both use the same output (Modbus slave "
                            + es
                            + ", relay "
                            + er
                            + "). Each role needs its own relay so one output is not shared across roles.");
        }
        if (ls == qs && lr == qr) {
            return Optional.of(
                    "Leader and Quality Controller both use the same output (Modbus slave "
                            + ls
                            + ", relay "
                            + lr
                            + "). Each role needs its own relay so one output is not shared across roles.");
        }
        return Optional.empty();
    }

    private Optional<String> validateUniqueness(WorkstationForm form, Map<WorkstationRole, Long> excludeIds) {
        Long exE = excludeIds.get(WorkstationRole.ENGINEER);
        Long exL = excludeIds.get(WorkstationRole.LEADER);
        Long exQ = excludeIds.get(WorkstationRole.QUALITY);
        Optional<WorkstationSlot> inE =
                workstationService.findConflictingSlotForInputBit(form.getEngInputBit(), exE);
        if (inE.isPresent()) {
            return Optional.of(inputBitConflictMessage("Engineering", form.getEngInputBit(), inE.get()));
        }
        Optional<WorkstationSlot> inL =
                workstationService.findConflictingSlotForInputBit(form.getLeadInputBit(), exL);
        if (inL.isPresent()) {
            return Optional.of(inputBitConflictMessage("Leader", form.getLeadInputBit(), inL.get()));
        }
        Optional<WorkstationSlot> inQ =
                workstationService.findConflictingSlotForInputBit(form.getQcInputBit(), exQ);
        if (inQ.isPresent()) {
            return Optional.of(inputBitConflictMessage("Quality Controller", form.getQcInputBit(), inQ.get()));
        }
        Optional<WorkstationSlot> outE =
                workstationService.findConflictingSlotForOutput(form.getEngSlave(), form.getEngRelay(), exE);
        if (outE.isPresent()) {
            return Optional.of(
                    outputConflictMessage(
                            "Engineering", form.getEngSlave(), form.getEngRelay(), outE.get()));
        }
        Optional<WorkstationSlot> outL =
                workstationService.findConflictingSlotForOutput(form.getLeadSlave(), form.getLeadRelay(), exL);
        if (outL.isPresent()) {
            return Optional.of(outputConflictMessage("Leader", form.getLeadSlave(), form.getLeadRelay(), outL.get()));
        }
        Optional<WorkstationSlot> outQ =
                workstationService.findConflictingSlotForOutput(form.getQcSlave(), form.getQcRelay(), exQ);
        if (outQ.isPresent()) {
            return Optional.of(
                    outputConflictMessage("Quality Controller", form.getQcSlave(), form.getQcRelay(), outQ.get()));
        }
        return Optional.empty();
    }

    private static String workstationDisplayName(Workstation w) {
        if (w.getName() != null && !w.getName().isBlank()) {
            return "\"" + w.getName().trim() + "\"";
        }
        return "workstation #" + w.getId();
    }

    private static String roleLabel(WorkstationRole role) {
        return switch (role) {
            case ENGINEER -> "Engineering";
            case LEADER -> "Leader";
            case QUALITY -> "Quality Controller";
        };
    }

    private static String inputBitConflictMessage(String roleBeingEdited, int bit, WorkstationSlot other) {
        return roleBeingEdited
                + " cannot use input bit "
                + bit
                + ": that bit is already used by "
                + workstationDisplayName(other.getWorkstation())
                + " ("
                + roleLabel(other.getRole())
                + "). Each input must map to exactly one role.";
    }

    private static String outputConflictMessage(String roleBeingEdited, int slave, int relay, WorkstationSlot other) {
        return roleBeingEdited
                + " cannot use Modbus slave "
                + slave
                + ", relay "
                + relay
                + ": that output is already used by "
                + workstationDisplayName(other.getWorkstation())
                + " ("
                + roleLabel(other.getRole())
                + "). Each relay can only map to one role so outputs are not shared.";
    }
}
