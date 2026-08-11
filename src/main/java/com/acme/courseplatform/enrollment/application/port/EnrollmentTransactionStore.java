package com.acme.courseplatform.enrollment.application.port;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface EnrollmentTransactionStore {

  boolean activatePendingPayment(UUID enrollmentId);

  boolean cancelPendingPayment(UUID enrollmentId);

  CancellationResult cancelWithPendingPayment(UUID enrollmentId, Instant cancelledAt);

  Optional<EnrollmentState> findState(UUID enrollmentId);

  ProgressUpdate updateProgress(UUID enrollmentId, int progress, Instant occurredAt);

  void appendCompletedEvent(UUID eventId, UUID enrollmentId, UUID courseId, Instant completedAt);

  record EnrollmentState(UUID courseId, String status, int progress) {}

  enum ProgressUpdate {
    UPDATED,
    COMPLETED,
    UNCHANGED,
    STALE,
    INVALID_STATE
  }

  enum CancellationResult {
    CANCELLED,
    ALREADY_CANCELLED,
    PAYMENT_TERMINAL,
    INVALID_STATE
  }
}
