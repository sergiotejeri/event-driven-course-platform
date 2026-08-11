package com.acme.courseplatform.payment.infrastructure;

import com.acme.courseplatform.messaging.infrastructure.RabbitTopologyConfig;
import com.acme.courseplatform.payment.application.ProcessEnrollmentCreatedUseCase;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

@Component
public class EnrollmentCreatedListener {

  private final ProcessEnrollmentCreatedUseCase enrollments;
  private final ObjectMapper json;

  public EnrollmentCreatedListener(ProcessEnrollmentCreatedUseCase enrollments, ObjectMapper json) {
    this.enrollments = enrollments;
    this.json = json;
  }

  @RabbitListener(queues = RabbitTopologyConfig.PAYMENT_QUEUE)
  public void consume(Message message) {
    String messageId = message.getMessageProperties().getMessageId();
    if (messageId == null) {
      throw new IllegalArgumentException("messageId is required");
    }
    var body = json.readTree(new String(message.getBody(), StandardCharsets.UTF_8));
    enrollments.process(
        UUID.fromString(messageId), UUID.fromString(body.required("enrollmentId").asString()));
  }
}
