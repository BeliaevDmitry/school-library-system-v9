package ru.school.library.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.school.library.entity.*;
import ru.school.library.repo.*;

import java.time.LocalDateTime;

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
    public void writeOff(Building building, BookTitle bookTitle, int count, String reason, User by) {
        Stock st = stocks.findOne(building.getId(), bookTitle.getId())
                .orElseThrow(() -> new RuntimeException("Нет остатка по книге в корпусе"));

        if (count <= 0) throw new RuntimeException("Количество должно быть > 0");
        if (st.getAvailable() < count) throw new RuntimeException("Недостаточно доступных экземпляров");

        st.setAvailable(st.getAvailable() - count);
        st.setTotal(Math.max(0, st.getTotal() - count));
        stocks.save(st);

        WriteOff w = new WriteOff();
        w.setBuilding(building);
        w.setBookTitle(bookTitle);
        w.setCount(count);
        w.setReason(reason);
        w.setCreatedBy(by);
        w.setCreatedAt(LocalDateTime.now());
        writeOffs.save(w);

        Movement m = new Movement();
        m.setType(Movement.Type.ADJUSTMENT);
        m.setFromBuilding(building);
        m.setToBuilding(null);
        m.setBookTitle(bookTitle);
        m.setCount(count);
        m.setCreatedBy(by);
        m.setCreatedAt(LocalDateTime.now());
        m.setNote("Списание: " + (reason == null ? "" : reason));
        movements.save(m);
    }
}
