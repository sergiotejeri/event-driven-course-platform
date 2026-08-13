package com.acme.courseplatform.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.acme.courseplatform.CoursePlatformApplication;
import com.acme.courseplatform.messaging.application.PublishOutboxBatchUseCase;
import com.acme.courseplatform.messaging.application.port.OutboxStore;
import com.acme.courseplatform.messaging.infrastructure.RabbitTopologyConfig;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.rabbitmq.RabbitMQContainer;

@Testcontainers
@SpringBootTest(classes = CoursePlatformApplication.class)
@AutoConfigureMockMvc
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
  @Autowired MockMvc mvc;

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

  @Test
  void httpCorrelationIdReachesOutboxAndRabbitMessage() throws Exception {
    UUID correlationId = UUID.randomUUID();
    UUID categoryId = UUID.randomUUID();
    UUID courseId = UUID.randomUUID();
    jdbc.update(
        "insert into categories(id,name,description,status) values (?,?,?,'ACTIVE')",
        categoryId,
        "Correlation " + categoryId,
        "Correlation test");
    jdbc.update(
        "insert into courses(id,title,description,estimated_hours,level,price,currency,capacity,occupied_seats,status,category_id,instructor_id) values (?,?,?,1,'ADVANCED',?,'EUR',1,0,'PUBLISHED',?,'20000000-0000-0000-0000-000000000002')",
        courseId,
        "Correlation course " + courseId,
        "Correlation test",
        new BigDecimal("49.90"),
        categoryId);

    String response =
        mvc.perform(
                post("/api/v1/courses/{courseId}/enrollments", courseId)
                    .with(
                        jwt()
                            .jwt(
                                token ->
                                    token
                                        .subject("10000000-0000-0000-0000-000000000003")
                                        .claim("roles", List.of("STUDENT")))
                            .authorities(new SimpleGrantedAuthority("ROLE_STUDENT")))
                    .header("Idempotency-Key", "correlation-" + correlationId)
                    .header("X-Correlation-Id", correlationId.toString()))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    UUID enrollmentId = UUID.fromString(value(response, "enrollmentId"));

    assertThat(
            jdbc.queryForObject(
                "select correlation_id from outbox_events where aggregate_id = ?",
                UUID.class,
                enrollmentId))
        .isEqualTo(correlationId);

    assertThat(publisher.publishBatch(10)).isEqualTo(1);
    Message message = rabbit.receive(RabbitTopologyConfig.PAYMENT_QUEUE, 5000);

    assertThat(message).isNotNull();
    assertThat(message.getMessageProperties().getCorrelationId())
        .isEqualTo(correlationId.toString());
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

  private static String value(String json, String field) {
    String marker = "\"" + field + "\":\"";
    int start = json.indexOf(marker) + marker.length();
    return json.substring(start, json.indexOf('"', start));
  }
}
