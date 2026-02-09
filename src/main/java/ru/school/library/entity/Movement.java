package ru.school.library.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter @Setter
@Entity
public class Movement {
    public enum Type { TRANSFER, ISSUE, RETURN, ADJUSTMENT }

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Type type;

    @ManyToOne
    private Building fromBuilding;

    @ManyToOne
    private Building toBuilding;

    @ManyToOne(optional = false)
    private BookTitle bookTitle;

    @Column(nullable = false)
    private int count;

    @ManyToOne(optional = false)
    private User createdBy;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    private String note;
}
