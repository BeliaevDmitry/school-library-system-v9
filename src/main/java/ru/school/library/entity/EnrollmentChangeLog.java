package ru.school.library.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;

@Getter
@Setter
@Entity
@Table(name = "enrollment_change_log")
public class EnrollmentChangeLog {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private OffsetDateTime ts;

    @ManyToOne
    private User actor;

    @ManyToOne
    private Building building;

    @Enumerated(EnumType.STRING)
    @Column(length = 20, nullable = false)
    private Scope scope; // CURRENT / FUTURE

    // для FUTURE обязательно, для CURRENT можно 0
    private int academicYear;

    private int grade;

    @Column(length = 10)
    private String letter;

    private int oldStudents;

    private int newStudents;

    @Enumerated(EnumType.STRING)
    @Column(length = 20, nullable = false)
    private Action action; // UPSERT / DELETE / BULK

    @Column(length = 500)
    private String note;

    public enum Scope { CURRENT, FUTURE }
    public enum Action { UPSERT, DELETE, BULK }
}
