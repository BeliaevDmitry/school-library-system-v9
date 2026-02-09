package ru.school.library.dto;

public record SummaryRow(
        String key,
        int needed,
        int available,
        int deficit
) {}
