package com.acme.courseplatform.messaging.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public final class Events {

  private Events() {}

  public record EnrollmentCreatedV1(UUID enrollmentId, UUID studentId, UUID courseId) {}

  public record PaymentSimulationRequestedV1(UUID paymentId, UUID enrollmentId, String outcome) {}

  public record PaymentConfirmedV1(
      UUID paymentId, UUID enrollmentId, BigDecimal amount, String currency) {}

  public record PaymentFailedV1(UUID paymentId, UUID enrollmentId, String reason) {}

  public record EnrollmentCompletedV1(UUID enrollmentId, UUID courseId, Instant completedAt) {}
}
