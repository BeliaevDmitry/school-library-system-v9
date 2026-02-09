package ru.school.library.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter @Setter
@Entity
public class WriteOff {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    private Building building;

    @ManyToOne(optional = false)
    private BookTitle bookTitle;

    @Column(nullable = false)
    private int count;

    private String reason;

    @ManyToOne(optional = false)
    private User createdBy;

    @Column(nullable = false)
    private LocalDateTime createdAt;
}
