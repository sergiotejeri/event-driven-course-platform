package com.acme.courseplatform.payment.application;

import com.acme.courseplatform.payment.application.ProcessPaymentSimulationUseCase.PaymentSimulationCommand;
import com.acme.courseplatform.payment.application.port.PaymentRepository;
import com.acme.courseplatform.shared.api.ResourceNotFoundException;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class ProcessEnrollmentCreatedUseCase {

  private final PaymentRepository payments;
  private final ProcessPaymentSimulationUseCase processor;
  private final String outcome;

  public ProcessEnrollmentCreatedUseCase(
      PaymentRepository payments,
      ProcessPaymentSimulationUseCase processor,
      @Value("${app.payment.enrollment-created-outcome:CONFIRM}") String outcome) {
    if (!outcome.equals("CONFIRM") && !outcome.equals("FAIL")) {
      throw new IllegalArgumentException(
          "app.payment.enrollment-created-outcome must be CONFIRM or FAIL");
    }
    this.payments = payments;
    this.processor = processor;
    this.outcome = outcome;
  }

  public void process(UUID eventId, UUID enrollmentId) {
    var payment =
        payments
            .findByEnrollmentId(enrollmentId)
            .orElseThrow(
                () -> new ResourceNotFoundException("payment for enrollment", enrollmentId));
    processor.process(
        eventId, new PaymentSimulationCommand(payment.id(), payment.enrollmentId(), outcome));
  }
}
