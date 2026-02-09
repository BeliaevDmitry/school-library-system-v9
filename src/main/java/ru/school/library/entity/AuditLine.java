package ru.school.library.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Getter @Setter
@Entity
@Table(uniqueConstraints = @UniqueConstraint(columnNames = {"audit_session_id","book_title_id"}))
public class AuditLine {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    private AuditSession auditSession;

    @ManyToOne(optional = false)
    private BookTitle bookTitle;

    @Column(nullable = false)
    private int countFact;

    private String comment;
}
