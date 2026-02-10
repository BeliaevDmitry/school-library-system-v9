package ru.school.library.web.admin;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.security.Principal;
import java.time.OffsetDateTime;
import ru.school.library.entity.ClassGroup;
import ru.school.library.entity.FutureClassGroup;
import ru.school.library.repo.BuildingRepository;
import ru.school.library.repo.ClassGroupRepository;
import ru.school.library.repo.FutureClassGroupRepository;

import java.util.Comparator;
import java.util.Locale;

@Controller
@RequestMapping("/admin/classes")
@RequiredArgsConstructor
public class AdminClassGroupsController {

    private final BuildingRepository buildings;
    private final ClassGroupRepository classes;
    private final FutureClassGroupRepository futureClasses;
    private final ru.school.library.repo.UserRepository users;
    private final ru.school.library.repo.EnrollmentChangeLogRepository logs;

    @GetMapping
    public String page(@RequestParam(required = false) Long buildingId,
                       Model model) {
        model.addAttribute("buildings", buildings.findAll().stream()
                .sorted(Comparator.comparing(b -> b.getCode()))
                .toList());
        model.addAttribute("buildingId", buildingId);
        if (buildingId != null) {
            model.addAttribute("rows", classes.findByBuilding_Id(buildingId).stream()
                    .sorted(Comparator.comparingInt(ClassGroup::getGrade).thenComparing(ClassGroup::getLetter, Comparator.nullsLast(String::compareToIgnoreCase)))
                    .toList());
        }
        return "admin/classes";
    }

    @PostMapping("/save")
    public String save(@RequestParam Long buildingId,
                       @RequestParam int grade,
                       @RequestParam String letter,
                       @RequestParam int students,
                       @RequestParam(required = false) String note,
                       Principal principal,
                       RedirectAttributes ra) {
        try {
            var b = buildings.findById(buildingId).orElseThrow();
            String l = letter == null ? "" : letter.trim().toUpperCase(Locale.ROOT);
            // найдём по уникальности building+grade+letter
            var existing = classes.findByBuilding_IdAndGradeAndLetterIgnoreCase(buildingId, grade, l).orElse(null);

            ClassGroup cg = existing != null ? existing : new ClassGroup();
            cg.setBuilding(b);
            cg.setGrade(grade);
            cg.setLetter(l);
            cg.setStudents(Math.max(0, students));
            classes.save(cg);

            ra.addFlashAttribute("success", "Сохранено");
        } catch (Exception e) {
            ra.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/admin/classes?buildingId=" + buildingId;
    }

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable Long id, Principal principal, RedirectAttributes ra) {
        var cg = classes.findById(id).orElse(null);
        if (cg != null) {
            Long bId = cg.getBuilding().getId();
            int old = cg.getStudents();
            classes.deleteById(id);

            var actor = users.findByUsername(principal.getName()).orElse(null);
            var log = new ru.school.library.entity.EnrollmentChangeLog();
            log.setTs(OffsetDateTime.now());
            log.setActor(actor);
            log.setBuilding(cg.getBuilding());
            log.setScope(ru.school.library.entity.EnrollmentChangeLog.Scope.CURRENT);
            log.setAcademicYear(0);
            log.setGrade(cg.getGrade());
            log.setLetter(cg.getLetter());
            log.setOldStudents(old);
            log.setNewStudents(0);
            log.setAction(ru.school.library.entity.EnrollmentChangeLog.Action.DELETE);
            logs.save(log);

            ra.addFlashAttribute("success", "Удалено");
            return "redirect:/admin/classes?buildingId=" + bId;
        }
        ra.addFlashAttribute("error", "Не найдено");
        return "redirect:/admin/classes";
    }

    @PostMapping("/promote")
    @Transactional
    public String promote(@RequestParam Long buildingId,
                          @RequestParam(required = false) Integer academicYear,
                          Principal principal,
                          RedirectAttributes ra) {
        try {
            var b = buildings.findById(buildingId).orElseThrow();
            var list = classes.findByBuilding_Id(buildingId);
            if (list.isEmpty()) {
                ra.addFlashAttribute("error", "В выбранном корпусе нет классов для перевода");
                return "redirect:/admin/classes?buildingId=" + buildingId;
            }

            var actor = users.findByUsername(principal.getName()).orElse(null);
            int targetAcademicYear = academicYear == null ? java.time.Year.now().getValue() + 1 : academicYear;

            // Текущий контингент не трогаем: формируем/обновляем будущий контингент на следующий учебный год.
            int promotedToFuture = 0;
            int graduated = 0;

            for (var oldCg : list) {
                String oldLetterNormalized = oldCg.getLetter() == null ? "" : oldCg.getLetter().trim().toUpperCase(Locale.ROOT);
                if (oldCg.getGrade() >= 11) {
                    graduated++;
                    var log = new ru.school.library.entity.EnrollmentChangeLog();
                    log.setTs(OffsetDateTime.now());
                    log.setActor(actor);
                    log.setBuilding(b);
                    log.setScope(ru.school.library.entity.EnrollmentChangeLog.Scope.FUTURE);
                    log.setAcademicYear(targetAcademicYear);
                    log.setGrade(oldCg.getGrade());
                    log.setLetter(oldLetterNormalized);
                    log.setOldStudents(oldCg.getStudents());
                    log.setNewStudents(0);
                    log.setAction(ru.school.library.entity.EnrollmentChangeLog.Action.DELETE);
                    log.setNote("Годовой перевод в будущий контингент: выпуск из 11 класса");
                    logs.save(log);
                    continue;
                }

                int nextGrade = oldCg.getGrade() + 1;
                var futureCg = futureClasses
                        .findByBuilding_IdAndAcademicYearAndGradeAndLetterIgnoreCase(buildingId, targetAcademicYear, nextGrade, oldLetterNormalized)
                        .orElseGet(ru.school.library.entity.FutureClassGroup::new);

                int oldStudents = futureCg.getId() == null ? 0 : futureCg.getStudents();
                futureCg.setBuilding(b);
                futureCg.setAcademicYear(targetAcademicYear);
                futureCg.setGrade(nextGrade);
                futureCg.setLetter(oldLetterNormalized);
                futureCg.setStudents(oldCg.getStudents());
                futureClasses.save(futureCg);
                promotedToFuture++;

                var log = new ru.school.library.entity.EnrollmentChangeLog();
                log.setTs(OffsetDateTime.now());
                log.setActor(actor);
                log.setBuilding(b);
                log.setScope(ru.school.library.entity.EnrollmentChangeLog.Scope.FUTURE);
                log.setAcademicYear(targetAcademicYear);
                log.setGrade(futureCg.getGrade());
                log.setLetter(futureCg.getLetter());
                log.setOldStudents(oldStudents);
                log.setNewStudents(futureCg.getStudents());
                log.setAction(ru.school.library.entity.EnrollmentChangeLog.Action.UPSERT);
                log.setNote("Годовой перевод в будущий контингент: из " + oldCg.getGrade() + "-" + oldLetterNormalized);
                logs.save(log);
            }

            ra.addFlashAttribute("success", "Годовой перевод выполнен. Будущий контингент " + targetAcademicYear + "/" + (targetAcademicYear + 1)
                    + " обновлён: переведено классов " + promotedToFuture + ", выпущено 11-х классов: " + graduated + ". Текущий контингент не изменён.");
        } catch (Exception e) {
            ra.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/admin/classes?buildingId=" + buildingId;
    }

    // ===== Будущий контингент =====

    @GetMapping("/future")
    public String futurePage(@RequestParam(required = false) Integer academicYear,
                             @RequestParam(required = false) Long buildingId,
                             Model model) {
        if (academicYear == null) academicYear = java.time.Year.now().getValue() + 1;
        model.addAttribute("academicYear", academicYear);
        model.addAttribute("buildings", buildings.findAll().stream()
                .sorted(Comparator.comparing(b -> b.getCode()))
                .toList());
        model.addAttribute("buildingId", buildingId);

        if (buildingId != null) {
            model.addAttribute("rows", futureClasses.findByBuilding_IdAndAcademicYear(buildingId, academicYear).stream()
                    .sorted(Comparator.comparingInt(FutureClassGroup::getGrade).thenComparing(FutureClassGroup::getLetter, Comparator.nullsLast(String::compareToIgnoreCase)))
                    .toList());
        }
        return "admin/future_classes";
    }

    @PostMapping("/future/save")
    public String saveFuture(@RequestParam int academicYear,
                             @RequestParam Long buildingId,
                             @RequestParam int grade,
                             @RequestParam String letter,
                             @RequestParam int students,
                             @RequestParam(required = false) String note,
                             Principal principal,
                             RedirectAttributes ra) {
        try {
            var b = buildings.findById(buildingId).orElseThrow();
            String l = letter == null ? "" : letter.trim().toUpperCase(Locale.ROOT);

            var existing = futureClasses.findByBuilding_IdAndAcademicYearAndGradeAndLetterIgnoreCase(buildingId, academicYear, grade, l).orElse(null);

            FutureClassGroup cg = existing != null ? existing : new FutureClassGroup();
            cg.setBuilding(b);
            cg.setAcademicYear(academicYear);
            cg.setGrade(grade);
            cg.setLetter(l);
            cg.setStudents(Math.max(0, students));
            int old = existing != null ? existing.getStudents() : 0;
            futureClasses.save(cg);

            var actor = users.findByUsername(principal.getName()).orElse(null);
            var log = new ru.school.library.entity.EnrollmentChangeLog();
            log.setTs(OffsetDateTime.now());
            log.setActor(actor);
            log.setBuilding(b);
            log.setScope(ru.school.library.entity.EnrollmentChangeLog.Scope.FUTURE);
            log.setAcademicYear(academicYear);
            log.setGrade(grade);
            log.setLetter(l);
            log.setOldStudents(old);
            log.setNewStudents(cg.getStudents());
            log.setAction(ru.school.library.entity.EnrollmentChangeLog.Action.UPSERT);
            log.setNote(note == null ? null : note.trim());
            logs.save(log);

            ra.addFlashAttribute("success", "Сохранено");
        } catch (Exception e) {
            ra.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/admin/classes/future?academicYear=" + academicYear + "&buildingId=" + buildingId;
    }

    @PostMapping("/future/{id}/delete")
    public String deleteFuture(@PathVariable Long id,
                               @RequestParam int academicYear,
                               Principal principal,
                               RedirectAttributes ra) {
        var cg = futureClasses.findById(id).orElse(null);
        if (cg != null) {
            Long bId = cg.getBuilding().getId();
            int old = cg.getStudents();
            futureClasses.deleteById(id);

            var actor = users.findByUsername(principal.getName()).orElse(null);
            var log = new ru.school.library.entity.EnrollmentChangeLog();
            log.setTs(OffsetDateTime.now());
            log.setActor(actor);
            log.setBuilding(cg.getBuilding());
            log.setScope(ru.school.library.entity.EnrollmentChangeLog.Scope.FUTURE);
            log.setAcademicYear(academicYear);
            log.setGrade(cg.getGrade());
            log.setLetter(cg.getLetter());
            log.setOldStudents(old);
            log.setNewStudents(0);
            log.setAction(ru.school.library.entity.EnrollmentChangeLog.Action.DELETE);
            logs.save(log);

            ra.addFlashAttribute("success", "Удалено");
            return "redirect:/admin/classes/future?academicYear=" + academicYear + "&buildingId=" + bId;
        }
        ra.addFlashAttribute("error", "Не найдено");
        return "redirect:/admin/classes/future?academicYear=" + academicYear;
    }

}
