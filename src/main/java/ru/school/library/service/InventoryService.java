package ru.school.library.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import ru.school.library.entity.Stock;
import ru.school.library.repo.StockRepository;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class InventoryService {

    private final StockRepository stocks;

    public record InventoryRow(
            Long stockId,
            String buildingName,
            String subject,
            Integer grade,
            String title,
            int total,
            int available,
            int issuedToStudents,
            int inCabinets,
            String note,
            int expectedTotal,
            int diff,
            String status,
            int accuracyPercent,
            int suufTotal,
            int meshTotal,
            boolean approvedByOrder
    ) {}

    public List<InventoryRow> forBuilding(Long buildingId) {
        return stocks.findByBuilding_Id(buildingId).stream()
                .map(this::map)
                .sorted(Comparator.comparing((InventoryRow r) -> r.grade() == null ? 0 : r.grade())
                        .thenComparing(InventoryRow::subject, Comparator.nullsLast(String::compareToIgnoreCase))
                        .thenComparing(InventoryRow::title, Comparator.nullsLast(String::compareToIgnoreCase)))
                .collect(Collectors.toList());
    }

    public List<InventoryRow> all() {
        return stocks.findAll().stream()
                .map(this::map)
                .sorted(Comparator.comparing(InventoryRow::buildingName, Comparator.nullsLast(String::compareToIgnoreCase))
                        .thenComparing(r -> r.grade() == null ? 0 : r.grade())
                        .thenComparing(InventoryRow::subject, Comparator.nullsLast(String::compareToIgnoreCase))
                        .thenComparing(InventoryRow::title, Comparator.nullsLast(String::compareToIgnoreCase)))
                .collect(Collectors.toList());
    }

    private InventoryRow map(Stock s) {
        String buildingName = s.getBuilding() != null ? s.getBuilding().getName() : "";
        String subject = (s.getBookTitle()!=null && s.getBookTitle().getSubject()!=null) ? s.getBookTitle().getSubject().getName() : "";
        Integer grade = s.getBookTitle()!=null ? s.getBookTitle().getGrade() : null;
        String title = s.getBookTitle()!=null ? s.getBookTitle().getTitle() : "";
        int expected = s.getAvailable() + s.getIssuedToStudents() + s.getInCabinets();
        int diff = s.getTotal() - expected;
        String status = diff == 0 ? "OK" : (diff > 0 ? "Излишек" : "Недостача");
        int accuracyPercent = s.getTotal() <= 0 ? (expected <= 0 ? 100 : 0)
                : (int) Math.max(0, Math.min(100, Math.round((expected * 100.0f) / s.getTotal())));
        return new InventoryRow(
                s.getId(),
                buildingName,
                subject,
                grade,
                title,
                s.getTotal(),
                s.getAvailable(),
                s.getIssuedToStudents(),
                s.getInCabinets(),
                s.getNote(),
                expected,
                diff,
                status,
                accuracyPercent,
                s.getSuufTotal(),
                s.getMeshTotal(),
                s.getBookTitle() != null && s.getBookTitle().isApprovedByOrder()
        );
    }
}
