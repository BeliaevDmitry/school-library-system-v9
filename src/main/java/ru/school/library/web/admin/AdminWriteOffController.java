package ru.school.library.web.admin;

import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import ru.school.library.service.AuthService;
import ru.school.library.service.WriteOffService;

@Controller
@RequiredArgsConstructor
@RequestMapping("/admin/writeoff")
public class AdminWriteOffController {

    private final WriteOffService writeOffService;
    private final AuthService auth;

    @GetMapping
    public String page(Model model) {
        model.addAttribute("rows", writeOffService.pendingRequests());
        return "admin/writeoff_requests";
    }

    @PostMapping("/{id}/approve")
    public String approve(@PathVariable Long id,
                          @RequestParam(required = false) String note,
                          Authentication a,
                          RedirectAttributes ra) {
        try {
            writeOffService.reviewRequest(id, true, note, auth.requireUser(a.getName()));
            ra.addFlashAttribute("success", "Заявка подтверждена");
        } catch (Exception e) {
            ra.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/admin/writeoff";
    }

    @PostMapping("/{id}/reject")
    public String reject(@PathVariable Long id,
                         @RequestParam(required = false) String note,
                         Authentication a,
                         RedirectAttributes ra) {
        try {
            writeOffService.reviewRequest(id, false, note, auth.requireUser(a.getName()));
            ra.addFlashAttribute("success", "Заявка отклонена");
        } catch (Exception e) {
            ra.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/admin/writeoff";
    }

    @PostMapping("/approve-all")
    public String approveAll(@RequestParam(required = false) String note,
                             Authentication a,
                             RedirectAttributes ra) {
        try {
            int approved = writeOffService.approveAllPending(auth.requireUser(a.getName()), note);
            ra.addFlashAttribute("success", "Подтверждено заявок: " + approved);
        } catch (Exception e) {
            ra.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/admin/writeoff";
    }
}
