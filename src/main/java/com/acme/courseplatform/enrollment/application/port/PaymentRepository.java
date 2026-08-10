package com.acme.courseplatform.enrollment.application.port;

import java.math.BigDecimal;
import java.util.UUID;

public interface PaymentRepository {

  void savePending(
      UUID paymentId, UUID enrollmentId, BigDecimal amount, String currency, String idempotencyKey);
}
