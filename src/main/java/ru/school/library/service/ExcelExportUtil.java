package ru.school.library.service;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.io.ByteArrayOutputStream;

public class ExcelExportUtil {

    private ExcelExportUtil() {
    }

    public static Workbook workbookWithHeader(String sheetName, String[] headers) {
        Workbook wb = new XSSFWorkbook();
        Sheet sh = wb.createSheet(sheetName);

        Font headerFont = wb.createFont();
        headerFont.setBold(true);

        CellStyle headerStyle = wb.createCellStyle();
        headerStyle.setFont(headerFont);

        Row hr = sh.createRow(0);
        for (int i = 0; i < headers.length; i++) {
            Cell c = hr.createCell(i);
            c.setCellValue(headers[i]);
            c.setCellStyle(headerStyle);
            sh.autoSizeColumn(i);
        }

        return wb;
    }

    public static ResponseEntity<byte[]> okXlsx(String fileName, Workbook wb) throws Exception {
        try (ByteArrayOutputStream bos = new ByteArrayOutputStream()) {
            wb.write(bos);
            wb.close();

            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION,
                            "attachment; filename=\"" + fileName + "\"")
                    .contentType(MediaType.APPLICATION_OCTET_STREAM)
                    .body(bos.toByteArray());
        }
    }
}
