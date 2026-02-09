package ru.school.library.web.librarian;

import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import ru.school.library.entity.Stock;
import ru.school.library.repo.StockRepository;
import ru.school.library.service.AuthService;
import ru.school.library.service.WriteOffService;

@Controller
@RequiredArgsConstructor
@RequestMapping("/librarian/writeoff")
public class WriteOffController {

    private final AuthService auth;
    private final StockRepository stocks;
    private final WriteOffService writeOffService;

    @GetMapping
    public String page(Authentication a, Model model) {
        var u = auth.requireUser(a.getName());
        var list = stocks.findByBuilding_Id(u.getBuilding().getId());
        model.addAttribute("building", u.getBuilding());
        model.addAttribute("stocks", list);
        return "librarian/writeoff";
    }

    @PostMapping
    public String submit(Authentication a,
                         @RequestParam Long stockId,
                         @RequestParam int count,
                         @RequestParam(required = false) String reason) {
        var u = auth.requireUser(a.getName());
        Stock st = stocks.findById(stockId).orElseThrow();
        if (!st.getBuilding().getId().equals(u.getBuilding().getId())) {
            throw new RuntimeException("Нельзя списывать не свой корпус");
        }
        writeOffService.writeOff(st.getBuilding(), st.getBookTitle(), count, reason, u);
        return "redirect:/librarian/stock";
    }
}
