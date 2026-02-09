package ru.school.library.repo;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import ru.school.library.entity.Stock;

import java.util.List;
import java.util.Optional;

public interface StockRepository extends JpaRepository<Stock,Long> {
    List<Stock> findByBuilding_Id(Long buildingId);

    @Query("select s from Stock s where s.building.id = :buildingId and s.bookTitle.id = :bookTitleId")
    Optional<Stock> findOne(Long buildingId, Long bookTitleId);

    @Query("select s.available from Stock s where s.building.id = :buildingId and s.bookTitle.id = :bookTitleId")
    Optional<Integer> findAvailable(Long buildingId, Long bookTitleId);
}
