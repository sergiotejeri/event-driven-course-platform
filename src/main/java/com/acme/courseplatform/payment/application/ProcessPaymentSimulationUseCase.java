package com.acme.courseplatform.payment.application;

import com.acme.courseplatform.messaging.application.ProcessedEventService;
import com.acme.courseplatform.observability.BusinessMetrics;
import com.acme.courseplatform.payment.application.port.PaymentRepository;
import com.acme.courseplatform.payment.application.port.PaymentTransactionStore;
import com.acme.courseplatform.shared.api.ResourceNotFoundException;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProcessPaymentSimulationUseCase {

  private static final String CONSUMER = "payment-simulation-v1";

  private final PaymentRepository payments;
  private final PaymentTransactionStore transactions;
  private final ProcessedEventService processedEvents;
  private final BusinessMetrics metrics;

  public ProcessPaymentSimulationUseCase(
      PaymentRepository payments,
      PaymentTransactionStore transactions,
      ProcessedEventService processedEvents,
      BusinessMetrics metrics) {
    this.payments = payments;
    this.transactions = transactions;
    this.processedEvents = processedEvents;
    this.metrics = metrics;
  }

  @Transactional
  public void process(UUID eventId, PaymentSimulationCommand command) {
    if (!processedEvents.claim(CONSUMER, eventId)) {
      return;
    }
    boolean confirmed = switchOutcome(command.outcome());
    var payment =
        payments
            .findById(command.paymentId())
            .orElseThrow(() -> new ResourceNotFoundException("payment", command.paymentId()));
    if (transactions.completePending(
        UUID.randomUUID(),
        payment.id(),
        command.enrollmentId(),
        confirmed,
        payment.amount(),
        payment.currency(),
        Instant.now())) {
      metrics.paymentOutcome(confirmed);
    }
  }

  private boolean switchOutcome(String outcome) {
    return switch (outcome) {
      case "CONFIRM" -> true;
      case "FAIL" -> false;
      default -> throw new IllegalArgumentException("outcome must be CONFIRM or FAIL");
    };
  }

  public record PaymentSimulationCommand(UUID paymentId, UUID enrollmentId, String outcome) {}
}
