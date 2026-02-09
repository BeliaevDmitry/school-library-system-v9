package ru.school.library.service;

import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.*;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import ru.school.library.entity.*;
import ru.school.library.repo.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class ExcelImportService {
    private final BuildingRepository buildings;
    private final SubjectRepository subjects;
    private final BookTitleRepository bookTitles;
    private final StockRepository stocks;
    private final CurriculumItemRepository curriculum;
    private final ClassGroupRepository classes;
    private final FutureClassGroupRepository futureClasses;

    public void importLibrarianStock(MultipartFile file, Long buildingId) throws Exception {
        try (Workbook wb = WorkbookFactory.create(file.getInputStream())) {
            Sheet sh = wb.getSheetAt(0);
            Building building = buildings.findById(buildingId)
                    .orElseThrow(() -> new RuntimeException("Корпус не найден"));

            int headerRowIdx = findLibrarianHeaderRow(sh);
            if (headerRowIdx < 0) {
                throw new RuntimeException("Не найден заголовок шаблона остатков библиотекаря");
            }

            Row header = sh.getRow(headerRowIdx);
            java.util.Map<String, Integer> col = new java.util.HashMap<>();
            for (Cell cell : header) {
                String h = cell.getStringCellValue();
                if (h != null) col.put(h.trim().toLowerCase(), cell.getColumnIndex());
            }

            java.util.List<String> errors = new java.util.ArrayList<>();
            int processed = 0;
            for (int rIdx = headerRowIdx + 1; rIdx <= sh.getLastRowNum(); rIdx++) {
                Row r = sh.getRow(rIdx);
                if (r == null) continue;
                try {
                    String subjectName = getStringByAnyHeader(r, col, "предмет", "subject");
                    int grade = parseInt(getStringByAnyHeader(r, col, "параллель", "grade"));
                    String title = getStringByAnyHeader(r, col, "название", "title");
                    String authors = getStringByAnyHeader(r, col, "авторы", "authors");
                    String publisher = getStringByAnyHeader(r, col, "издательство", "publisher");
                    Integer year = parseIntNullable(getStringByAnyHeader(r, col, "год издания", "year"));
                    String isbn = getStringByAnyHeader(r, col, "isbn");
                    int total = parseInt(getStringByAnyHeader(r, col, "всего", "total"));
                    int available = parseInt(getStringByAnyHeader(r, col, "свободно", "available"));
                    int inUse = parseInt(getStringByAnyHeader(r, col, "в использовании", "inuse"));

                    if ((title == null || title.isBlank()) && (subjectName == null || subjectName.isBlank())) {
                        continue;
                    }

                    Subject subject = subjects.findByNameIgnoreCase(subjectName)
                            .orElseGet(() -> {
                                Subject s = new Subject();
                                s.setName(subjectName);
                                return subjects.save(s);
                            });

                    BookTitle bt = null;
                    if (isbn != null && !isbn.isBlank()) {
                        bt = bookTitles.findByIsbnAndGradeAndSubject_Id(isbn.trim(), grade, subject.getId()).orElse(null);
                    }
                    if (bt == null) {
                        bt = bookTitles.findByTitleIgnoreCaseAndGradeAndSubject_Id(title, grade, subject.getId()).orElse(null);
                    }
                    if (bt == null) {
                        bt = new BookTitle();
                        bt.setGrade(grade);
                        bt.setSubject(subject);
                    }
                    bt.setTitle(title);
                    bt.setAuthors(authors);
                    bt.setPublisher(publisher);
                    bt.setYear(year);
                    bt.setIsbn(isbn == null || isbn.isBlank() ? null : isbn.trim());
                    bt = bookTitles.save(bt);

                    Stock st = stocks.findOne(building.getId(), bt.getId()).orElseGet(Stock::new);
                    st.setBuilding(building);
                    st.setBookTitle(bt);
                    st.setTotal(Math.max(0, total));
                    st.setAvailable(Math.max(0, available));
                    st.setInUse(Math.max(0, inUse));
                    stocks.save(st);
                    processed++;
                } catch (Exception ex) {
                    errors.add("Строка " + (rIdx + 1) + ": " + ex.getMessage());
                    if (errors.size() >= 30) break;
                }
            }

            if (!errors.isEmpty()) {
                throw new RuntimeException("Импорт остатков завершён с ошибками. Обработано строк: " + processed + ". Примеры:\n" + String.join("\n", errors));
            }
        }
    }

// Реестр: buildingCode | grade | subject | title | authors | year | isbn | total | available | inUse
    // Реестр: поддерживаем 2 формата
// Формат A (наш шаблон): buildingCode | grade | subject | title | authors | year | isbn | total | available | inUse
// Формат B (реестр МЭШ): колонки (рус.): Название, Предмет, Параллель, Автор(-ы), Издательство, Год издания,
//                         № ФПУ, Общее кол-во экземпляров, Кол-во свободных экземпляров
// Для МЭШ buildingCode передаём отдельно (по умолчанию удобно грузить в "0" = Центральный фонд)
public void importRegistry(MultipartFile file, String buildingCode) throws Exception {
    try (Workbook wb = WorkbookFactory.create(file.getInputStream())) {
        Sheet sh = wb.getSheetAt(0);

        int headerRowIdx = findHeaderRow(sh);
        if (headerRowIdx < 0) {
            throw new RuntimeException("Не смог найти строку заголовков в реестре. Ожидал либо шаблон, либо реестр МЭШ.");
        }

        // Собираем мапу "название колонки -> индекс"
        Row header = sh.getRow(headerRowIdx);
        java.util.Map<String, Integer> col = new java.util.HashMap<>();
        for (Cell cell : header) {
            String h = cell.getStringCellValue();
            if (h != null) col.put(h.trim().toLowerCase(), cell.getColumnIndex());
        }

        boolean isMesh = col.containsKey("название") && col.containsKey("предмет") && col.containsKey("параллель");

        Building building = buildings.findByCode(normalizeBuildingCode(buildingCode))
                .orElseThrow(() -> new RuntimeException("Unknown building code: " + buildingCode));

        java.util.List<String> errors = new java.util.ArrayList<>();
        int processed = 0;

        for (int rIdx = headerRowIdx + 1; rIdx <= sh.getLastRowNum(); rIdx++) {
            Row r = sh.getRow(rIdx);
            if (r == null) continue;

            try {
                if (isMesh) {
                    // МЭШ
                    String title = getStringByHeader(r, col, "название");
                    if (title == null || title.isBlank()) continue; // пустая строка

                    String subjectName = getStringByHeader(r, col, "предмет");
                    int grade = parseGrade(getStringByHeader(r, col, "параллель"));
                    String authors = getStringByHeader(r, col, "автор(-ы)");
                    String publisher = getStringByHeader(r, col, "издательство");
                    String yearRaw = getStringByHeader(r, col, "год издания");
                    String fpu = getStringByHeader(r, col, "№ фпу");
                    int total = parseInt(getStringByHeader(r, col, "общее кол-во экземпляров"));
                    int available = parseInt(getStringByHeader(r, col, "кол-во свободных экземпляров"));
                    int inUse = Math.max(0, total - available);
                    List<Integer> years = parseYearCandidates(yearRaw);

                    Subject subject = subjects.findByNameIgnoreCase(subjectName)
                            .orElseGet(() -> {
                                Subject s = new Subject();
                                s.setName(subjectName);
                                return subjects.save(s);
                            });

                    for (int i = 0; i < years.size(); i++) {
                        Integer year = years.get(i);
                        int totalPart = splitPart(total, years.size(), i);
                        int availablePart = splitPart(available, years.size(), i);
                        int inUsePart = Math.max(0, totalPart - availablePart);

                        BookTitle bt = findOrCreateMeshTitle(fpu, grade, subject, title, authors, publisher, year, years.size() > 1);

                        Stock st = stocks.findOne(building.getId(), bt.getId()).orElse(null);
                        if (st == null) {
                            st = new Stock();
                            st.setBuilding(building);
                            st.setBookTitle(bt);
                            st.setTotal(0);
                            st.setAvailable(0);
                            st.setInUse(0);
                        }
                        st.setTotal(totalPart);
                        st.setAvailable(availablePart);
                        st.setInUse(inUsePart);
                        stocks.save(st);
                    }

                    processed++;
                } else {
                    // Наш шаблон (фиксированные позиции)
                    String buildingCodeCell = getString(r,0);
                    int grade = getInt(r,1);
                    String subjectName = getString(r,2);
                    String title = getString(r,3);
                    String authors = getString(r,4);
                    Integer year = getIntNullable(r,5);
                    String isbn = getString(r,6);
                    int total = getInt(r,7);
                    int available = getInt(r,8);
                    int inUse = getInt(r,9);

                    Building b = buildings.findByCode(normalizeBuildingCode(buildingCodeCell))
                            .orElseThrow(() -> new RuntimeException("Unknown building code: " + buildingCodeCell));

                    Subject subject = subjects.findByNameIgnoreCase(subjectName)
                            .orElseGet(() -> {
                                Subject s = new Subject();
                                s.setName(subjectName);
                                return subjects.save(s);
                            });

                    BookTitle bt;
                    if (isbn != null && !isbn.isBlank()) {
                        bt = bookTitles.findByIsbnAndGradeAndSubject_Id(isbn, grade, subject.getId()).orElse(null);
                    } else {
                        bt = null;
                    }

                    if (bt == null) {
                        bt = new BookTitle();
                        bt.setGrade(grade);
                        bt.setSubject(subject);
                        bt.setTitle(title);
                        bt.setAuthors(authors);
                        bt.setYear(year);
                        bt.setIsbn(isbn);
                        bt = bookTitles.save(bt);
                    } else {
                        bt.setTitle(title);
                        bt.setAuthors(authors);
                        bt.setYear(year);
                        bookTitles.save(bt);
                    }

                    Stock st = stocks.findOne(b.getId(), bt.getId()).orElse(null);
                    if (st == null) {
                        st = new Stock();
                        st.setBuilding(b);
                        st.setBookTitle(bt);
                        st.setTotal(0);
                        st.setAvailable(0);
                        st.setInUse(0);
                    }
                    st.setTotal(total);
                    st.setAvailable(available);
                    st.setInUse(inUse);
                    stocks.save(st);

                    processed++;
                }
            } catch (Exception ex) {
                errors.add("Строка " + (rIdx + 1) + ": " + ex.getMessage());
                if (errors.size() >= 30) break;
            }
        }

        if (!errors.isEmpty()) {
            throw new RuntimeException("Импорт завершён с ошибками. Обработано строк: " + processed + ". Примеры:\n" + String.join("\n", errors));
        }
    }
}

private int findHeaderRow(Sheet sh) {
    // Ищем либо наш шаблон (первая строка: buildingCode...), либо МЭШ (есть "Название", "Предмет", "Параллель")
    for (int i = 0; i <= Math.min(50, sh.getLastRowNum()); i++) {
        Row r = sh.getRow(i);
        if (r == null) continue;
        String c0 = cellString(r,0).toLowerCase();
        if (c0.equals("buildingcode") || c0.contains("код корпуса")) return i;

        // МЭШ: строка с заголовками на русском
        boolean hasName = false, hasSubject = false, hasGrade = false;
        for (int c = 0; c < 30; c++) {
            String v = cellString(r,c).toLowerCase();
            if (v.equals("название")) hasName = true;
            if (v.equals("предмет")) hasSubject = true;
            if (v.equals("параллель")) hasGrade = true;
        }
        if (hasName && hasSubject && hasGrade) return i;
    }
    return -1;
}

private String getStringByHeader(Row r, java.util.Map<String,Integer> col, String headerRu) {
    Integer idx = col.get(headerRu.toLowerCase());
    if (idx == null) return "";
    return cellString(r, idx);
}

private String getStringByAnyHeader(Row r, java.util.Map<String,Integer> col, String... headers) {
    for (String h : headers) {
        Integer idx = col.get(h.toLowerCase());
        if (idx != null) return cellString(r, idx);
    }
    return "";
}

private int findLibrarianHeaderRow(Sheet sh) {
    for (int i = 0; i <= Math.min(30, sh.getLastRowNum()); i++) {
        Row r = sh.getRow(i);
        if (r == null) continue;
        boolean hasTitle = false;
        boolean hasSubject = false;
        for (int c = 0; c < 20; c++) {
            String v = cellString(r, c).toLowerCase();
            if (v.equals("название") || v.equals("title")) hasTitle = true;
            if (v.equals("предмет") || v.equals("subject")) hasSubject = true;
        }
        if (hasTitle && hasSubject) return i;
    }
    return -1;
}

private String cellString(Row r, int idx) {
    Cell c = r.getCell(idx);
    if (c == null) return "";
    if (c.getCellType() == CellType.NUMERIC) {
        double d = c.getNumericCellValue();
        long l = (long) d;
        if (Math.abs(d - l) < 1e-9) return String.valueOf(l);
        return String.valueOf(d);
    }
    if (c.getCellType() == CellType.BOOLEAN) return String.valueOf(c.getBooleanCellValue());
    c.setCellType(CellType.STRING);
    return c.getStringCellValue().trim();
}

private int parseGrade(String raw) {
    if (raw == null) return 0;
    String s = raw.trim();
    if (s.isBlank()) return 0;
    // берём первое число в строке
    java.util.regex.Matcher m = java.util.regex.Pattern.compile("(\\d{1,2})").matcher(s);
    if (m.find()) return Integer.parseInt(m.group(1));
    return 0;
}

private int parseInt(String raw) {
    if (raw == null) return 0;
    String s = raw.trim();
    if (s.isBlank()) return 0;
    return (int) Double.parseDouble(s.replace(",", "."));
}

private Integer parseIntNullable(String raw) {
    if (raw == null) return null;
    String s = raw.trim();
    if (s.isBlank()) return null;
    return (int) Double.parseDouble(s.replace(",", "."));
}


private BookTitle findOrCreateMeshTitle(String fpu, int grade, Subject subject, String title, String authors, String publisher, Integer year, boolean splitByYears) {
    String externalKey = fpu == null ? null : fpu.trim();
    String effectiveKey = externalKey;
    if (splitByYears && externalKey != null && !externalKey.isBlank() && year != null) {
        effectiveKey = externalKey + "#" + year;
    }
    BookTitle bt = null;
    if (effectiveKey != null && !effectiveKey.isBlank()) {
        bt = bookTitles.findByExternalKeyAndGradeAndSubject_Id(effectiveKey, grade, subject.getId()).orElse(null);
    }
    if (bt == null) {
        bt = new BookTitle();
        bt.setExternalKey(effectiveKey);
        bt.setGrade(grade);
        bt.setSubject(subject);
        bt.setTitle(title);
        bt.setAuthors(authors);
        bt.setPublisher(publisher);
        bt.setYear(year);
        bt.setIsbn(null);
        return bookTitles.save(bt);
    }
    bt.setTitle(title);
    bt.setAuthors(authors);
    bt.setPublisher(publisher);
    bt.setYear(year);
    return bookTitles.save(bt);
}

private List<Integer> parseYearCandidates(String raw) {
    if (raw == null || raw.isBlank()) {
        List<Integer> single = new ArrayList<>();
        single.add(null);
        return single;
    }
    List<Integer> years = new ArrayList<>();
    java.util.regex.Matcher m = java.util.regex.Pattern.compile("(19\\d{2}|20\\d{2})").matcher(raw);
    while (m.find()) {
        int y = Integer.parseInt(m.group(1));
        if (!years.contains(y)) years.add(y);
    }
    if (years.isEmpty()) {
        List<Integer> single = new ArrayList<>();
        single.add(parseIntNullable(raw));
        return single;
    }
    return years;
}

private int splitPart(int total, int parts, int idx) {
    if (parts <= 1) return total;
    int base = total / parts;
    int remainder = Math.floorMod(total, parts);
    return idx < remainder ? base + 1 : base;
}




    // Учебный план: grade | subject | isbn | perStudent
    
// Старый реестр (закупки / суфф-шаблон):
// Параллель: | Наименование учебника: | Предмет: | Издательство: | ФП: | ... | Количество учебников:
// Лист1, заголовки обычно в первой строке.
public void importLegacyRegistry(MultipartFile file, String buildingCode) throws Exception {
    try (Workbook wb = WorkbookFactory.create(file.getInputStream())) {
        Sheet sh = wb.getSheetAt(0);

        int headerRowIdx = -1;
        for (int i = 0; i <= Math.min(30, sh.getLastRowNum()); i++) {
            Row r = sh.getRow(i);
            if (r == null) continue;
            String c0 = cellString(r,0).toLowerCase();
            String c1 = cellString(r,1).toLowerCase();
            if (c0.contains("паралл") && c1.contains("наимен")) { headerRowIdx = i; break; }
        }
        if (headerRowIdx < 0) throw new RuntimeException("Не найден заголовок старого реестра (ожидал: 'Параллель:' и 'Наименование учебника:')");

        Building building = buildings.findByCode(normalizeBuildingCode(buildingCode))
                .orElseThrow(() -> new RuntimeException("Unknown building code: " + buildingCode));

        java.util.List<String> errors = new java.util.ArrayList<>();
        int processed = 0;

        for (int rIdx = headerRowIdx + 1; rIdx <= sh.getLastRowNum(); rIdx++) {
            Row r = sh.getRow(rIdx);
            if (r == null) continue;
            try {
                String gradeRaw = cellString(r,0);
                String title = cellString(r,1);
                String subjectName = cellString(r,2);
                String publisher = cellString(r,3);
                String qtyRaw = cellString(r,7);

                if ((title == null || title.isBlank()) && (gradeRaw == null || gradeRaw.isBlank())) continue;

                int grade = parseGrade(gradeRaw);
                int total = parseInt(qtyRaw);
                int available = total; // для старого реестра обычно это закупка/наличие, свободные считаем = total
                int inUse = 0;

                Subject subject = subjects.findByNameIgnoreCase(subjectName)
                        .orElseGet(() -> {
                            Subject s = new Subject();
                            s.setName(subjectName);
                            return subjects.save(s);
                        });

                BookTitle bt = bookTitles.findByTitleIgnoreCaseAndGradeAndSubject_Id(title, grade, subject.getId()).orElse(null);
                if (bt == null) {
                    bt = new BookTitle();
                    bt.setGrade(grade);
                    bt.setSubject(subject);
                    bt.setTitle(title);
                    bt.setAuthors(null);
                    bt.setPublisher(publisher);
                    bt.setYear(null);
                    bt.setIsbn(null);
                    bt.setExternalKey(null);
                    bt = bookTitles.save(bt);
                } else {
                    // обновим издательство/название при необходимости
                    bt.setPublisher(publisher);
                    bt.setTitle(title);
                    bookTitles.save(bt);
                }

                Stock st = stocks.findOne(building.getId(), bt.getId()).orElse(null);
                if (st == null) {
                    st = new Stock();
                    st.setBuilding(building);
                    st.setBookTitle(bt);
                    st.setTotal(0);
                    st.setAvailable(0);
                    st.setInUse(0);
                }
                st.setTotal(total);
                st.setAvailable(available);
                st.setInUse(inUse);
                stocks.save(st);

                processed++;
            } catch (Exception ex) {
                errors.add("Строка " + (rIdx + 1) + ": " + ex.getMessage());
                if (errors.size() >= 30) break;
            }
        }

        if (!errors.isEmpty()) {
            throw new RuntimeException("Импорт старого реестра завершён с ошибками. Обработано строк: " + processed + ". Примеры:\n" + String.join("\n", errors));
        }
    }
}
public void importCurriculum(MultipartFile file) throws Exception {
        try (Workbook wb = WorkbookFactory.create(file.getInputStream())) {
            Sheet sh = wb.getSheetAt(0);
            for (Row r : sh) {
                if (r.getRowNum() == 0) continue;

                int grade = getInt(r,0);
                String subjectName = getString(r,1);
                String isbn = getString(r,2);
                int perStudent = getInt(r,3);

                Subject subject = subjects.findByNameIgnoreCase(subjectName)
                        .orElseGet(() -> {
                            Subject s = new Subject();
                            s.setName(subjectName);
                            return subjects.save(s);
                        });

                // Находим книгу по isbn + grade + subject
                BookTitle bt = bookTitles.findByIsbnAndGradeAndSubject_Id(isbn, grade, subject.getId())
                        .orElseThrow(() -> new RuntimeException("Book not found for curriculum: isbn=" + isbn));

                CurriculumItem item = new CurriculumItem();
                item.setGrade(grade);
                item.setSubject(subject);
                item.setBookTitle(bt);
                item.setPerStudent(perStudent);
                curriculum.save(item);
            }
        }
    }

    // Численность (поддерживаем 2 формата):
// Формат A (наш шаблон): buildingCode | grade | letter | students
// Формат B (как у вас): "Номер и буква класса" | students | "корпус"
// Примеры: "10-А" 29 "сп1"
public void importClasses(MultipartFile file) throws Exception {
    try (Workbook wb = WorkbookFactory.create(file.getInputStream())) {
        Sheet sh = wb.getSheetAt(0);
        for (Row r : sh) {
            if (r.getRowNum() == 0) continue;

            String a0 = getString(r, 0);
            String b1 = getString(r, 1);
            String c2 = getString(r, 2);

            // Пропускаем пустые строки
            if ((a0 == null || a0.isBlank()) && (b1 == null || b1.isBlank()) && (c2 == null || c2.isBlank())) {
                continue;
            }

            // Определяем формат:
            // Если первый столбец похож на класс (например 10-А), а третий похож на корпус (сп1/1/корпус 1),
            // то это формат B.
            boolean looksLikeClass = a0 != null && a0.matches("\\s*\\d{1,2}\\s*[-–]?\\s*[А-ЯA-Zа-яa-z]\\s*");
            boolean looksLikeBuilding = c2 != null && !c2.isBlank();

            if (looksLikeClass && looksLikeBuilding) {
                // Формат B: className | students | building
                ParsedClass pc = parseClassName(a0);
                int students = parseIntSafe(b1);
                String buildingCode = normalizeBuildingCode(c2);

                Building building = buildings.findByCode(buildingCode)
                        .orElseThrow(() -> new RuntimeException("Unknown building code: " + buildingCode));

                ClassGroup cg = classes.findByBuilding_IdAndGradeAndLetterIgnoreCase(building.getId(), pc.grade, pc.letter)
                        .orElseGet(ClassGroup::new);
                cg.setBuilding(building);
                cg.setGrade(pc.grade);
                cg.setLetter(pc.letter);
                cg.setStudents(students);
                classes.save(cg);
            } else {
                // Формат A: buildingCode | grade | letter | students
                String buildingCodeRaw = getString(r, 0);
                String buildingCode = normalizeBuildingCode(buildingCodeRaw);
                int grade = getInt(r, 1);
                String letter = getString(r, 2);
                int students = getInt(r, 3);

                Building building = buildings.findByCode(buildingCode)
                        .orElseThrow(() -> new RuntimeException("Unknown building code: " + buildingCode));

                String normalizedLetter = letter == null ? "" : letter.trim().toUpperCase(Locale.ROOT);
                ClassGroup cg = classes.findByBuilding_IdAndGradeAndLetterIgnoreCase(building.getId(), grade, normalizedLetter)
                        .orElseGet(ClassGroup::new);
                cg.setBuilding(building);
                cg.setGrade(grade);
                cg.setLetter(normalizedLetter);
                cg.setStudents(students);
                classes.save(cg);
            }
        }
    }
}

private static class ParsedClass {
    final int grade;
    final String letter;
    ParsedClass(int grade, String letter) { this.grade = grade; this.letter = letter; }
}

// "10-А" / "10А" / "10 - А" -> grade=10, letter="А"
private ParsedClass parseClassName(String raw) {
    String s = raw == null ? "" : raw.trim();
    s = s.replace("–", "-");
    s = s.replaceAll("\\s+", "");
    String[] parts = s.split("-");
    String left;
    String right;
    if (parts.length == 2) {
        left = parts[0];
        right = parts[1];
    } else {
        // Без дефиса: "10А"
        if (s.length() < 2) throw new RuntimeException("Bad class name: " + raw);
        left = s.substring(0, s.length() - 1);
        right = s.substring(s.length() - 1);
    }
    int grade = Integer.parseInt(left);
    String letter = right.toUpperCase();
    // русские буквы в верхний регистр тоже ок
    return new ParsedClass(grade, letter);
}

private int parseIntSafe(String raw) {
    if (raw == null) return 0;
    String s = raw.trim();
    if (s.isBlank()) return 0;
    // иногда в заголовках бывает число/дата — тут мы уже на r>0, так что норм
    return (int) Double.parseDouble(s.replace(",", "."));
}

// Принимаем "сп1"/"sp1"/"корпус 1"/"1" и приводим к "1"
private String normalizeBuildingCode(String raw) {
    if (raw == null) return "";
    String s = raw.trim().toLowerCase();
    s = s.replace("корпус", "").trim();
    // русское "сп" или латиница "sp"
    s = s.replace("сп", "sp");
    if (s.startsWith("sp")) {
        s = s.substring(2).trim();
    }
    // если осталось число
    String digits = s.replaceAll("[^0-9]", "");
    if (!digits.isBlank()) return digits;
    // иначе оставим как есть (на случай, если вы заведёте коды именно "сп1")
    return raw.trim();
}


    private String getString(Row r, int i) {
        Cell c = r.getCell(i);
        if (c == null) return "";
        c.setCellType(CellType.STRING);
        return c.getStringCellValue().trim();
    }
    private int getInt(Row r, int i) {
        Cell c = r.getCell(i);
        if (c == null) return 0;
        if (c.getCellType() == CellType.NUMERIC) return (int)c.getNumericCellValue();
        c.setCellType(CellType.STRING);
        String s = c.getStringCellValue().trim();
        if (s.isBlank()) return 0;
        return Integer.parseInt(s);
    }
    private Integer getIntNullable(Row r, int i) {
        Cell c = r.getCell(i);
        if (c == null) return null;
        if (c.getCellType() == CellType.NUMERIC) return (int)c.getNumericCellValue();
        c.setCellType(CellType.STRING);
        String s = c.getStringCellValue().trim();
        if (s.isBlank()) return null;
        return Integer.parseInt(s);
    }

// Будущий контингент (на следующий учебный год)
// Поддерживаем те же 2 формата, что и для численности (importClasses):
// Формат A: buildingCode | grade | letter | students
// Формат B: "Номер и буква класса" | students | корпус (например 10-А | 29 | сп1)
// academicYear — учебный год (например 2026 означает 2026/2027)
public void importFutureClasses(MultipartFile file, int academicYear) throws Exception {
    try (Workbook wb = WorkbookFactory.create(file.getInputStream())) {
        Sheet sh = wb.getSheetAt(0);
        java.util.List<String> errors = new java.util.ArrayList<>();
        int processed = 0;

        for (Row r : sh) {
            if (r.getRowNum() == 0) continue;

            String a0 = getString(r, 0);
            String b1 = getString(r, 1);
            String c2 = getString(r, 2);

            if ((a0 == null || a0.isBlank()) && (b1 == null || b1.isBlank()) && (c2 == null || c2.isBlank())) {
                continue;
            }

            boolean looksLikeClass = a0 != null && a0.matches("\\s*\\d{1,2}\\s*[-–]?\\s*[А-ЯA-Zа-яa-z]\\s*");
            boolean looksLikeBuilding = c2 != null && !c2.isBlank();

            try {
                if (looksLikeClass && looksLikeBuilding) {
                    ParsedClass pc = parseClassName(a0);
                    int students = parseIntSafe(b1);
                    String buildingCode = normalizeBuildingCode(c2);

                    Building building = buildings.findByCode(buildingCode)
                            .orElseThrow(() -> new RuntimeException("Unknown building code: " + buildingCode));

                    FutureClassGroup cg = futureClasses.findByBuilding_IdAndAcademicYearAndGradeAndLetterIgnoreCase(building.getId(), academicYear, pc.grade, pc.letter)
                            .orElseGet(FutureClassGroup::new);
                    cg.setBuilding(building);
                    cg.setAcademicYear(academicYear);
                    cg.setGrade(pc.grade);
                    cg.setLetter(pc.letter);
                    cg.setStudents(students);
                    futureClasses.save(cg);
                } else {
                    String buildingCodeRaw = getString(r,0);
                    String buildingCode = normalizeBuildingCode(buildingCodeRaw);
                    int grade = getInt(r,1);
                    String letter = getString(r,2);
                    int students = getInt(r,3);

                    Building building = buildings.findByCode(buildingCode)
                            .orElseThrow(() -> new RuntimeException("Unknown building code: " + buildingCode));

                    String normalizedLetter = letter == null ? "" : letter.trim().toUpperCase(Locale.ROOT);
                    FutureClassGroup cg = futureClasses.findByBuilding_IdAndAcademicYearAndGradeAndLetterIgnoreCase(building.getId(), academicYear, grade, normalizedLetter)
                            .orElseGet(FutureClassGroup::new);
                    cg.setBuilding(building);
                    cg.setAcademicYear(academicYear);
                    cg.setGrade(grade);
                    cg.setLetter(normalizedLetter);
                    cg.setStudents(students);
                    futureClasses.save(cg);
                }
                processed++;
            } catch (Exception ex) {
                errors.add("Строка " + (r.getRowNum() + 1) + ": " + ex.getMessage());
                if (errors.size() >= 30) break;
            }
        }

        if (!errors.isEmpty()) {
            throw new RuntimeException("Импорт будущего контингента завершён с ошибками. Обработано строк: " + processed + ". Примеры:\n" + String.join("\n", errors));
        }
    }
}

}
