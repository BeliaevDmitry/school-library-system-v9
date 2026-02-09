package ru.school.library.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;

@Data
@Entity
@Table(uniqueConstraints = @UniqueConstraint(columnNames = {"grade","subject_id","book_title_id"}))
public class CurriculumItem {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private int grade;

    @ManyToOne(optional = false)
    private Subject subject;

    @ManyToOne(optional = false)
    private BookTitle bookTitle;

    @Column(nullable = false)
    private int perStudent; // обычно 1
}
