package com.acme.courseplatform.enrollment.infrastructure;

import com.acme.courseplatform.enrollment.application.ApplyPaymentResultUseCase;
import com.acme.courseplatform.messaging.infrastructure.RabbitTopologyConfig;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

@Component
public class PaymentResultListener {

  private final ApplyPaymentResultUseCase results;
  private final ObjectMapper json;

  public PaymentResultListener(ApplyPaymentResultUseCase results, ObjectMapper json) {
    this.results = results;
    this.json = json;
  }

  @RabbitListener(queues = RabbitTopologyConfig.RESULT_QUEUE)
  public void consume(Message message) {
    String messageId = message.getMessageProperties().getMessageId();
    if (messageId == null) {
      throw new IllegalArgumentException("messageId is required");
    }
    var body = json.readTree(new String(message.getBody(), StandardCharsets.UTF_8));
    boolean confirmed =
        "PaymentConfirmedV1".equals(message.getMessageProperties().getHeader("eventType"));
    results.apply(
        UUID.fromString(messageId),
        UUID.fromString(body.required("enrollmentId").asString()),
        confirmed);
  }
}
