package com.acme.courseplatform.enrollment.application;

import com.acme.courseplatform.enrollment.application.port.EnrollmentTransactionStore;
import com.acme.courseplatform.identity.application.AuthorizationService;
import com.acme.courseplatform.identity.application.CurrentActor;
import com.acme.courseplatform.messaging.application.EventContext;
import com.acme.courseplatform.shared.application.CorrelationContext;
import com.acme.courseplatform.shared.domain.InvalidTransitionException;
import com.acme.courseplatform.shared.domain.ProgressRegressionException;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UpdateProgressUseCase {

  private final EnrollmentTransactionStore enrollments;
  private final AuthorizationService authorization;
  private final CorrelationContext correlation;

  public UpdateProgressUseCase(
      EnrollmentTransactionStore enrollments,
      AuthorizationService authorization,
      CorrelationContext correlation) {
    this.enrollments = enrollments;
    this.authorization = authorization;
    this.correlation = correlation;
  }

  @Transactional
  public ProgressResult update(CurrentActor actor, UUID enrollmentId, int progress) {
    if (progress < 0 || progress > 100) {
      throw new IllegalArgumentException("Progress must be between 0 and 100");
    }
    authorization.requireEnrollmentOwnerOrAdmin(actor, enrollmentId);
    var state =
        enrollments
            .findState(enrollmentId)
            .orElseThrow(() -> new InvalidTransitionException("Enrollment does not exist"));
    Instant now = Instant.now();
    var result = enrollments.updateProgress(enrollmentId, progress, now);
    return switch (result) {
      case COMPLETED -> {
        enrollments.appendCompletedEvent(
            UUID.randomUUID(),
            enrollmentId,
            state.courseId(),
            now,
            new EventContext(correlation.currentId(), null));
        yield new ProgressResult(enrollmentId, 100, "COMPLETED", true);
      }
      case STALE -> throw new ProgressRegressionException("Enrollment progress cannot decrease");
      case INVALID_STATE ->
          throw new InvalidTransitionException("Progress requires an ACTIVE enrollment");
      case UPDATED -> new ProgressResult(enrollmentId, progress, "ACTIVE", false);
      case UNCHANGED -> {
        var current = enrollments.findState(enrollmentId).orElseThrow();
        yield new ProgressResult(enrollmentId, current.progress(), current.status(), false);
      }
    };
  }

  public record ProgressResult(
      UUID enrollmentId, int progress, String status, boolean completedNow) {}
}
