package ru.school.library.service;

import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import ru.school.library.dto.ReconRow;
import ru.school.library.dto.SummaryRow;
import ru.school.library.entity.ClassGroup;
import ru.school.library.repo.ClassGroupRepository;
import ru.school.library.repo.CurriculumItemRepository;
import ru.school.library.repo.StockRepository;

import java.io.ByteArrayOutputStream;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class ReconciliationService {
    private final CurriculumItemRepository curriculum;
    private final ClassGroupRepository classes;
    private final StockRepository stocks;

    public ReconciliationService(CurriculumItemRepository curriculum, ClassGroupRepository classes, StockRepository stocks) {
        this.curriculum = curriculum;
        this.classes = classes;
        this.stocks = stocks;
    }

    public List<ReconRow> calcForBuilding(Long buildingId, String buildingCode) {
        var classList = classes.findByBuilding_Id(buildingId);
        var studentsByGrade = classList.stream()
                .collect(Collectors.groupingBy(ClassGroup::getGrade, Collectors.summingInt(ClassGroup::getStudents)));

        List<ReconRow> rows = new ArrayList<>();
        for (var item : curriculum.findAll()) {
            int students = studentsByGrade.getOrDefault(item.getGrade(), 0);
            int needed = students * item.getPerStudent();
            int available = stocks.findAvailable(buildingId, item.getBookTitle().getId()).orElse(0);
            int deficit = Math.max(0, needed - available);
            rows.add(new ReconRow(
                    buildingCode,
                    item.getGrade(),
                    item.getSubject().getName(),
                    item.getBookTitle().getTitle(),
                    needed,
                    available,
                    deficit
            ));
        }
        return rows;
    }

    public List<SummaryRow> summarize(List<ReconRow> rows, Function<ReconRow,String> keyFn) {
        return rows.stream()
                .collect(Collectors.groupingBy(keyFn))
                .entrySet().stream()
                .map(e -> {
                    int needed = e.getValue().stream().mapToInt(ReconRow::needed).sum();
                    int available = e.getValue().stream().mapToInt(ReconRow::available).sum();
                    int deficit = e.getValue().stream().mapToInt(ReconRow::deficit).sum();
                    return new SummaryRow(e.getKey(), needed, available, deficit);
                })
                .sorted(Comparator.comparing(SummaryRow::key))
                .toList();
    }

    public byte[] exportExcel(String buildingCode, List<ReconRow> rows) throws Exception {
        try (var wb = new XSSFWorkbook()) {
            var sheet = wb.createSheet("Сверка " + buildingCode);
            var header = sheet.createRow(0);
            String[] cols = {"Корпус","Параллель","Предмет","Учебник","Нужно","Есть","Дефицит"};
            for (int i=0;i<cols.length;i++) header.createCell(i).setCellValue(cols[i]);

            int r = 1;
            for (var row : rows) {
                var x = sheet.createRow(r++);
                x.createCell(0).setCellValue(row.buildingCode());
                x.createCell(1).setCellValue(row.grade());
                x.createCell(2).setCellValue(row.subject());
                x.createCell(3).setCellValue(row.title());
                x.createCell(4).setCellValue(row.needed());
                x.createCell(5).setCellValue(row.available());
                x.createCell(6).setCellValue(row.deficit());
            }

            // Итоги по предметам
            var subj = summarize(rows, ReconRow::subject);
            var s2 = wb.createSheet("Итоги по предметам");
            var h2 = s2.createRow(0);
            String[] c2 = {"Предмет","Нужно","Есть","Дефицит"};
            for (int i=0;i<c2.length;i++) h2.createCell(i).setCellValue(c2[i]);
            int rr=1;
            for (var s : subj) {
                var x = s2.createRow(rr++);
                x.createCell(0).setCellValue(s.key());
                x.createCell(1).setCellValue(s.needed());
                x.createCell(2).setCellValue(s.available());
                x.createCell(3).setCellValue(s.deficit());
            }

            // Итоги по параллелям
            var gr = summarize(rows, r0 -> String.valueOf(r0.grade()));
            var s3 = wb.createSheet("Итоги по параллелям");
            var h3 = s3.createRow(0);
            String[] c3 = {"Параллель","Нужно","Есть","Дефицит"};
            for (int i=0;i<c3.length;i++) h3.createCell(i).setCellValue(c3[i]);
            rr=1;
            for (var s : gr) {
                var x = s3.createRow(rr++);
                x.createCell(0).setCellValue(s.key());
                x.createCell(1).setCellValue(s.needed());
                x.createCell(2).setCellValue(s.available());
                x.createCell(3).setCellValue(s.deficit());
            }

            var out = new ByteArrayOutputStream();
            wb.write(out);
            return out.toByteArray();
        }
    }
}
