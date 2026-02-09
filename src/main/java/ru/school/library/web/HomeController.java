package ru.school.library.web;

import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import ru.school.library.entity.User;
import ru.school.library.service.AuthService;

@Controller
@RequiredArgsConstructor
public class HomeController {

    private final AuthService auth;

    @GetMapping("/")
    public String home(Authentication a, Model model) {
        var u = auth.requireUser(a.getName());
        model.addAttribute("user", u);
        if (u.getRole() == User.Role.ADMIN) return "redirect:/admin/dashboard";
        return "redirect:/librarian/dashboard";
    }
}
