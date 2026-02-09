package ru.school.library.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "future_class_group",
       uniqueConstraints = @UniqueConstraint(columnNames = {"building_id","academic_year","grade","letter"}))
public class FutureClassGroup {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    private Building building;

    @Column(name = "academic_year", nullable = false)
    private int academicYear;

    private int grade;

    @Column(length = 10)
    private String letter;

    private int students;
}
