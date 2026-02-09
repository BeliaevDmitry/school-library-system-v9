package ru.school.library.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import ru.school.library.entity.*;
import ru.school.library.repo.*;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PlanningService {

    private final BuildingRepository buildings;
    private final CurriculumItemRepository curriculum;
    private final FutureClassGroupRepository futureClasses;
    private final StockRepository stocks;

    public List<PlanRow> calcForBuilding(Long buildingId, int academicYear) {
        buildings.findById(buildingId).orElseThrow();

        // будущий контингент
        var classList = futureClasses
                .findByBuilding_IdAndAcademicYear(buildingId, academicYear);

        Map<Integer, Integer> studentsByGrade = classList.stream()
                .collect(Collectors.groupingBy(
                        FutureClassGroup::getGrade,
                        Collectors.summingInt(FutureClassGroup::getStudents)
                ));

        List<PlanRow> rows = new ArrayList<>();

        for (CurriculumItem ci : curriculum.findAll()) {
            int grade = ci.getGrade();
            int students = studentsByGrade.getOrDefault(grade, 0);
            if (students == 0) continue;

            BookTitle bt = ci.getBookTitle();
            Subject subj = ci.getSubject();

            String subjectName = subj != null ? subj.getName() : "";
            String title = bt != null ? bt.getTitle() : "(не задано)";
            String isbnOrKey = "";

            if (bt != null) {
                if (bt.getIsbn() != null && !bt.getIsbn().isBlank()) {
                    isbnOrKey = bt.getIsbn();
                } else if (bt.getExternalKey() != null) {
                    isbnOrKey = bt.getExternalKey();
                }
            }

            int perStudent = ci.getPerStudent(); // int
            int needed = students * perStudent;

            int available = 0;
            if (bt != null) {
                var st = stocks.findOne(buildingId, bt.getId()).orElse(null);
                if (st != null) {
                    available = st.getAvailable();
                }
            }

            int deficit = Math.max(0, needed - available);

            rows.add(new PlanRow(
                    grade,
                    subjectName,
                    title,
                    isbnOrKey,
                    perStudent,
                    students,
                    needed,
                    available,
                    deficit
            ));
        }

        // сортировка: параллель → предмет → название
        rows.sort(Comparator
                .comparingInt(PlanRow::grade)
                .thenComparing(PlanRow::subject, Comparator.nullsLast(String::compareToIgnoreCase))
                .thenComparing(PlanRow::title, Comparator.nullsLast(String::compareToIgnoreCase)));

        return rows;
    }

    // DTO для экрана и Excel
    public record PlanRow(
            int grade,
            String subject,
            String title,
            String isbnOrKey,
            int perStudent,
            int students,
            int needed,
            int available,
            int deficit
    ) {}
}
