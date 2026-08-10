package com.acme.courseplatform.enrollment.infrastructure.persistence;

import com.acme.courseplatform.enrollment.application.port.EnrollmentRepository;
import com.acme.courseplatform.enrollment.application.port.PaymentRepository;
import java.math.BigDecimal;
import java.util.UUID;
import org.springframework.stereotype.Repository;

@Repository
public class EnrollmentPersistenceAdapter implements EnrollmentRepository, PaymentRepository {

  private final SpringDataEnrollmentRepository enrollments;
  private final SpringDataPaymentRepository payments;

  public EnrollmentPersistenceAdapter(
      SpringDataEnrollmentRepository enrollments, SpringDataPaymentRepository payments) {
    this.enrollments = enrollments;
    this.payments = payments;
  }

  @Override
  public void savePendingPayment(UUID enrollmentId, UUID studentId, UUID courseId) {
    enrollments.saveAndFlush(EnrollmentJpaEntity.pendingPayment(enrollmentId, studentId, courseId));
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
}
