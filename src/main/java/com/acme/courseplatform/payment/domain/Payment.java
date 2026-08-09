package com.acme.courseplatform.payment.domain;

import com.acme.courseplatform.shared.domain.InvalidTransitionException;
import java.math.BigDecimal;
import java.util.UUID;

public class Payment {

  private final UUID id;
  private final UUID enrollmentId;
  private final BigDecimal amount;
  private final String currency;
  private PaymentStatus status;

  private Payment(UUID id, UUID enrollmentId, BigDecimal amount, String currency) {
    this.id = id;
    this.enrollmentId = enrollmentId;
    this.amount = amount;
    this.currency = currency;
    this.status = PaymentStatus.PENDING;
  }

  public static Payment pending(UUID id, UUID enrollmentId, BigDecimal amount, String currency) {
    return new Payment(id, enrollmentId, amount, currency);
  }

  public boolean confirm() {
    if (status == PaymentStatus.CONFIRMED) {
      return false;
    }
    requirePending();
    status = PaymentStatus.CONFIRMED;
    return true;
  }

  public boolean fail() {
    if (status == PaymentStatus.FAILED) {
      return false;
    }
    requirePending();
    status = PaymentStatus.FAILED;
    return true;
  }

  private void requirePending() {
    if (status != PaymentStatus.PENDING) {
      throw new InvalidTransitionException("Payment has already reached a terminal state");
    }
  }

  public PaymentStatus status() {
    return status;
  }
}
