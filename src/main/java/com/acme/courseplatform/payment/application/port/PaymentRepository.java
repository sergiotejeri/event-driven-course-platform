package com.acme.courseplatform.payment.application.port;

import com.acme.courseplatform.payment.domain.PaymentStatus;
import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

public interface PaymentRepository {

  Optional<PaymentData> findById(UUID paymentId);

  Optional<PaymentData> findByEnrollmentId(UUID enrollmentId);

  record PaymentData(
      UUID id, UUID enrollmentId, BigDecimal amount, String currency, PaymentStatus status) {}
}
