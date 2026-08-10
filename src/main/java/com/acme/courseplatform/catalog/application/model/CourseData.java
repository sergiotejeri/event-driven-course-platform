package com.acme.courseplatform.catalog.application.model;

import java.math.BigDecimal;
import java.util.UUID;

public record CourseData(
    String title,
    String description,
    int estimatedHours,
    String level,
    BigDecimal price,
    String currency,
    int capacity,
    UUID categoryId,
    UUID instructorId) {}
