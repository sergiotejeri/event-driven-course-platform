package com.acme.courseplatform.payment.application.port;

import com.acme.courseplatform.messaging.application.EventContext;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public interface PaymentTransactionStore {

  boolean completePending(
      UUID eventId,
      UUID paymentId,
      UUID enrollmentId,
      boolean confirmed,
      BigDecimal amount,
      String currency,
      Instant occurredAt,
      EventContext context);

  void appendSimulationRequested(
      UUID eventId,
      UUID paymentId,
      UUID enrollmentId,
      String outcome,
      Instant occurredAt,
      EventContext context);
}
