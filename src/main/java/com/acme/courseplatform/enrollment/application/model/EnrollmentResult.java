package com.acme.courseplatform.enrollment.application.model;

import java.util.UUID;

public record EnrollmentResult(UUID enrollmentId, UUID paymentId, boolean replayed) {}
