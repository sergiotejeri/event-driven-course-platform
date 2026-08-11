package com.acme.courseplatform.enrollment.application;

import com.acme.courseplatform.enrollment.application.port.EnrollmentTransactionStore;
import com.acme.courseplatform.identity.application.AuthorizationService;
import com.acme.courseplatform.identity.application.CurrentActor;
import com.acme.courseplatform.shared.api.ResourceNotFoundException;
import com.acme.courseplatform.shared.domain.InvalidTransitionException;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CancelEnrollmentUseCase {

  private final EnrollmentTransactionStore enrollments;
  private final AuthorizationService authorization;

  public CancelEnrollmentUseCase(
      EnrollmentTransactionStore enrollments, AuthorizationService authorization) {
    this.enrollments = enrollments;
    this.authorization = authorization;
  }

  @Transactional
  public CancellationResult cancel(CurrentActor actor, UUID enrollmentId) {
    authorization.requireEnrollmentOwnerOrAdmin(actor, enrollmentId);
    enrollments
        .findState(enrollmentId)
        .orElseThrow(() -> new ResourceNotFoundException("Enrollment", enrollmentId));
    return switch (enrollments.cancelWithPendingPayment(enrollmentId, Instant.now())) {
      case CANCELLED -> new CancellationResult(enrollmentId, "CANCELLED", false);
      case ALREADY_CANCELLED -> new CancellationResult(enrollmentId, "CANCELLED", true);
      case PAYMENT_TERMINAL ->
          throw new InvalidTransitionException("A terminal payment prevents cancellation");
      case INVALID_STATE ->
          throw new InvalidTransitionException(
              "Enrollment cannot be cancelled in its current state");
    };
  }

  public record CancellationResult(UUID enrollmentId, String status, boolean replayed) {}
}
