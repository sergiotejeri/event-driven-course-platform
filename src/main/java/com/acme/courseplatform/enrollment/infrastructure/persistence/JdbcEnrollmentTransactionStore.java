package com.acme.courseplatform.enrollment.infrastructure.persistence;

import com.acme.courseplatform.enrollment.application.port.EnrollmentTransactionStore;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcEnrollmentTransactionStore implements EnrollmentTransactionStore {

  private final JdbcTemplate jdbc;

  public JdbcEnrollmentTransactionStore(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  @Override
  public boolean activatePendingPayment(UUID enrollmentId) {
    return jdbc.update(
            "update enrollments set status='ACTIVE',updated_at=now() where id=? and status='PENDING_PAYMENT'",
            enrollmentId)
        == 1;
  }

  @Override
  public boolean cancelPendingPayment(UUID enrollmentId) {
    return jdbc.update(
            "with cancelled as (update enrollments set status='CANCELLED',cancelled_at=now(),updated_at=now() where id=? and status='PENDING_PAYMENT' returning course_id) update courses set occupied_seats=occupied_seats-1,updated_at=now() where id=(select course_id from cancelled)",
            enrollmentId)
        == 1;
  }

  @Override
  public CancellationResult cancelWithPendingPayment(UUID enrollmentId, Instant cancelledAt) {
    CancellationSnapshot state =
        jdbc.query(
            "select p.status as payment_status,e.status as enrollment_status from payments p join enrollments e on e.id=p.enrollment_id where e.id=? for update of p",
            result ->
                result.next()
                    ? new CancellationSnapshot(
                        result.getString("payment_status"), result.getString("enrollment_status"))
                    : null,
            enrollmentId);
    if (state == null) {
      return CancellationResult.INVALID_STATE;
    }
    if (state.enrollmentStatus().equals("CANCELLED")) {
      return CancellationResult.ALREADY_CANCELLED;
    }
    if (!state.paymentStatus().equals("PENDING")) {
      return CancellationResult.PAYMENT_TERMINAL;
    }
    if (!state.enrollmentStatus().equals("PENDING_PAYMENT")
        && !state.enrollmentStatus().equals("ACTIVE")) {
      return CancellationResult.INVALID_STATE;
    }
    int paymentUpdated =
        jdbc.update(
            "update payments set status='FAILED',failed_at=?,updated_at=? where enrollment_id=? and status='PENDING'",
            Timestamp.from(cancelledAt),
            Timestamp.from(cancelledAt),
            enrollmentId);
    if (paymentUpdated != 1) {
      return CancellationResult.PAYMENT_TERMINAL;
    }
    int enrollmentUpdated =
        jdbc.update(
            "with cancelled as (update enrollments set status='CANCELLED',cancelled_at=?,updated_at=? where id=? and status in ('PENDING_PAYMENT','ACTIVE') returning course_id) update courses set occupied_seats=occupied_seats-1,updated_at=? where id=(select course_id from cancelled) and occupied_seats>0",
            Timestamp.from(cancelledAt),
            Timestamp.from(cancelledAt),
            enrollmentId,
            Timestamp.from(cancelledAt));
    if (enrollmentUpdated != 1) {
      throw new IllegalStateException("Enrollment cancellation lost after locking payment");
    }
    return CancellationResult.CANCELLED;
  }

  @Override
  public Optional<EnrollmentState> findState(UUID enrollmentId) {
    return jdbc.query(
        "select course_id,status,progress from enrollments where id=?",
        result ->
            result.next()
                ? Optional.of(
                    new EnrollmentState(
                        result.getObject("course_id", UUID.class),
                        result.getString("status"),
                        result.getInt("progress")))
                : Optional.empty(),
        enrollmentId);
  }

  @Override
  public ProgressUpdate updateProgress(UUID enrollmentId, int progress, Instant occurredAt) {
    var updated =
        jdbc.query(
            "update enrollments set progress=?,status=case when ?=100 then 'COMPLETED' else status end,completed_at=case when ?=100 then ? else completed_at end,updated_at=? where id=? and status='ACTIVE' and progress<=? returning status",
            result -> result.next() ? result.getString("status") : null,
            progress,
            progress,
            progress,
            Timestamp.from(occurredAt),
            Timestamp.from(occurredAt),
            enrollmentId,
            progress);
    if (updated != null) {
      return updated.equals("COMPLETED") ? ProgressUpdate.COMPLETED : ProgressUpdate.UPDATED;
    }
    var state = findState(enrollmentId).orElse(null);
    if (state == null) {
      return ProgressUpdate.INVALID_STATE;
    }
    if (state.progress() > progress) {
      return ProgressUpdate.STALE;
    }
    if (state.status().equals("COMPLETED") && state.progress() == progress) {
      return ProgressUpdate.UNCHANGED;
    }
    return ProgressUpdate.INVALID_STATE;
  }

  @Override
  public void appendCompletedEvent(
      UUID eventId, UUID enrollmentId, UUID courseId, Instant completedAt) {
    String payload =
        "{\"enrollmentId\":\""
            + enrollmentId
            + "\",\"courseId\":\""
            + courseId
            + "\",\"completedAt\":\""
            + completedAt
            + "\"}";
    jdbc.update(
        "insert into outbox_events(event_id,event_type,event_version,aggregate_type,aggregate_id,payload,correlation_id,occurred_at) values (?,'EnrollmentCompletedV1',1,'Enrollment',?,cast(? as jsonb),?,?)",
        eventId,
        enrollmentId,
        payload,
        UUID.randomUUID(),
        Timestamp.from(completedAt));
  }

  private record CancellationSnapshot(String paymentStatus, String enrollmentStatus) {}
}
