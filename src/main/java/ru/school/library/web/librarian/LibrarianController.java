package ru.school.library.web.librarian;

import lombok.RequiredArgsConstructor;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import ru.school.library.entity.BookTitle;
import ru.school.library.entity.Stock;
import ru.school.library.entity.Subject;
import ru.school.library.entity.User;
import ru.school.library.repo.BookTitleRepository;
import ru.school.library.repo.StockRepository;
import ru.school.library.repo.SubjectRepository;
import ru.school.library.service.AuthService;
import ru.school.library.service.ExcelImportService;
import ru.school.library.service.InventoryService;
import ru.school.library.service.ReconciliationService;

import java.io.ByteArrayOutputStream;

@Controller
@RequiredArgsConstructor
@RequestMapping("/librarian")
public class LibrarianController {

    private final AuthService auth;
    private final StockRepository stocks;
    private final SubjectRepository subjects;
    private final BookTitleRepository bookTitles;
    private final InventoryService inventory;
    private final ReconciliationService recon;
    private final ExcelImportService excel;

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
        model.addAttribute("bookTitles", bookTitles.findAll().stream()
                .sorted(java.util.Comparator.comparing((BookTitle bt) -> bt.getSubject().getName(), String.CASE_INSENSITIVE_ORDER)
                        .thenComparing(BookTitle::getGrade)
                        .thenComparing(BookTitle::getTitle, String.CASE_INSENSITIVE_ORDER))
                .toList());
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


    @PostMapping("/stocks/{id}/counts")
    public String updateStockCounts(@PathVariable Long id,
                                    @RequestParam(defaultValue = "0") int total,
                                    @RequestParam(defaultValue = "0") int available,
                                    @RequestParam(defaultValue = "0") int inUse,
                                    Authentication a,
                                    RedirectAttributes ra) {
        User u = auth.requireUser(a.getName());
        Stock st = stocks.findById(id).orElseThrow();

        if (u.getRole() == User.Role.LIBRARIAN
                && (u.getBuilding() == null || !u.getBuilding().getId().equals(st.getBuilding().getId()))) {
            ra.addFlashAttribute("error", "Нет доступа к чужому корпусу");
            return "redirect:/librarian/stock";
        }

        st.setTotal(Math.max(0, total));
        st.setAvailable(Math.max(0, available));
        st.setInUse(Math.max(0, inUse));
        stocks.save(st);

        ra.addFlashAttribute("success", "Количество экземпляров обновлено");
        return "redirect:/librarian/stock";
    }

    @PostMapping("/stocks/{id}/approval")
    public String updateStockApproval(@PathVariable Long id,
                                      @RequestParam(defaultValue = "false") boolean approvedByOrder,
                                      Authentication a,
                                      RedirectAttributes ra) {
        User u = auth.requireUser(a.getName());
        Stock st = stocks.findById(id).orElseThrow();

        if (u.getRole() == User.Role.LIBRARIAN
                && (u.getBuilding() == null || !u.getBuilding().getId().equals(st.getBuilding().getId()))) {
            ra.addFlashAttribute("error", "Нет доступа к чужому корпусу");
            return "redirect:/librarian/stock";
        }

        if (st.getBookTitle() != null) {
            st.getBookTitle().setApprovedByOrder(approvedByOrder);
            bookTitles.save(st.getBookTitle());
            ra.addFlashAttribute("success", "Статус «разрешён приказом» обновлён");
        }
        return "redirect:/librarian/stock";
    }

    @PostMapping("/stocks/add-from-title")
    public String addFromTitle(Authentication a,
                               @RequestParam Long bookTitleId,
                               @RequestParam(defaultValue = "0") int total,
                               @RequestParam(defaultValue = "0") int available,
                               @RequestParam(defaultValue = "0") int inUse,
                               RedirectAttributes ra) {
        try {
            var u = auth.requireUser(a.getName());
            BookTitle bt = bookTitles.findById(bookTitleId).orElseThrow();
            Stock st = stocks.findOne(u.getBuilding().getId(), bt.getId()).orElseGet(Stock::new);
            st.setBuilding(u.getBuilding());
            st.setBookTitle(bt);
            st.setTotal(Math.max(0, total));
            st.setAvailable(Math.max(0, available));
            st.setInUse(Math.max(0, inUse));
            stocks.save(st);
            ra.addFlashAttribute("success", "Книга добавлена из списка");
        } catch (Exception e) {
            ra.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/librarian/stock";
    }

    @PostMapping("/import/stock")
    public String importStock(@RequestParam("file") MultipartFile file,
                              Authentication a,
                              RedirectAttributes ra) {
        try {
            var u = auth.requireUser(a.getName());
            excel.importLibrarianStock(file, u.getBuilding().getId());
            ra.addFlashAttribute("success", "Остатки загружены");
        } catch (Exception e) {
            ra.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/librarian/stock";
    }

    @PostMapping("/stocks/add")
    public String addStock(Authentication a,
                           @RequestParam String subject,
                           @RequestParam int grade,
                           @RequestParam String title,
                           @RequestParam(required = false) String authors,
                           @RequestParam(required = false) String publisher,
                           @RequestParam(required = false) Integer year,
                           @RequestParam(required = false) String isbn,
                           @RequestParam(defaultValue = "false") boolean approvedByOrder,
                           @RequestParam(defaultValue = "0") int total,
                           @RequestParam(defaultValue = "0") int available,
                           @RequestParam(defaultValue = "0") int inUse,
                           RedirectAttributes ra) {
        try {
            var u = auth.requireUser(a.getName());
            Subject s = subjects.findByNameIgnoreCase(subject)
                    .orElseGet(() -> {
                        Subject x = new Subject();
                        x.setName(subject);
                        return subjects.save(x);
                    });

            BookTitle bt = null;
            if (isbn != null && !isbn.isBlank()) {
                bt = bookTitles.findByIsbnAndGradeAndSubject_Id(isbn.trim(), grade, s.getId()).orElse(null);
            }
            if (bt == null) {
                bt = bookTitles.findByTitleIgnoreCaseAndGradeAndSubject_Id(title, grade, s.getId()).orElse(null);
            }
            if (bt == null) {
                bt = new BookTitle();
                bt.setGrade(grade);
                bt.setSubject(s);
            }
            bt.setTitle(title);
            bt.setAuthors(authors);
            bt.setPublisher(publisher);
            bt.setYear(year);
            bt.setIsbn(isbn == null || isbn.isBlank() ? null : isbn.trim());
            bt.setApprovedByOrder(approvedByOrder);
            bt = bookTitles.save(bt);

            Stock st = stocks.findOne(u.getBuilding().getId(), bt.getId()).orElseGet(Stock::new);
            st.setBuilding(u.getBuilding());
            st.setBookTitle(bt);
            st.setTotal(Math.max(0, total));
            st.setAvailable(Math.max(0, available));
            st.setInUse(Math.max(0, inUse));
            stocks.save(st);

            ra.addFlashAttribute("success", "Позиция сохранена");
        } catch (Exception e) {
            ra.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/librarian/stock";
    }

    @GetMapping("/templates/stock.xlsx")
    public ResponseEntity<byte[]> stockTemplate(Authentication a) throws Exception {
        var u = auth.requireUser(a.getName());
        XSSFWorkbook wb = new XSSFWorkbook();
        var sh = wb.createSheet("Остатки");
        var h = sh.createRow(0);
        String[] cols = {"Предмет", "Параллель", "Название", "Авторы", "Издательство", "Год издания", "ISBN", "Всего", "Свободно", "В использовании"};
        for (int i = 0; i < cols.length; i++) {
            h.createCell(i).setCellValue(cols[i]);
            sh.setColumnWidth(i, Math.max(12, cols[i].length() + 2) * 256);
        }

        int r = 1;
        var sortedTitles = bookTitles.findAll().stream()
                .sorted(java.util.Comparator.comparing((BookTitle bt) -> bt.getSubject().getName(), String.CASE_INSENSITIVE_ORDER)
                        .thenComparing(BookTitle::getGrade)
                        .thenComparing(BookTitle::getTitle, String.CASE_INSENSITIVE_ORDER))
                .toList();
        for (var bt : sortedTitles) {
            var row = sh.createRow(r++);
            var st = stocks.findOne(u.getBuilding().getId(), bt.getId()).orElse(null);

            row.createCell(0).setCellValue(bt.getSubject() != null ? bt.getSubject().getName() : "");
            row.createCell(1).setCellValue(bt.getGrade() == null ? 0 : bt.getGrade());
            row.createCell(2).setCellValue(bt.getTitle() == null ? "" : bt.getTitle());
            row.createCell(3).setCellValue(bt.getAuthors() == null ? "" : bt.getAuthors());
            row.createCell(4).setCellValue(bt.getPublisher() == null ? "" : bt.getPublisher());
            if (bt.getYear() != null) row.createCell(5).setCellValue(bt.getYear());
            row.createCell(6).setCellValue(bt.getIsbn() == null ? "" : bt.getIsbn());
            row.createCell(7).setCellValue(st == null ? 0 : st.getTotal());
            row.createCell(8).setCellValue(st == null ? 0 : st.getAvailable());
            row.createCell(9).setCellValue(st == null ? 0 : st.getInUse());
        }

        try (wb) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            wb.write(out);
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=books_for_inventory.xlsx")
                    .body(out.toByteArray());
        }
    }

    @GetMapping("/templates/stock-empty.xlsx")
    public ResponseEntity<byte[]> stockEmptyTemplate() throws Exception {
        XSSFWorkbook wb = new XSSFWorkbook();
        var sh = wb.createSheet("Остатки");
        var h = sh.createRow(0);
        String[] cols = {"Предмет", "Параллель", "Название", "Авторы", "Издательство", "Год издания", "ISBN", "Всего", "Свободно", "В использовании"};
        for (int i = 0; i < cols.length; i++) {
            h.createCell(i).setCellValue(cols[i]);
            sh.setColumnWidth(i, Math.max(12, cols[i].length() + 2) * 256);
        }
        try (wb) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            wb.write(out);
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=template_librarian_stock_empty.xlsx")
                    .body(out.toByteArray());
        }
    }
}
