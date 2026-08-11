package com.acme.courseplatform.payment.application.port;

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
      Instant occurredAt);

  void appendSimulationRequested(
      UUID eventId, UUID paymentId, UUID enrollmentId, String outcome, Instant occurredAt);
}
