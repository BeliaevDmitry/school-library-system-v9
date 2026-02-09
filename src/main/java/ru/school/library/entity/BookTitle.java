package ru.school.library.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Getter @Setter
@Entity
@Table(indexes = {
        @Index(name = "idx_book_isbn", columnList = "isbn"),
        @Index(name = "idx_book_grade", columnList = "grade")
})
public class BookTitle {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String externalKey;

    @Column(nullable = false)
    private String title;

    private String authors;
    private String publisher;
    private Integer year;

    private String isbn;

    @ManyToOne(optional = false)
    private Subject subject;

    @Column(nullable = false)
    private Integer grade;
}
