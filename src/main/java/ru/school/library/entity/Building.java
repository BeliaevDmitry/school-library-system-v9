package ru.school.library.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Getter @Setter
@Entity
@Table(uniqueConstraints = @UniqueConstraint(columnNames = {"code"}))
public class Building {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Для 8 корпусов: 1..8 или A..H — как удобнее
    @Column(nullable = false)
    private String code;

    @Column(nullable = false)
    private String name;
}
