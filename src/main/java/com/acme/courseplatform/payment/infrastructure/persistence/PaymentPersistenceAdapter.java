package com.acme.courseplatform.payment.infrastructure.persistence;

import com.acme.courseplatform.payment.application.port.PaymentRepository;
import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;

@Repository
public class PaymentPersistenceAdapter
    implements PaymentRepository,
        com.acme.courseplatform.enrollment.application.port.PaymentRepository {

  private final SpringDataPaymentRepository payments;

  public PaymentPersistenceAdapter(SpringDataPaymentRepository payments) {
    this.payments = payments;
  }

  @Override
  public void savePending(
      UUID paymentId,
      UUID enrollmentId,
      BigDecimal amount,
      String currency,
      String idempotencyKey) {
    payments.saveAndFlush(
        PaymentJpaEntity.pending(paymentId, enrollmentId, amount, currency, idempotencyKey));
  }

  @Override
  public Optional<PaymentData> findById(UUID paymentId) {
    return payments.findById(paymentId).map(PaymentJpaEntity::toData);
  }

  @Override
  public Optional<PaymentData> findByEnrollmentId(UUID enrollmentId) {
    return payments.findByEnrollmentId(enrollmentId).map(PaymentJpaEntity::toData);
  }
}
