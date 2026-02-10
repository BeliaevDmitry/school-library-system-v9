package ru.school.library.repo;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.school.library.entity.ClassGroup;

import java.util.List;
import java.util.Optional;

public interface ClassGroupRepository extends JpaRepository<ClassGroup, Long> {
    List<ClassGroup> findByBuilding_Id(Long buildingId);
    Optional<ClassGroup> findByBuilding_IdAndGradeAndLetterIgnoreCase(Long buildingId, int grade, String letter);
}
