package com.acme.courseplatform.payment.infrastructure.persistence;

import com.acme.courseplatform.payment.application.port.PaymentRepository.PaymentData;
import com.acme.courseplatform.payment.domain.PaymentStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "payments")
public class PaymentJpaEntity {

  @Id private UUID id;

  @Column(name = "enrollment_id")
  private UUID enrollmentId;

  @Column(precision = 12, scale = 2)
  private BigDecimal amount;

  @JdbcTypeCode(SqlTypes.CHAR)
  @Column(length = 3)
  private String currency;

  private String status;

  @Column(name = "idempotency_key", length = 128)
  private String idempotencyKey;

  @Column(name = "created_at")
  private Instant createdAt;

  @Column(name = "updated_at")
  private Instant updatedAt;

  protected PaymentJpaEntity() {}

  private PaymentJpaEntity(
      UUID id, UUID enrollmentId, BigDecimal amount, String currency, String idempotencyKey) {
    this.id = id;
    this.enrollmentId = enrollmentId;
    this.amount = amount;
    this.currency = currency;
    this.status = PaymentStatus.PENDING.name();
    this.idempotencyKey = idempotencyKey;
    this.createdAt = Instant.now();
    this.updatedAt = createdAt;
  }

  public static PaymentJpaEntity pending(
      UUID id, UUID enrollmentId, BigDecimal amount, String currency, String idempotencyKey) {
    return new PaymentJpaEntity(id, enrollmentId, amount, currency, idempotencyKey);
  }

  PaymentData toData() {
    return new PaymentData(
        id, enrollmentId, amount, currency.strip(), PaymentStatus.valueOf(status));
  }
}
