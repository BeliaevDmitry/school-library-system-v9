package ru.school.library.repo;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.school.library.entity.EnrollmentChangeLog;

import java.util.List;

public interface EnrollmentChangeLogRepository extends JpaRepository<EnrollmentChangeLog, Long> {
    List<EnrollmentChangeLog> findTop200ByOrderByTsDesc();
}
