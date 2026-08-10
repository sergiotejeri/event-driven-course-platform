package com.acme.courseplatform.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.acme.courseplatform.CoursePlatformApplication;
import com.acme.courseplatform.messaging.application.PublishOutboxBatchUseCase;
import com.acme.courseplatform.messaging.application.port.OutboxStore;
import com.acme.courseplatform.messaging.infrastructure.RabbitTopologyConfig;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.rabbitmq.RabbitMQContainer;

@Testcontainers
@SpringBootTest(classes = CoursePlatformApplication.class)
class OutboxPublisherIntegrationTest {

  @Container
  static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17.6-alpine");

  @Container
  static final RabbitMQContainer RABBIT = new RabbitMQContainer("rabbitmq:4.1-management-alpine");

  @DynamicPropertySource
  static void properties(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
    registry.add("spring.datasource.username", POSTGRES::getUsername);
    registry.add("spring.datasource.password", POSTGRES::getPassword);
    registry.add("spring.rabbitmq.host", RABBIT::getHost);
    registry.add("spring.rabbitmq.port", RABBIT::getAmqpPort);
    registry.add("spring.rabbitmq.username", RABBIT::getAdminUsername);
    registry.add("spring.rabbitmq.password", RABBIT::getAdminPassword);
    registry.add("spring.rabbitmq.listener.simple.auto-startup", () -> "false");
  }

  @Autowired JdbcTemplate jdbc;
  @Autowired PublishOutboxBatchUseCase publisher;
  @Autowired OutboxStore outbox;
  @Autowired RabbitTemplate rabbit;
  @Autowired CachingConnectionFactory connectionFactory;

  @BeforeEach
  void clearOutbox() {
    jdbc.update("delete from outbox_events");
    while (rabbit.receive(RabbitTopologyConfig.PAYMENT_QUEUE, 100) != null) {}
  }

  @Test
  void claimedMessageIsLeasedBeforePublisherConfirmation() {
    UUID eventId = insertEnrollmentCreated();

    assertThat(outbox.claimBatch(1))
        .extracting(OutboxStore.OutboxMessage::eventId)
        .containsExactly(eventId);
    assertThat(outbox.claimBatch(1)).isEmpty();
  }

  @Test
  void brokerAcknowledgementMarksPublishedAndRoutesPayload() {
    UUID eventId = insertEnrollmentCreated();

    assertThat(connectionFactory.isPublisherConfirms()).isTrue();
    assertThat(publisher.publishBatch(10)).isEqualTo(1);
    Message message = rabbit.receive(RabbitTopologyConfig.PAYMENT_QUEUE, 5000);

    assertThat(message).isNotNull();
    assertThat(message.getMessageProperties().getMessageId()).isEqualTo(eventId.toString());
    assertThat(new String(message.getBody())).contains("enrollmentId");
    assertThat(
            jdbc.queryForObject(
                "select published_at is not null from outbox_events where event_id=?",
                Boolean.class,
                eventId))
        .isTrue();
    assertThat(
            jdbc.queryForObject(
                "select attempts from outbox_events where event_id=?", Integer.class, eventId))
        .isEqualTo(1);
  }

  private UUID insertEnrollmentCreated() {
    UUID eventId = UUID.randomUUID();
    UUID aggregateId = UUID.randomUUID();
    jdbc.update(
        "insert into outbox_events(event_id,event_type,event_version,aggregate_type,aggregate_id,payload,correlation_id,occurred_at) values (?,'EnrollmentCreatedV1',1,'Enrollment',?,cast(? as jsonb),?,?)",
        eventId,
        aggregateId,
        "{\"enrollmentId\":\"" + aggregateId + "\"}",
        UUID.randomUUID(),
        Timestamp.from(Instant.now()));
    return eventId;
  }
}
