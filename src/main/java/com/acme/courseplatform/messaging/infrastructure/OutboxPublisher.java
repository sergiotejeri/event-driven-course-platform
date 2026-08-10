package com.acme.courseplatform.messaging.infrastructure;

import com.acme.courseplatform.messaging.application.port.OutboxStore.OutboxMessage;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

@Component
public class OutboxPublisher {

  private final RabbitTemplate rabbit;

  public OutboxPublisher(RabbitTemplate rabbit) {
    this.rabbit = rabbit;
  }

  public PublishResult publish(OutboxMessage message) {
    CorrelationData correlation = new CorrelationData(message.eventId().toString());
    try {
      rabbit.convertAndSend(
          RabbitTopologyConfig.EXCHANGE,
          routingKey(message.eventType()),
          message.payload(),
          amqpMessage -> {
            amqpMessage.getMessageProperties().setMessageId(message.eventId().toString());
            amqpMessage.getMessageProperties().setCorrelationId(message.correlationId().toString());
            amqpMessage.getMessageProperties().setContentType("application/json");
            amqpMessage.getMessageProperties().setHeader("eventType", message.eventType());
            amqpMessage.getMessageProperties().setHeader("eventVersion", message.eventVersion());
            amqpMessage
                .getMessageProperties()
                .setHeader("occurredAt", message.occurredAt().toString());
            if (message.causationId() != null) {
              amqpMessage
                  .getMessageProperties()
                  .setHeader("causationId", message.causationId().toString());
            }
            return amqpMessage;
          },
          correlation);
      CorrelationData.Confirm confirm =
          correlation.getFuture().get(Duration.ofSeconds(5).toMillis(), TimeUnit.MILLISECONDS);
      if (confirm.ack()) {
        return PublishResult.ack();
      }
      return PublishResult.rejected(
          confirm.reason() == null ? "publisher confirm failed" : confirm.reason());
    } catch (Exception exception) {
      return PublishResult.rejected(
          exception.getMessage() == null
              ? exception.getClass().getSimpleName()
              : exception.getMessage());
    }
  }

  static String routingKey(String eventType) {
    return switch (eventType) {
      case "EnrollmentCreatedV1" -> "enrollment.created.v1";
      case "PaymentSimulationRequestedV1" -> "payment.simulation-requested.v1";
      case "PaymentConfirmedV1" -> "payment.confirmed.v1";
      case "PaymentFailedV1" -> "payment.failed.v1";
      case "EnrollmentCompletedV1" -> "enrollment.completed.v1";
      default -> throw new IllegalArgumentException("Unsupported event type " + eventType);
    };
  }

  public record PublishResult(boolean acknowledged, String error) {

    public static PublishResult ack() {
      return new PublishResult(true, null);
    }

    public static PublishResult rejected(String error) {
      return new PublishResult(false, error);
    }
  }
}
