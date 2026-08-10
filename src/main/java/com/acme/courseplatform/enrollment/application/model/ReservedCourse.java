package com.acme.courseplatform.enrollment.application.model;

import java.math.BigDecimal;

public record ReservedCourse(BigDecimal price, String currency) {}
