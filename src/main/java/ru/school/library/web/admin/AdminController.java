package ru.school.library.web.admin;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import ru.school.library.dto.BuildingStockSummary;
import ru.school.library.repo.BuildingRepository;
import ru.school.library.repo.StockRepository;
import ru.school.library.service.ExcelImportService;
import ru.school.library.service.ReconciliationService;

@Controller
@RequiredArgsConstructor
@RequestMapping("/admin")
public class AdminController {

    private final BuildingRepository buildings;
    private final StockRepository stocks;
    private final ExcelImportService excel;
    private final ReconciliationService recon;

    @GetMapping("/dashboard")
    public String dashboard(Model model) {
        var bs = buildings.findAll();
        model.addAttribute("buildings", bs);
        long totalPositions = stocks.count();
        long totalBooks = stocks.findAll().stream().mapToLong(s -> s.getTotal()).sum();
        model.addAttribute("totalPositions", totalPositions);
        model.addAttribute("totalBooks", totalBooks);
        var byBuilding = bs.stream().map(b -> {
            var list = stocks.findByBuilding_Id(b.getId());
            long qty = list.stream().mapToLong(s -> s.getTotal()).sum();
            return new BuildingStockSummary(
                    b.getName(),
                    b.getCode(),
                    list.size(),
                    qty
            );
        }).toList();
        model.addAttribute("stockByBuilding", byBuilding);
        return "admin/dashboard";
    }

    @GetMapping("/import")
    public String importPage(Model model) {
        model.addAttribute("buildings", buildings.findAll());
        return "admin/import";
    }

    @PostMapping("/import/registry")
    public String importRegistry(@RequestParam("file") MultipartFile file,
                                 @RequestParam("buildingCode") String buildingCode,
                                 RedirectAttributes ra) {
        try {
            excel.importRegistry(file, buildingCode);
            ra.addFlashAttribute("success", "Реестр загружен");
        } catch (Exception e) {
            ra.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/admin/import";
    }

    @PostMapping("/import/legacy-registry")
    public String importLegacyRegistry(@RequestParam("file") MultipartFile file,
                                       @RequestParam("buildingCode") String buildingCode,
                                       RedirectAttributes ra) {
        try {
            excel.importLegacyRegistry(file, buildingCode);
            ra.addFlashAttribute("success", "Старый реестр загружен успешно");
        } catch (Exception e) {
            ra.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/admin/import";
    }

    @PostMapping("/import/curriculum")
    public String importCurriculum(@RequestParam("file") MultipartFile file,
                                   RedirectAttributes ra) {
        try {
            excel.importCurriculum(file);
            ra.addFlashAttribute("success", "Учебный план загружен");
        } catch (Exception e) {
            ra.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/admin/import";
    }

    @PostMapping("/import/classes")
    public String importClasses(@RequestParam("file") MultipartFile file,
                                RedirectAttributes ra) {
        try {
            excel.importClasses(file);
            ra.addFlashAttribute("success", "Численность загружена");
        } catch (Exception e) {
            ra.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/admin/import";
    }

    @PostMapping("/import/future-classes")
    public String importFutureClasses(@RequestParam("file") MultipartFile file,
                                      @RequestParam("academicYear") int academicYear,
                                      RedirectAttributes ra) {
        try {
            excel.importFutureClasses(file, academicYear);
            ra.addFlashAttribute("success", "Будущий контингент загружен успешно");
        } catch (Exception e) {
            ra.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/admin/import";
    }


    @PostMapping("/buildings/{id}")
    public String updateBuilding(@PathVariable Long id,
                                 @RequestParam String name,
                                 RedirectAttributes ra) {
        try {
            var b = buildings.findById(id).orElseThrow();
            b.setName(name == null ? "" : name.trim());
            buildings.save(b);
            ra.addFlashAttribute("success", "Название корпуса обновлено");
        } catch (Exception e) {
            ra.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/admin/dashboard";
    }

    @GetMapping("/reconciliation/{buildingId}")
    public String reconciliation(@PathVariable Long buildingId, Model model) {
        var b = buildings.findById(buildingId).orElseThrow();
        var rows = recon.calcForBuilding(b.getId(), b.getCode());
        model.addAttribute("building", b);
        model.addAttribute("rows", rows);
        model.addAttribute("bySubject", recon.summarize(rows, r -> r.subject()));
        model.addAttribute("byGrade", recon.summarize(rows, r -> String.valueOf(r.grade())));
        return "admin/reconciliation";
    }

    @GetMapping("/reconciliation/{buildingId}.xlsx")
    public ResponseEntity<byte[]> reconciliationXlsx(@PathVariable Long buildingId) throws Exception {
        var b = buildings.findById(buildingId).orElseThrow();
        var rows = recon.calcForBuilding(b.getId(), b.getCode());
        byte[] bytes = recon.exportExcel(b.getCode(), rows);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=reconciliation_" + b.getCode() + ".xlsx")
                .body(bytes);
    }
}
