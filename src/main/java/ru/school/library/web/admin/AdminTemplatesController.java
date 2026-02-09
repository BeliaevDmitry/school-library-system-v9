package ru.school.library.web.admin;

import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import java.io.ByteArrayOutputStream;

@Controller
@RequestMapping("/admin/templates")
public class AdminTemplatesController {

    @GetMapping(value = "/registry.xlsx", produces = MediaType.APPLICATION_OCTET_STREAM_VALUE)
    public ResponseEntity<byte[]> registryTemplate() throws Exception {
        return okXlsx("template_registry.xlsx", workbookWithHeader(
                "Реестр",
                new String[]{"buildingCode","grade","subject","title","authors","year","isbn","total","available","inUse"}
        ));
    }

    @GetMapping(value = "/curriculum.xlsx", produces = MediaType.APPLICATION_OCTET_STREAM_VALUE)
    public ResponseEntity<byte[]> curriculumTemplate() throws Exception {
        return okXlsx("template_curriculum.xlsx", workbookWithHeader(
                "Учебный план",
                new String[]{"grade","subject","isbn","perStudent"}
        ));
    }

    @GetMapping(value = "/classes.xlsx", produces = MediaType.APPLICATION_OCTET_STREAM_VALUE)
    public ResponseEntity<byte[]> classesTemplate() throws Exception {
        return okXlsx("template_classes.xlsx", workbookWithHeader(
                "Численность",
                new String[]{"buildingCode","grade","letter","students"}
        ));
    }

    @GetMapping(value = "/future-classes.xlsx", produces = MediaType.APPLICATION_OCTET_STREAM_VALUE)
    public ResponseEntity<byte[]> futureClassesTemplate() throws Exception {
        return okXlsx("template_future_classes.xlsx", workbookWithHeader(
                "Будущий контингент",
                new String[]{"buildingCode","grade","letter","students"}
        ));
    }

    private static XSSFWorkbook workbookWithHeader(String sheetName, String[] cols) {
        XSSFWorkbook wb = new XSSFWorkbook();
        var sh = wb.createSheet(sheetName);
        var h = sh.createRow(0);
        for (int i = 0; i < cols.length; i++) {
            h.createCell(i).setCellValue(cols[i]);
            sh.setColumnWidth(i, Math.max(12, cols[i].length() + 2) * 256);
        }
        return wb;
    }

    private static ResponseEntity<byte[]> okXlsx(String filename, XSSFWorkbook wb) throws Exception {
        try (wb) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            wb.write(out);
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=" + filename)
                    .body(out.toByteArray());
        }
    }
}
