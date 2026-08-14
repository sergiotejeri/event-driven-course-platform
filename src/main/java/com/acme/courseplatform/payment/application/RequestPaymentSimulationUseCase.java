package com.acme.courseplatform.payment.application;

import com.acme.courseplatform.identity.application.AuthorizationService;
import com.acme.courseplatform.identity.application.CurrentActor;
import com.acme.courseplatform.messaging.application.EventContext;
import com.acme.courseplatform.payment.application.port.PaymentRepository;
import com.acme.courseplatform.payment.application.port.PaymentTransactionStore;
import com.acme.courseplatform.payment.domain.PaymentStatus;
import com.acme.courseplatform.shared.api.ConflictException;
import com.acme.courseplatform.shared.api.ResourceNotFoundException;
import com.acme.courseplatform.shared.application.CorrelationContext;
import java.time.Instant;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RequestPaymentSimulationUseCase {

  private static final Set<String> OUTCOMES = Set.of("CONFIRM", "FAIL");

  private final PaymentRepository payments;
  private final PaymentTransactionStore transactions;
  private final AuthorizationService authorization;
  private final CorrelationContext correlation;

  public RequestPaymentSimulationUseCase(
      PaymentRepository payments,
      PaymentTransactionStore transactions,
      AuthorizationService authorization,
      CorrelationContext correlation) {
    this.payments = payments;
    this.transactions = transactions;
    this.authorization = authorization;
    this.correlation = correlation;
  }

  @Transactional
  public UUID request(CurrentActor actor, UUID paymentId, String requestedOutcome) {
    String outcome = requestedOutcome.toUpperCase(Locale.ROOT);
    if (!OUTCOMES.contains(outcome)) {
      throw new IllegalArgumentException("Outcome must be CONFIRM or FAIL");
    }
    authorization.requirePaymentOwnerOrAdmin(actor, paymentId);
    var payment =
        payments
            .findById(paymentId)
            .orElseThrow(() -> new ResourceNotFoundException("Payment", paymentId));
    if (payment.status() != PaymentStatus.PENDING) {
      throw new ConflictException("PAYMENT_ALREADY_PROCESSED", "Payment is no longer pending");
    }
    UUID eventId = UUID.randomUUID();
    transactions.appendSimulationRequested(
        eventId,
        payment.id(),
        payment.enrollmentId(),
        outcome,
        Instant.now(),
        new EventContext(correlation.currentId(), null));
    return eventId;
  }
}
