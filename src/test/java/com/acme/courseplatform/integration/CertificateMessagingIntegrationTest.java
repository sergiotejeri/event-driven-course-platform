package com.acme.courseplatform.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.acme.courseplatform.CoursePlatformApplication;
import com.acme.courseplatform.messaging.application.PublishOutboxBatchUseCase;
import com.acme.courseplatform.messaging.infrastructure.RabbitTopologyConfig;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
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
class CertificateMessagingIntegrationTest {

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
  }

  @Autowired JdbcTemplate jdbc;
  @Autowired PublishOutboxBatchUseCase publisher;
  @Autowired RabbitTemplate rabbit;
  @Autowired MockMvc mvc;

  @BeforeEach
  void resetDatabase() {
    jdbc.update("delete from processed_events");
    jdbc.update("delete from outbox_events");
    jdbc.update("delete from certificates");
    jdbc.update("delete from payments");
    jdbc.update("delete from enrollments");
    jdbc.update("delete from idempotency_records");
    jdbc.update("delete from courses");
    jdbc.update("delete from categories");
  }

  @Test
  void completedEnrollmentIssuesOnePubliclyVerifiableCertificate() throws Exception {
    UUID enrollmentId = createCompletedEnrollment();
    enqueueCompletion(enrollmentId);

    assertThat(publisher.publishBatch(10)).isEqualTo(1);
    await(() -> certificateCount(enrollmentId) == 1);
    String verificationCode =
        jdbc.queryForObject(
            "select verification_code from certificates where enrollment_id=?",
            String.class,
            enrollmentId);

    UUID duplicateEventId = UUID.randomUUID();
    rabbit.send(
        RabbitTopologyConfig.EXCHANGE,
        "enrollment.completed.v1",
        completionMessage(duplicateEventId, enrollmentId));
    await(() -> processedEventCount(duplicateEventId) == 1);

    assertThat(certificateCount(enrollmentId)).isEqualTo(1);
    mvc.perform(get("/api/v1/certificates/verify/{verificationCode}", verificationCode))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.enrollmentId").value(enrollmentId.toString()))
        .andExpect(jsonPath("$.verificationCode").value(verificationCode))
        .andExpect(jsonPath("$.issuedAt").isNotEmpty());

    mvc.perform(
            get("/api/v1/certificates/enrollment/{enrollmentId}", enrollmentId)
                .with(
                    jwt()
                        .jwt(
                            token ->
                                token
                                    .subject("10000000-0000-0000-0000-000000000003")
                                    .claim("roles", Set.of("STUDENT")))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.verificationCode").value(verificationCode));

    mvc.perform(
            get("/api/v1/certificates/enrollment/{enrollmentId}", enrollmentId)
                .with(
                    jwt()
                        .jwt(
                            token ->
                                token
                                    .subject(UUID.randomUUID().toString())
                                    .claim("roles", Set.of("STUDENT")))))
        .andExpect(status().isForbidden());
  }

  private UUID createCompletedEnrollment() {
    UUID categoryId = UUID.randomUUID();
    UUID courseId = UUID.randomUUID();
    UUID enrollmentId = UUID.randomUUID();
    jdbc.update(
        "insert into categories(id,name,description,status) values (?,?,?,'ACTIVE')",
        categoryId,
        "Certificates " + categoryId,
        "Certificate flow");
    jdbc.update(
        "insert into courses(id,title,description,estimated_hours,level,price,currency,capacity,occupied_seats,status,category_id,instructor_id) values (?,?,?,1,'ADVANCED',20,'EUR',2,1,'PUBLISHED',?,'20000000-0000-0000-0000-000000000002')",
        courseId,
        "Event-driven certificates",
        "Certificate flow",
        categoryId);
    jdbc.update(
        "insert into enrollments(id,student_id,course_id,status,progress,completed_at) values (?,'30000000-0000-0000-0000-000000000003',?,'COMPLETED',100,now())",
        enrollmentId,
        courseId);
    return enrollmentId;
  }

  private UUID enqueueCompletion(UUID enrollmentId) {
    UUID eventId = UUID.randomUUID();
    UUID courseId =
        jdbc.queryForObject(
            "select course_id from enrollments where id=?", UUID.class, enrollmentId);
    String payload = completionPayload(enrollmentId, courseId);
    jdbc.update(
        "insert into outbox_events(event_id,event_type,event_version,aggregate_type,aggregate_id,payload,correlation_id,occurred_at) values (?,'EnrollmentCompletedV1',1,'Enrollment',?,cast(? as jsonb),?,now())",
        eventId,
        enrollmentId,
        payload,
        UUID.randomUUID());
    return eventId;
  }

  private Message completionMessage(UUID eventId, UUID enrollmentId) {
    UUID courseId =
        jdbc.queryForObject(
            "select course_id from enrollments where id=?", UUID.class, enrollmentId);
    return MessageBuilder.withBody(
            completionPayload(enrollmentId, courseId).getBytes(StandardCharsets.UTF_8))
        .setMessageId(eventId.toString())
        .setContentType("application/json")
        .build();
  }

  private String completionPayload(UUID enrollmentId, UUID courseId) {
    return "{\"enrollmentId\":\""
        + enrollmentId
        + "\",\"courseId\":\""
        + courseId
        + "\",\"completedAt\":\""
        + Instant.now()
        + "\"}";
  }

  private long certificateCount(UUID enrollmentId) {
    return jdbc.queryForObject(
        "select count(*) from certificates where enrollment_id=?", Long.class, enrollmentId);
  }

  private long processedEventCount(UUID eventId) {
    return jdbc.queryForObject(
        "select count(*) from processed_events where consumer_name='certificate-issuer-v1' and event_id=?",
        Long.class,
        eventId);
  }

  private void await(BooleanSupplier condition) throws Exception {
    long deadline = System.nanoTime() + Duration.ofSeconds(8).toNanos();
    while (System.nanoTime() < deadline) {
      if (condition.getAsBoolean()) {
        return;
      }
      Thread.sleep(50);
    }
    assertThat(condition.getAsBoolean()).isTrue();
  }
}
