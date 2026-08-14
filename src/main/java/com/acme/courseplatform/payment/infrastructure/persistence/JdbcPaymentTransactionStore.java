package com.acme.courseplatform.payment.infrastructure.persistence;

import com.acme.courseplatform.messaging.application.EventContext;
import com.acme.courseplatform.payment.application.port.PaymentTransactionStore;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcPaymentTransactionStore implements PaymentTransactionStore {

  private final JdbcTemplate jdbc;

  public JdbcPaymentTransactionStore(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  @Override
  public boolean completePending(
      UUID eventId,
      UUID paymentId,
      UUID enrollmentId,
      boolean confirmed,
      BigDecimal amount,
      String currency,
      Instant occurredAt,
      EventContext context) {
    String status = confirmed ? "CONFIRMED" : "FAILED";
    String timestamp = confirmed ? "confirmed_at" : "failed_at";
    int updated =
        jdbc.update(
            "update payments set status=?,"
                + timestamp
                + "=?,updated_at=? where id=? and enrollment_id=? and status='PENDING'",
            status,
            Timestamp.from(occurredAt),
            Timestamp.from(occurredAt),
            paymentId,
            enrollmentId);
    if (updated == 0) {
      return false;
    }
    String eventType = confirmed ? "PaymentConfirmedV1" : "PaymentFailedV1";
    String payload =
        confirmed
            ? "{\"paymentId\":\""
                + paymentId
                + "\",\"enrollmentId\":\""
                + enrollmentId
                + "\",\"amount\":"
                + amount
                + ",\"currency\":\""
                + currency
                + "\"}"
            : "{\"paymentId\":\""
                + paymentId
                + "\",\"enrollmentId\":\""
                + enrollmentId
                + "\",\"reason\":\"SIMULATED_FAILURE\"}";
    jdbc.update(
        "insert into outbox_events(event_id,event_type,event_version,aggregate_type,aggregate_id,payload,correlation_id,causation_id,occurred_at) values (?,?,1,'Payment',?,cast(? as jsonb),?,?,?)",
        eventId,
        eventType,
        paymentId,
        payload,
        context.correlationId(),
        context.causationId(),
        Timestamp.from(occurredAt));
    return true;
  }

  @Override
  public void appendSimulationRequested(
      UUID eventId,
      UUID paymentId,
      UUID enrollmentId,
      String outcome,
      Instant occurredAt,
      EventContext context) {
    String payload =
        "{\"paymentId\":\""
            + paymentId
            + "\",\"enrollmentId\":\""
            + enrollmentId
            + "\",\"outcome\":\""
            + outcome
            + "\"}";
    jdbc.update(
        "insert into outbox_events(event_id,event_type,event_version,aggregate_type,aggregate_id,payload,correlation_id,occurred_at) values (?,'PaymentSimulationRequestedV1',1,'Payment',?,cast(? as jsonb),?,?)",
        eventId,
        paymentId,
        payload,
        context.correlationId(),
        Timestamp.from(occurredAt));
  }
}
