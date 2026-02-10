package ru.school.library.entity;

import jakarta.persistence.*;
import jakarta.persistence.Column;
import lombok.Getter;
import lombok.Setter;

@Getter @Setter
@Entity
@Table(uniqueConstraints = @UniqueConstraint(columnNames = {"building_id","book_title_id"}))
public class Stock {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    private Building building;

    @ManyToOne(optional = false)
    private BookTitle bookTitle;

    @Column(nullable = false)
    private int total;

    @Column(nullable = false)
    private int available;

    @Column(nullable = false)
    private int inUse;

    // Отдельные срезы наличия по источникам
    @Column(nullable = false, columnDefinition = "integer default 0")
    private int meshTotal;

    @Column(nullable = false, columnDefinition = "integer default 0")
    private int suufTotal;

    // Для инвентаризации/аудита (заполняет библиотекарь)
    private int issuedToStudents;
    private int inCabinets;
    @Column(length = 2000)
    private String note;
}
