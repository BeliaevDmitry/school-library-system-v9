package ru.school.library.repo; import org.springframework.data.jpa.repository.JpaRepository; import ru.school.library.entity.BookTitle; import java.util.*; public interface BookTitleRepository extends JpaRepository<BookTitle,Long>{
  Optional<BookTitle> findByIsbnAndGradeAndSubject_Id(String isbn, Integer grade, Long subjectId);
  Optional<BookTitle> findByExternalKeyAndGradeAndSubject_Id(String externalKey, Integer grade, Long subjectId);
  Optional<BookTitle> findByTitleIgnoreCaseAndGradeAndSubject_Id(String title, Integer grade, Long subjectId);
}
 