package com.acme.courseplatform.enrollment.application;

import com.acme.courseplatform.enrollment.application.port.EnrollmentTransactionStore;
import com.acme.courseplatform.messaging.application.ProcessedEventService;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ApplyPaymentResultUseCase {

  private static final String CONSUMER = "enrollment-payment-result-v1";

  private final EnrollmentTransactionStore enrollments;
  private final ProcessedEventService processedEvents;

  public ApplyPaymentResultUseCase(
      EnrollmentTransactionStore enrollments, ProcessedEventService processedEvents) {
    this.enrollments = enrollments;
    this.processedEvents = processedEvents;
  }

  @Transactional
  public void apply(UUID eventId, UUID enrollmentId, boolean confirmed) {
    if (!processedEvents.claim(CONSUMER, eventId)) {
      return;
    }
    if (confirmed) {
      enrollments.activatePendingPayment(enrollmentId);
    } else {
      enrollments.cancelPendingPayment(enrollmentId);
    }
  }
}
