package ru.school.library.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.school.library.entity.*;
import ru.school.library.repo.MovementRepository;
import ru.school.library.repo.StockRepository;
import ru.school.library.repo.WriteOffRepository;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class WriteOffService {
    private final WriteOffRepository writeOffs;
    private final StockRepository stocks;
    private final MovementRepository movements;

    public WriteOffService(WriteOffRepository writeOffs, StockRepository stocks, MovementRepository movements) {
        this.writeOffs = writeOffs;
        this.stocks = stocks;
        this.movements = movements;
    }

    @Transactional
    public void requestWriteOff(Building building, BookTitle bookTitle, int count, String reason, User by) {
        Stock st = stocks.findOne(building.getId(), bookTitle.getId())
                .orElseThrow(() -> new RuntimeException("Нет остатка по книге в корпусе"));

        if (count <= 0) throw new RuntimeException("Количество должно быть > 0");
        if (st.getTotal() < count) throw new RuntimeException("Нельзя пометить к списанию больше, чем всего в наличии");

        WriteOff w = new WriteOff();
        w.setBuilding(building);
        w.setBookTitle(bookTitle);
        w.setCount(count);
        w.setReason(reason == null ? null : reason.trim());
        w.setCreatedBy(by);
        w.setCreatedAt(LocalDateTime.now());
        w.setStatus(WriteOff.Status.PENDING);
        writeOffs.save(w);
    }

    @Transactional
    public void reviewRequest(Long writeOffId, boolean approve, String note, User reviewer) {
        WriteOff w = writeOffs.findById(writeOffId)
                .orElseThrow(() -> new RuntimeException("Заявка на списание не найдена"));

        if (w.getStatus() != WriteOff.Status.PENDING) {
            throw new RuntimeException("Заявка уже обработана");
        }

        w.setReviewedBy(reviewer);
        w.setReviewedAt(LocalDateTime.now());
        w.setReviewNote(note == null ? null : note.trim());

        if (!approve) {
            w.setStatus(WriteOff.Status.REJECTED);
            writeOffs.save(w);
            return;
        }

        Stock st = stocks.findOne(w.getBuilding().getId(), w.getBookTitle().getId())
                .orElseThrow(() -> new RuntimeException("Нет остатка по книге в корпусе"));

        if (st.getAvailable() < w.getCount()) {
            throw new RuntimeException("Недостаточно свободных экземпляров для подтверждения списания");
        }

        st.setAvailable(st.getAvailable() - w.getCount());
        st.setTotal(Math.max(0, st.getTotal() - w.getCount()));
        stocks.save(st);

        Movement m = new Movement();
        m.setType(Movement.Type.ADJUSTMENT);
        m.setFromBuilding(w.getBuilding());
        m.setToBuilding(null);
        m.setBookTitle(w.getBookTitle());
        m.setCount(w.getCount());
        m.setCreatedBy(reviewer);
        m.setCreatedAt(LocalDateTime.now());
        m.setNote("Списание подтверждено администратором" + (w.getReason() == null ? "" : ": " + w.getReason()));
        movements.save(m);

        w.setStatus(WriteOff.Status.APPROVED);
        writeOffs.save(w);
    }

    @Transactional(readOnly = true)
    public List<WriteOff> pendingRequests() {
        return writeOffs.findByStatusOrderByCreatedAtDesc(WriteOff.Status.PENDING);
    }

    @Transactional
    public int approveAllPending(User reviewer, String note) {
        List<WriteOff> pending = writeOffs.findByStatusOrderByCreatedAtDesc(WriteOff.Status.PENDING);
        int approved = 0;
        for (WriteOff w : pending) {
            reviewRequest(w.getId(), true, note, reviewer);
            approved++;
        }
        return approved;
    }
}
