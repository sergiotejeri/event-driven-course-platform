package com.acme.courseplatform.enrollment.application;

import com.acme.courseplatform.enrollment.application.model.EnrollmentResult;
import com.acme.courseplatform.enrollment.application.model.ReservedCourse;
import com.acme.courseplatform.enrollment.application.port.CourseSeatPort;
import com.acme.courseplatform.enrollment.application.port.EnrollmentIdempotencyPort;
import com.acme.courseplatform.enrollment.application.port.EnrollmentRepository;
import com.acme.courseplatform.enrollment.application.port.PaymentRepository;
import com.acme.courseplatform.enrollment.application.port.StudentPort;
import com.acme.courseplatform.identity.application.CurrentActor;
import com.acme.courseplatform.messaging.application.port.OutboxStore;
import com.acme.courseplatform.messaging.domain.EventEnvelope;
import com.acme.courseplatform.messaging.domain.Events.EnrollmentCreatedV1;
import com.acme.courseplatform.observability.BusinessMetrics;
import com.acme.courseplatform.shared.api.ConflictException;
import com.acme.courseplatform.shared.api.ResourceNotFoundException;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EnrollStudentUseCase {

  private final CourseSeatPort seats;
  private final StudentPort students;
  private final EnrollmentRepository enrollments;
  private final PaymentRepository payments;
  private final EnrollmentIdempotencyPort idempotency;
  private final OutboxStore outbox;
  private final BusinessMetrics metrics;
  private final MeterRegistry meterRegistry;

  public EnrollStudentUseCase(
      CourseSeatPort seats,
      StudentPort students,
      EnrollmentRepository enrollments,
      PaymentRepository payments,
      EnrollmentIdempotencyPort idempotency,
      OutboxStore outbox,
      BusinessMetrics metrics,
      MeterRegistry meterRegistry) {
    this.seats = seats;
    this.students = students;
    this.enrollments = enrollments;
    this.payments = payments;
    this.idempotency = idempotency;
    this.outbox = outbox;
    this.metrics = metrics;
    this.meterRegistry = meterRegistry;
  }

  @Transactional
  public EnrollmentResult enroll(CurrentActor actor, UUID courseId, String idempotencyKey) {
    Timer.Sample sample = metrics.enrollmentStarted(meterRegistry);
    try {
      EnrollmentResult result = doEnroll(actor, courseId, idempotencyKey);
      metrics.enrollmentSucceeded(sample);
      return result;
    } catch (RuntimeException exception) {
      metrics.enrollmentRejected(sample);
      throw exception;
    }
  }

  private EnrollmentResult doEnroll(CurrentActor actor, UUID courseId, String idempotencyKey) {
    String key = requiredKey(idempotencyKey);
    String requestHash = hash(courseId.toString());
    var replay = idempotency.find(actor.userId(), key);
    if (replay.isPresent()) {
      return replay(replay.get(), requestHash);
    }
    if (!idempotency.start(actor.userId(), key, requestHash)) {
      return idempotency
          .find(actor.userId(), key)
          .map(value -> replay(value, requestHash))
          .orElseThrow(() -> new ConflictException("REQUEST_IN_PROGRESS", "Request is processing"));
    }

    UUID studentId =
        students
            .findStudentId(actor.userId())
            .orElseThrow(() -> new ResourceNotFoundException("Student", actor.userId()));
    ReservedCourse course =
        seats
            .reserve(courseId)
            .orElseThrow(
                () ->
                    new ConflictException(
                        "COURSE_NOT_AVAILABLE", "Course is not published or has no seats"));
    UUID enrollmentId = UUID.randomUUID();
    UUID paymentId = UUID.randomUUID();
    enrollments.savePendingPayment(enrollmentId, studentId, courseId);
    payments.savePending(paymentId, enrollmentId, course.price(), course.currency(), key);
    UUID eventId = UUID.randomUUID();
    outbox.append(
        new EventEnvelope<>(
            eventId,
            "EnrollmentCreatedV1",
            1,
            "Enrollment",
            enrollmentId,
            Instant.now(),
            eventId,
            null,
            new EnrollmentCreatedV1(enrollmentId, studentId, courseId)));
    EnrollmentResult result = new EnrollmentResult(enrollmentId, paymentId, false);
    idempotency.complete(actor.userId(), key, result);
    return result;
  }

  private EnrollmentResult replay(EnrollmentIdempotencyPort.Replay replay, String requestHash) {
    if (!replay.requestHash().equals(requestHash)) {
      throw new ConflictException(
          "IDEMPOTENCY_KEY_REUSED", "Idempotency key belongs to a different request");
    }
    if (!replay.completed()) {
      throw new ConflictException("REQUEST_IN_PROGRESS", "Request is processing");
    }
    return new EnrollmentResult(replay.enrollmentId(), replay.paymentId(), true);
  }

  private String requiredKey(String key) {
    if (key == null || key.isBlank() || key.length() > 128) {
      throw new IllegalArgumentException(
          "Idempotency-Key must contain between 1 and 128 characters");
    }
    return key.strip();
  }

  private String hash(String value) {
    try {
      byte[] digest =
          MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
      return HexFormat.of().formatHex(digest);
    } catch (NoSuchAlgorithmException exception) {
      throw new IllegalStateException(exception);
    }
  }
}
