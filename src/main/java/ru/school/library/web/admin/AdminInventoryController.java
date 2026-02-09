package ru.school.library.web.admin;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import ru.school.library.service.InventoryService;

@Controller
@RequiredArgsConstructor
public class AdminInventoryController {

    private final InventoryService inventory;

    @GetMapping("/admin/inventory")
    public String page(Model model) {
        var rows = inventory.all();
        long problems = rows.stream().filter(r -> r.diff() != 0).count();
        model.addAttribute("rows", rows);
        model.addAttribute("problems", problems);
        return "admin/inventory";
    }
}
