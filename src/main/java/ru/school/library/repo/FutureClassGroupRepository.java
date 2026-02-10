package ru.school.library.repo;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.school.library.entity.FutureClassGroup;

import java.util.List;
import java.util.Optional;

public interface FutureClassGroupRepository extends JpaRepository<FutureClassGroup, Long> {
    List<FutureClassGroup> findByBuilding_IdAndAcademicYear(Long buildingId, int academicYear);
    List<FutureClassGroup> findByAcademicYear(int academicYear);
    Optional<FutureClassGroup> findByBuilding_IdAndAcademicYearAndGradeAndLetterIgnoreCase(Long buildingId, int academicYear, int grade, String letter);
    void deleteByAcademicYear(int academicYear);
}
