package ru.school.library.web.admin;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import ru.school.library.repo.BuildingRepository;
import ru.school.library.service.ExcelExportUtil;
import ru.school.library.service.PlanningService;
import ru.school.library.service.PlanningService.PlanRow;

import java.util.List;

@Controller
@RequiredArgsConstructor
@RequestMapping("/admin/planning")
public class AdminPlanningController {

    private final BuildingRepository buildings;
    private final PlanningService planning;

    @GetMapping
    public String planningPage(@RequestParam(required = false) Long buildingId,
                               @RequestParam(required = false) Integer academicYear,
                               Model model) {

        model.addAttribute("buildings", buildings.findAll());

        if (buildingId != null && academicYear != null) {
            List<PlanRow> rows = planning.calcForBuilding(buildingId, academicYear);
            model.addAttribute("rows", rows);
            model.addAttribute("buildingId", buildingId);
            model.addAttribute("academicYear", academicYear);
        }

        return "admin/planning";
    }

    @GetMapping(value = "/export.xlsx", produces = MediaType.APPLICATION_OCTET_STREAM_VALUE)
    public ResponseEntity<byte[]> export(@RequestParam Long buildingId,
                                         @RequestParam int academicYear) throws Exception {

        List<PlanRow> rows = planning.calcForBuilding(buildingId, academicYear);

        var wb = ExcelExportUtil.workbookWithHeader(
                "План закупки",
                new String[]{
                        "Параллель",
                        "Предмет",
                        "Учебник",
                        "ISBN / ключ",
                        "На ученика",
                        "Численность",
                        "Нужно",
                        "В наличии",
                        "Дефицит"
                }
        );

        var sh = wb.getSheetAt(0);
        int r = 1;
        for (PlanRow row : rows) {
            var rr = sh.createRow(r++);
            rr.createCell(0).setCellValue(row.grade());
            rr.createCell(1).setCellValue(row.subject());
            rr.createCell(2).setCellValue(row.title());
            rr.createCell(3).setCellValue(row.isbnOrKey());
            rr.createCell(4).setCellValue(row.perStudent());
            rr.createCell(5).setCellValue(row.students());
            rr.createCell(6).setCellValue(row.needed());
            rr.createCell(7).setCellValue(row.available());
            rr.createCell(8).setCellValue(row.deficit());
        }

        return ExcelExportUtil.okXlsx(
                "plan_" + academicYear + ".xlsx",
                wb
        );
    }
}
