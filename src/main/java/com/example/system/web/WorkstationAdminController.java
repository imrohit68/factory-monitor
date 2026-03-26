package com.example.system.web;

import com.example.system.domain.Workstation;
import com.example.system.dto.WorkstationForm;
import com.example.system.dto.WorkstationFormPageData;
import com.example.system.service.WorkstationAdminService;
import com.example.system.service.WorkstationService;
import lombok.RequiredArgsConstructor;
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

import java.util.List;
import java.util.Optional;

@Controller
@RequestMapping("/admin/workstations")
@RequiredArgsConstructor
public class WorkstationAdminController {

    private final WorkstationService workstationService;
    private final WorkstationAdminService workstationAdminService;

    @GetMapping
    public String list(Model model) {
        List<Workstation> workstations = workstationService.findAllOrdered();
        model.addAttribute("workstations", workstations);
        model.addAttribute("slotRows", workstationAdminService.slotRowsForWorkstations(workstations));
        return "admin/workstations";
    }

    @GetMapping("/new")
    public String createForm(Model model) {
        WorkstationForm form = new WorkstationForm();
        model.addAttribute("form", form);
        applyFormPageData(model, workstationAdminService.formPageData(null, form.getPlaceAfterWorkstationId()));
        return "admin/workstation-form";
    }

    @GetMapping("/{id}/edit")
    public String editForm(@PathVariable Long id, Model model) {
        Workstation w =
                workstationService
                        .findByIdWithSlots(id)
                        .orElseThrow(() -> new IllegalArgumentException("Unknown id"));
        WorkstationForm form = workstationAdminService.formFromWorkstation(w);
        model.addAttribute("form", form);
        applyFormPageData(model, workstationAdminService.formPageData(w.getId(), form.getPlaceAfterWorkstationId()));
        return "admin/workstation-form";
    }

    @PostMapping
    public String save(
            @ModelAttribute("form") WorkstationForm form,
            @RequestParam(value = "engAudioFile", required = false) MultipartFile engAudioFile,
            @RequestParam(value = "leadAudioFile", required = false) MultipartFile leadAudioFile,
            @RequestParam(value = "qcAudioFile", required = false) MultipartFile qcAudioFile,
            RedirectAttributes redirectAttributes,
            Model model) {

        Optional<String> validationError = workstationAdminService.validateWorkstationForm(form);
        if (validationError.isPresent()) {
            model.addAttribute("errorMessage", validationError.get());
            applyFormPageData(
                    model,
                    workstationAdminService.formPageData(form.getId(), form.getPlaceAfterWorkstationId()));
            return "admin/workstation-form";
        }

        Optional<String> persistError =
                workstationAdminService.persistWorkstationFromForm(form, engAudioFile, leadAudioFile, qcAudioFile);
        if (persistError.isPresent()) {
            model.addAttribute("errorMessage", persistError.get());
            applyFormPageData(
                    model,
                    workstationAdminService.formPageData(form.getId(), form.getPlaceAfterWorkstationId()));
            return "admin/workstation-form";
        }

        redirectAttributes.addFlashAttribute("message", "Workstation saved");
        return "redirect:/admin/workstations";
    }

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        workstationService.deleteById(id);
        redirectAttributes.addFlashAttribute("message", "Workstation deleted");
        return "redirect:/admin/workstations";
    }

    private static void applyFormPageData(Model model, WorkstationFormPageData page) {
        model.addAttribute("maxInputBit", page.maxInputBit());
        model.addAttribute("maxRelayPerSlave", page.maxRelayPerSlave());
        model.addAttribute("placementOptions", page.placementOptions());
        model.addAttribute("showPlacementControl", page.showPlacementControl());
        model.addAttribute("selectedPlaceAfterId", page.selectedPlaceAfterId());
    }
}
