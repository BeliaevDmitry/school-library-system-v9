package ru.school.library.web.admin;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import ru.school.library.repo.BookTitleRepository;
import ru.school.library.repo.StockRepository;
import ru.school.library.service.InventoryService;

@Controller
@RequiredArgsConstructor
public class AdminInventoryController {

    private final InventoryService inventory;
    private final StockRepository stocks;
    private final BookTitleRepository bookTitles;

    @GetMapping("/admin/inventory")
    public String page(Model model) {
        var rows = inventory.all();
        long problems = rows.stream().filter(r -> r.diff() != 0).count();
        model.addAttribute("rows", rows);
        model.addAttribute("problems", problems);
        return "admin/inventory";
    }

    @PostMapping("/admin/stocks/{id}/approval")
    public String updateApproval(@PathVariable Long id,
                                 @RequestParam(defaultValue = "false") boolean approvedByOrder,
                                 RedirectAttributes ra) {
        var st = stocks.findById(id).orElse(null);
        if (st == null || st.getBookTitle() == null) {
            ra.addFlashAttribute("error", "Позиция не найдена");
            return "redirect:/admin/inventory";
        }
        st.getBookTitle().setApprovedByOrder(approvedByOrder);
        bookTitles.save(st.getBookTitle());
        ra.addFlashAttribute("success", "Статус «разрешён приказом» обновлён");
        return "redirect:/admin/inventory";
    }
}
