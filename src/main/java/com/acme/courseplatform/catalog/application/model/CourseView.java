package com.acme.courseplatform.catalog.application.model;

import java.math.BigDecimal;
import java.util.UUID;

public record CourseView(
    UUID id,
    String title,
    String description,
    int estimatedHours,
    String level,
    BigDecimal price,
    String currency,
    int capacity,
    int occupiedSeats,
    String status,
    UUID categoryId,
    UUID instructorId) {}
