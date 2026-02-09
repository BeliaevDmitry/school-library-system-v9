package ru.school.library.web.librarian;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import ru.school.library.entity.Stock;
import ru.school.library.entity.User;
import ru.school.library.repo.StockRepository;
import ru.school.library.service.AuthService;
import ru.school.library.service.InventoryService;
import ru.school.library.service.ReconciliationService;

@Controller
@RequiredArgsConstructor
@RequestMapping("/librarian")
public class LibrarianController {

    private final AuthService auth;
    private final StockRepository stocks;
    private final InventoryService inventory;
    private final ReconciliationService recon;

    @GetMapping("/dashboard")
    public String dashboard(Authentication a, Model model) {
        var u = auth.requireUser(a.getName());
        model.addAttribute("user", u);
        model.addAttribute("building", u.getBuilding());
        return "librarian/dashboard";
    }

    @GetMapping("/stock")
    public String stock(Authentication a, Model model) {
        var u = auth.requireUser(a.getName());
        var list = stocks.findByBuilding_Id(u.getBuilding().getId());
        model.addAttribute("building", u.getBuilding());
        model.addAttribute("stocks", list);
        return "librarian/stock";
    }

    @PostMapping("/stocks/{id}/extra")
    public String updateStockExtra(@PathVariable Long id,
                                   @RequestParam(defaultValue = "0") int issuedToStudents,
                                   @RequestParam(defaultValue = "0") int inCabinets,
                                   @RequestParam(required = false) String note,
                                   Authentication a,
                                   RedirectAttributes ra) {
        User u = auth.requireUser(a.getName());
        Stock st = stocks.findById(id).orElseThrow();

        // защита: библиотекарь может править только свой корпус (admin тоже может)
        if (u.getRole() == User.Role.LIBRARIAN
                && (u.getBuilding() == null || !u.getBuilding().getId().equals(st.getBuilding().getId()))) {
            ra.addFlashAttribute("error", "Нет доступа к чужому корпусу");
            return "redirect:/librarian/stock";
        }

        st.setIssuedToStudents(Math.max(0, issuedToStudents));
        st.setInCabinets(Math.max(0, inCabinets));
        st.setNote(note == null ? null : note.trim());
        stocks.save(st);

        ra.addFlashAttribute("success", "Сохранено");
        return "redirect:/librarian/stock";
    }

    @GetMapping("/reconciliation")
    public String reconciliation(Authentication a, Model model) {
        var u = auth.requireUser(a.getName());
        var b = u.getBuilding();
        var rows = recon.calcForBuilding(b.getId(), b.getCode());
        model.addAttribute("building", b);
        model.addAttribute("rows", rows);
        model.addAttribute("bySubject", recon.summarize(rows, r -> r.subject()));
        model.addAttribute("byGrade", recon.summarize(rows, r -> String.valueOf(r.grade())));
        return "librarian/reconciliation";
    }

    @GetMapping("/reconciliation.xlsx")
    public ResponseEntity<byte[]> reconciliationXlsx(Authentication a) throws Exception {
        var u = auth.requireUser(a.getName());
        var b = u.getBuilding();
        var rows = recon.calcForBuilding(b.getId(), b.getCode());
        byte[] bytes = recon.exportExcel(b.getCode(), rows);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=reconciliation_" + b.getCode() + ".xlsx")
                .body(bytes);
    }

    @GetMapping("/inventory")
    public String inventoryPage(Authentication a, Model model) {
        var u = auth.requireUser(a.getName());
        if (u.getBuilding() == null) throw new RuntimeException("No building assigned");
        var rows = inventory.forBuilding(u.getBuilding().getId());
        long problems = rows.stream().filter(r -> r.diff() != 0).count();
        model.addAttribute("building", u.getBuilding());
        model.addAttribute("rows", rows);
        model.addAttribute("problems", problems);
        return "librarian/inventory";
    }
}
