package ru.school.library.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Getter @Setter
@Entity
@Table(uniqueConstraints = @UniqueConstraint(columnNames = {"building_id","grade","letter"}))
public class ClassGroup {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    private Building building;

    @Column(nullable = false)
    private int grade;

    @Column(nullable = false)
    private String letter; // А/Б/В...

    @Column(nullable = false)
    private int students;
}
