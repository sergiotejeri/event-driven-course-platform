package com.acme.courseplatform.payment.infrastructure;

import com.acme.courseplatform.messaging.application.EventContext;
import com.acme.courseplatform.messaging.infrastructure.RabbitTopologyConfig;
import com.acme.courseplatform.payment.application.ProcessPaymentSimulationUseCase;
import com.acme.courseplatform.payment.application.ProcessPaymentSimulationUseCase.PaymentSimulationCommand;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

@Component
public class PaymentSimulationListener {

  private final ProcessPaymentSimulationUseCase payments;
  private final ObjectMapper json;

  public PaymentSimulationListener(ProcessPaymentSimulationUseCase payments, ObjectMapper json) {
    this.payments = payments;
    this.json = json;
  }

  @RabbitListener(queues = RabbitTopologyConfig.SIMULATION_QUEUE)
  public void consume(Message message) {
    String messageId = message.getMessageProperties().getMessageId();
    if (messageId == null) {
      throw new IllegalArgumentException("messageId is required");
    }
    var body = json.readTree(new String(message.getBody(), StandardCharsets.UTF_8));
    UUID eventId = UUID.fromString(messageId);
    payments.process(
        eventId,
        new PaymentSimulationCommand(
            UUID.fromString(body.required("paymentId").asString()),
            UUID.fromString(body.required("enrollmentId").asString()),
            body.required("outcome").asString()),
        EventContext.causedBy(correlationId(message), eventId));
  }

  private UUID correlationId(Message message) {
    String value = message.getMessageProperties().getCorrelationId();
    return value == null
        ? UUID.fromString(message.getMessageProperties().getMessageId())
        : UUID.fromString(value);
  }
}
