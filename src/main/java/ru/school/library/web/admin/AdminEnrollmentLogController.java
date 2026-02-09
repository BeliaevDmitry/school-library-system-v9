package ru.school.library.web.admin;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import ru.school.library.repo.EnrollmentChangeLogRepository;

@Controller
@RequiredArgsConstructor
public class AdminEnrollmentLogController {

    private final EnrollmentChangeLogRepository logs;

    @GetMapping("/admin/classes/log")
    public String page(Model model) {
        model.addAttribute("rows", logs.findTop200ByOrderByTsDesc());
        return "admin/enrollment_log";
    }
}
