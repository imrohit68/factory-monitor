package com.example.demo.web;

import com.example.demo.config.ModbusProperties;
import com.example.demo.domain.Workstation;
import com.example.demo.domain.WorkstationRole;
import com.example.demo.domain.WorkstationSlot;
import com.example.demo.service.AudioStorageService;
import com.example.demo.service.WorkstationService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.io.IOException;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Controller
@RequestMapping("/admin/workstations")
public class WorkstationAdminController {

    private final WorkstationService workstationService;
    private final ModbusProperties modbusProperties;
    private final AudioStorageService audioStorageService;

    public WorkstationAdminController(
            WorkstationService workstationService,
            ModbusProperties modbusProperties,
            AudioStorageService audioStorageService) {
        this.workstationService = workstationService;
        this.modbusProperties = modbusProperties;
        this.audioStorageService = audioStorageService;
    }

    @GetMapping
    public String list(Model model) {
        List<Workstation> workstations = workstationService.findAllOrdered();
        model.addAttribute("workstations", workstations);
        Map<Long, List<WorkstationSlot>> slotRows = new LinkedHashMap<>();
        for (Workstation w : workstations) {
            slotRows.put(w.getId(), workstationService.getSlotsOrderedElq(w));
        }
        model.addAttribute("slotRows", slotRows);
        return "admin/workstations";
    }

    @GetMapping("/new")
    public String createForm(Model model) {
        WorkstationForm form = new WorkstationForm();
        model.addAttribute("form", form);
        addAdminCommonModel(model, null, form.getPlaceAfterWorkstationId());
        return "admin/workstation-form";
    }

    @GetMapping("/{id}/edit")
    public String editForm(@PathVariable Long id, Model model) {
        Workstation w =
                workstationService
                        .findByIdWithSlots(id)
                        .orElseThrow(() -> new IllegalArgumentException("Unknown id"));
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
            form.setEngAudioUrlManual(manualFromPath(e.getAudioPath()));
        }
        if (l != null) {
            form.setLeadInputBit(l.getInputBitIndex());
            form.setLeadSlave(l.getOutputSlaveId());
            form.setLeadRelay(l.getOutputChannel());
            form.setCurrentLeadAudioPath(l.getAudioPath());
            form.setLeadAudioUrlManual(manualFromPath(l.getAudioPath()));
        }
        if (q != null) {
            form.setQcInputBit(q.getInputBitIndex());
            form.setQcSlave(q.getOutputSlaveId());
            form.setQcRelay(q.getOutputChannel());
            form.setCurrentQcAudioPath(q.getAudioPath());
            form.setQcAudioUrlManual(manualFromPath(q.getAudioPath()));
        }
        form.setPlaceAfterWorkstationId(defaultPlaceAfterForEdit(w.getId()));
        model.addAttribute("form", form);
        addAdminCommonModel(model, w.getId(), form.getPlaceAfterWorkstationId());
        return "admin/workstation-form";
    }

    private void addAdminCommonModel(Model model, Long currentWorkstationId, Long selectedAfterId) {
        int maxBit = modbusProperties.getInputRegisterCount() * 16 - 1;
        model.addAttribute("maxInputBit", maxBit);
        List<Workstation> all = workstationService.findAllOrdered();
        List<Workstation> placementOptions =
                all.stream()
                        .filter(w -> currentWorkstationId == null || !w.getId().equals(currentWorkstationId))
                        .toList();
        model.addAttribute("placementOptions", placementOptions);
        model.addAttribute("showPlacementControl", !placementOptions.isEmpty());
        model.addAttribute("selectedPlaceAfterId", selectedAfterId);
    }

    private Long defaultPlaceAfterForEdit(Long id) {
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

    /** Prefill manual URL field only for http(s); paths stay in "current" hint. */
    private static String manualFromPath(String path) {
        if (path == null || path.isBlank()) {
            return "";
        }
        if (path.startsWith("http://") || path.startsWith("https://")) {
            return path;
        }
        return "";
    }

    @PostMapping
    public String save(
            @ModelAttribute("form") WorkstationForm form,
            @RequestParam(value = "engAudioFile", required = false) MultipartFile engAudioFile,
            @RequestParam(value = "leadAudioFile", required = false) MultipartFile leadAudioFile,
            @RequestParam(value = "qcAudioFile", required = false) MultipartFile qcAudioFile,
            RedirectAttributes redirectAttributes,
            Model model) {
        int maxBit = modbusProperties.getInputRegisterCount() * 16 - 1;
        if (!validateBits(form, maxBit, model)) {
            addAdminCommonModel(model, form.getId(), form.getPlaceAfterWorkstationId());
            return "admin/workstation-form";
        }
        if (!validateUniqueOutputsWithinForm(form, model)) {
            addAdminCommonModel(model, form.getId(), form.getPlaceAfterWorkstationId());
            return "admin/workstation-form";
        }
        Map<WorkstationRole, Long> excludeIds = new EnumMap<>(WorkstationRole.class);
        Workstation ws;
        if (form.getId() == null) {
            ws = new Workstation();
        } else {
            ws =
                    workstationService
                            .findByIdWithSlots(form.getId())
                            .orElseThrow(() -> new IllegalArgumentException("Unknown id"));
            for (WorkstationSlot s : ws.getSlots()) {
                excludeIds.put(s.getRole(), s.getId());
            }
        }
        if (!validateUniqueness(form, excludeIds, model)) {
            addAdminCommonModel(model, form.getId(), form.getPlaceAfterWorkstationId());
            return "admin/workstation-form";
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
        String engAudio;
        String leadAudio;
        String qcAudio;
        try {
            engAudio =
                    resolveSlotAudio(
                            form.isClearEngAudio(), engAudioFile, form.getEngAudioUrlManual(), prevE, isNew);
            leadAudio =
                    resolveSlotAudio(
                            form.isClearLeadAudio(), leadAudioFile, form.getLeadAudioUrlManual(), prevL, isNew);
            qcAudio =
                    resolveSlotAudio(
                            form.isClearQcAudio(), qcAudioFile, form.getQcAudioUrlManual(), prevQ, isNew);
        } catch (IOException e) {
            model.addAttribute("errorMessage", "Could not save audio file: " + e.getMessage());
            addAdminCommonModel(model, form.getId(), form.getPlaceAfterWorkstationId());
            return "admin/workstation-form";
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
        redirectAttributes.addFlashAttribute("message", "Workstation saved");
        return "redirect:/admin/workstations";
    }

    private String resolveSlotAudio(
            boolean clear,
            MultipartFile upload,
            String manualUrl,
            String previousPath,
            boolean isNew)
            throws IOException {
        if (clear) {
            return null;
        }
        String uploaded = audioStorageService.storeUpload(upload);
        if (uploaded != null) {
            return uploaded;
        }
        if (manualUrl != null && !manualUrl.isBlank()) {
            return manualUrl.trim();
        }
        if (!isNew && previousPath != null && !previousPath.isBlank()) {
            return previousPath;
        }
        return null;
    }

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        workstationService.deleteById(id);
        redirectAttributes.addFlashAttribute("message", "Workstation deleted");
        return "redirect:/admin/workstations";
    }

    private boolean validateBits(WorkstationForm form, int maxBit, Model model) {
        int[] bits = {form.getEngInputBit(), form.getLeadInputBit(), form.getQcInputBit()};
        String[] names = {"Engineer", "Leader", "Quality"};
        for (int i = 0; i < bits.length; i++) {
            if (bits[i] < 0 || bits[i] > maxBit) {
                model.addAttribute("errorMessage", names[i] + " input bit must be 0–" + maxBit);
                return false;
            }
        }
        if (bits[0] == bits[1] || bits[0] == bits[2] || bits[1] == bits[2]) {
            model.addAttribute(
                    "errorMessage",
                    "Each input bit can only be used once. Engineering, Leader, and Quality must each use a different bit so one physical input is not wired to multiple roles.");
            return false;
        }
        int[] relays = {form.getEngRelay(), form.getLeadRelay(), form.getQcRelay()};
        for (int r : relays) {
            if (r < 1 || r > 32) {
                model.addAttribute("errorMessage", "Relay number must be 1–32 on each slave");
                return false;
            }
        }
        int[] slaves = {form.getEngSlave(), form.getLeadSlave(), form.getQcSlave()};
        for (int s : slaves) {
            if (s < 1 || s > 247) {
                model.addAttribute("errorMessage", "Modbus slave must be 1–247");
                return false;
            }
        }
        return true;
    }

    /**
     * Same form can assign duplicate (slave, relay) to two roles; DB check only sees persisted rows, so we
     * enforce one-to-one input→output pairs within the three roles here.
     */
    private boolean validateUniqueOutputsWithinForm(WorkstationForm form, Model model) {
        int es = form.getEngSlave();
        int er = form.getEngRelay();
        int ls = form.getLeadSlave();
        int lr = form.getLeadRelay();
        int qs = form.getQcSlave();
        int qr = form.getQcRelay();
        if (es == ls && er == lr) {
            model.addAttribute(
                    "errorMessage",
                    "Engineering and Leader both use the same output (Modbus slave "
                            + es
                            + ", relay "
                            + er
                            + "). Each role needs its own relay so one stack light or buzzer is not driven by multiple inputs.");
            return false;
        }
        if (es == qs && er == qr) {
            model.addAttribute(
                    "errorMessage",
                    "Engineering and Quality both use the same output (Modbus slave "
                            + es
                            + ", relay "
                            + er
                            + "). Each role needs its own relay so one output is not shared across roles.");
            return false;
        }
        if (ls == qs && lr == qr) {
            model.addAttribute(
                    "errorMessage",
                    "Leader and Quality both use the same output (Modbus slave "
                            + ls
                            + ", relay "
                            + lr
                            + "). Each role needs its own relay so one output is not shared across roles.");
            return false;
        }
        return true;
    }

    private boolean validateUniqueness(WorkstationForm form, Map<WorkstationRole, Long> excludeIds, Model model) {
        Long exE = excludeIds.get(WorkstationRole.ENGINEER);
        Long exL = excludeIds.get(WorkstationRole.LEADER);
        Long exQ = excludeIds.get(WorkstationRole.QUALITY);
        Optional<WorkstationSlot> inE =
                workstationService.findConflictingSlotForInputBit(form.getEngInputBit(), exE);
        if (inE.isPresent()) {
            model.addAttribute(
                    "errorMessage",
                    inputBitConflictMessage("Engineering", form.getEngInputBit(), inE.get()));
            return false;
        }
        Optional<WorkstationSlot> inL =
                workstationService.findConflictingSlotForInputBit(form.getLeadInputBit(), exL);
        if (inL.isPresent()) {
            model.addAttribute(
                    "errorMessage",
                    inputBitConflictMessage("Leader", form.getLeadInputBit(), inL.get()));
            return false;
        }
        Optional<WorkstationSlot> inQ =
                workstationService.findConflictingSlotForInputBit(form.getQcInputBit(), exQ);
        if (inQ.isPresent()) {
            model.addAttribute(
                    "errorMessage",
                    inputBitConflictMessage("Quality", form.getQcInputBit(), inQ.get()));
            return false;
        }
        Optional<WorkstationSlot> outE =
                workstationService.findConflictingSlotForOutput(form.getEngSlave(), form.getEngRelay(), exE);
        if (outE.isPresent()) {
            model.addAttribute(
                    "errorMessage",
                    outputConflictMessage(
                            "Engineering", form.getEngSlave(), form.getEngRelay(), outE.get()));
            return false;
        }
        Optional<WorkstationSlot> outL =
                workstationService.findConflictingSlotForOutput(form.getLeadSlave(), form.getLeadRelay(), exL);
        if (outL.isPresent()) {
            model.addAttribute(
                    "errorMessage",
                    outputConflictMessage("Leader", form.getLeadSlave(), form.getLeadRelay(), outL.get()));
            return false;
        }
        Optional<WorkstationSlot> outQ =
                workstationService.findConflictingSlotForOutput(form.getQcSlave(), form.getQcRelay(), exQ);
        if (outQ.isPresent()) {
            model.addAttribute(
                    "errorMessage",
                    outputConflictMessage("Quality", form.getQcSlave(), form.getQcRelay(), outQ.get()));
            return false;
        }
        return true;
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
            case QUALITY -> "Quality";
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

    public static class WorkstationForm {

        private Long id;
        private String name;
        private Long placeAfterWorkstationId;

        private int engInputBit;
        private int engSlave = 2;
        private int engRelay = 1;
        private String engAudioUrlManual = "";
        private boolean clearEngAudio;
        private String currentEngAudioPath;

        private int leadInputBit;
        private int leadSlave = 2;
        private int leadRelay = 1;
        private String leadAudioUrlManual = "";
        private boolean clearLeadAudio;
        private String currentLeadAudioPath;

        private int qcInputBit;
        private int qcSlave = 2;
        private int qcRelay = 1;
        private String qcAudioUrlManual = "";
        private boolean clearQcAudio;
        private String currentQcAudioPath;

        public Long getId() {
            return id;
        }

        public void setId(Long id) {
            this.id = id;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public Long getPlaceAfterWorkstationId() {
            return placeAfterWorkstationId;
        }

        public void setPlaceAfterWorkstationId(Long placeAfterWorkstationId) {
            this.placeAfterWorkstationId = placeAfterWorkstationId;
        }

        public int getEngInputBit() {
            return engInputBit;
        }

        public void setEngInputBit(int engInputBit) {
            this.engInputBit = engInputBit;
        }

        public int getEngSlave() {
            return engSlave;
        }

        public void setEngSlave(int engSlave) {
            this.engSlave = engSlave;
        }

        public int getEngRelay() {
            return engRelay;
        }

        public void setEngRelay(int engRelay) {
            this.engRelay = engRelay;
        }

        public String getEngAudioUrlManual() {
            return engAudioUrlManual;
        }

        public void setEngAudioUrlManual(String engAudioUrlManual) {
            this.engAudioUrlManual = engAudioUrlManual;
        }

        public boolean isClearEngAudio() {
            return clearEngAudio;
        }

        public void setClearEngAudio(boolean clearEngAudio) {
            this.clearEngAudio = clearEngAudio;
        }

        public String getCurrentEngAudioPath() {
            return currentEngAudioPath;
        }

        public void setCurrentEngAudioPath(String currentEngAudioPath) {
            this.currentEngAudioPath = currentEngAudioPath;
        }

        public int getLeadInputBit() {
            return leadInputBit;
        }

        public void setLeadInputBit(int leadInputBit) {
            this.leadInputBit = leadInputBit;
        }

        public int getLeadSlave() {
            return leadSlave;
        }

        public void setLeadSlave(int leadSlave) {
            this.leadSlave = leadSlave;
        }

        public int getLeadRelay() {
            return leadRelay;
        }

        public void setLeadRelay(int leadRelay) {
            this.leadRelay = leadRelay;
        }

        public String getLeadAudioUrlManual() {
            return leadAudioUrlManual;
        }

        public void setLeadAudioUrlManual(String leadAudioUrlManual) {
            this.leadAudioUrlManual = leadAudioUrlManual;
        }

        public boolean isClearLeadAudio() {
            return clearLeadAudio;
        }

        public void setClearLeadAudio(boolean clearLeadAudio) {
            this.clearLeadAudio = clearLeadAudio;
        }

        public String getCurrentLeadAudioPath() {
            return currentLeadAudioPath;
        }

        public void setCurrentLeadAudioPath(String currentLeadAudioPath) {
            this.currentLeadAudioPath = currentLeadAudioPath;
        }

        public int getQcInputBit() {
            return qcInputBit;
        }

        public void setQcInputBit(int qcInputBit) {
            this.qcInputBit = qcInputBit;
        }

        public int getQcSlave() {
            return qcSlave;
        }

        public void setQcSlave(int qcSlave) {
            this.qcSlave = qcSlave;
        }

        public int getQcRelay() {
            return qcRelay;
        }

        public void setQcRelay(int qcRelay) {
            this.qcRelay = qcRelay;
        }

        public String getQcAudioUrlManual() {
            return qcAudioUrlManual;
        }

        public void setQcAudioUrlManual(String qcAudioUrlManual) {
            this.qcAudioUrlManual = qcAudioUrlManual;
        }

        public boolean isClearQcAudio() {
            return clearQcAudio;
        }

        public void setClearQcAudio(boolean clearQcAudio) {
            this.clearQcAudio = clearQcAudio;
        }

        public String getCurrentQcAudioPath() {
            return currentQcAudioPath;
        }

        public void setCurrentQcAudioPath(String currentQcAudioPath) {
            this.currentQcAudioPath = currentQcAudioPath;
        }
    }
}
