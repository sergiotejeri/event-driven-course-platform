package com.acme.courseplatform.certificate.infrastructure;

import com.acme.courseplatform.certificate.application.IssueCertificateUseCase;
import com.acme.courseplatform.messaging.infrastructure.RabbitTopologyConfig;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

@Component
public class EnrollmentCompletedListener {

  private final IssueCertificateUseCase certificates;
  private final ObjectMapper json;

  public EnrollmentCompletedListener(IssueCertificateUseCase certificates, ObjectMapper json) {
    this.certificates = certificates;
    this.json = json;
  }

  @RabbitListener(queues = RabbitTopologyConfig.CERTIFICATE_QUEUE)
  public void consume(Message message) {
    String messageId = message.getMessageProperties().getMessageId();
    if (messageId == null) {
      throw new IllegalArgumentException("messageId is required");
    }
    var body = json.readTree(new String(message.getBody(), StandardCharsets.UTF_8));
    certificates.issue(
        UUID.fromString(messageId), UUID.fromString(body.required("enrollmentId").asString()));
  }
}
