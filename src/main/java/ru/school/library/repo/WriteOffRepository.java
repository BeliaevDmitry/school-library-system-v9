package ru.school.library.repo;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.school.library.entity.WriteOff;

import java.util.List;

public interface WriteOffRepository extends JpaRepository<WriteOff, Long> {
    List<WriteOff> findByBuilding_IdOrderByCreatedAtDesc(Long buildingId);
    List<WriteOff> findByStatusOrderByCreatedAtDesc(WriteOff.Status status);
}
