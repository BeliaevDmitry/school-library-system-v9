package ru.school.library.dto;

import lombok.Value;

@Value
public class BuildingStockSummary {
    String name;
    String code;
    long positions;
    long books;
}
