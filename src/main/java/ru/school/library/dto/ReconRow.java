package ru.school.library.dto;

public record ReconRow(
        String buildingCode,
        int grade,
        String subject,
        String title,
        int needed,
        int available,
        int deficit
) {}
