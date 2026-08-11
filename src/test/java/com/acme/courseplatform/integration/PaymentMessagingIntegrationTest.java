package com.acme.courseplatform.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.acme.courseplatform.CoursePlatformApplication;
import com.acme.courseplatform.enrollment.application.EnrollStudentUseCase;
import com.acme.courseplatform.identity.application.CurrentActor;
import com.acme.courseplatform.messaging.application.PublishOutboxBatchUseCase;
import com.acme.courseplatform.messaging.infrastructure.RabbitTopologyConfig;
import com.acme.courseplatform.payment.application.ProcessPaymentSimulationUseCase;
import com.acme.courseplatform.payment.application.ProcessPaymentSimulationUseCase.PaymentSimulationCommand;
import com.acme.courseplatform.payment.application.RequestPaymentSimulationUseCase;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.function.BooleanSupplier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
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
class PaymentMessagingIntegrationTest {

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
  @Autowired EnrollStudentUseCase enrollment;
  @Autowired PublishOutboxBatchUseCase publisher;
  @Autowired RequestPaymentSimulationUseCase simulation;
  @Autowired ProcessPaymentSimulationUseCase paymentProcessor;
  @Autowired RabbitTemplate rabbit;
  @Autowired MockMvc mvc;

  @BeforeEach
  void resetDatabase() {
    jdbc.update("delete from processed_events");
    jdbc.update("delete from outbox_events");
    jdbc.update("delete from payments");
    jdbc.update("delete from enrollments");
    jdbc.update("delete from idempotency_records");
    jdbc.update("delete from courses");
    jdbc.update("delete from categories");
    while (rabbit.receive(RabbitTopologyConfig.SIMULATION_DLQ, 50) != null) {}
  }

  @Test
  void enrollmentCreatedConfirmsPaymentAndActivatesEnrollment() throws Exception {
    UUID courseId = createPublishedCourse();
    var enrolled = enrollment.enroll(studentActor(), courseId, "automatic-payment-" + courseId);

    assertThat(publisher.publishBatch(10)).isEqualTo(1);
    await(() -> "CONFIRMED".equals(recordStatus("payments", enrolled.paymentId())));
    assertThat(publisher.publishBatch(10)).isEqualTo(1);
    await(() -> "ACTIVE".equals(recordStatus("enrollments", enrolled.enrollmentId())));
  }

  @Test
  void failedPaymentCancelsEnrollmentAndReleasesSeat() throws Exception {
    UUID courseId = createPublishedCourse();
    var enrolled = enrollment.enroll(studentActor(), courseId, "failed-payment-" + courseId);
    jdbc.update("delete from outbox_events");

    paymentProcessor.process(
        UUID.randomUUID(),
        new PaymentSimulationCommand(enrolled.paymentId(), enrolled.enrollmentId(), "FAIL"));
    assertThat(publisher.publishBatch(10)).isEqualTo(1);
    await(() -> "CANCELLED".equals(recordStatus("enrollments", enrolled.enrollmentId())));

    assertThat(recordStatus("payments", enrolled.paymentId())).isEqualTo("FAILED");
    assertThat(
            jdbc.queryForObject(
                "select occupied_seats from courses where id=?", Integer.class, courseId))
        .isZero();
  }

  @Test
  void duplicatedSimulationEventIsIgnored() throws Exception {
    UUID courseId = createPublishedCourse();
    var enrolled = enrollment.enroll(studentActor(), courseId, "duplicate-payment-" + courseId);
    jdbc.update("delete from outbox_events");
    UUID eventId = simulation.request(studentActor(), enrolled.paymentId(), "CONFIRM");

    assertThat(publisher.publishBatch(10)).isEqualTo(1);
    await(() -> "CONFIRMED".equals(recordStatus("payments", enrolled.paymentId())));
    String payload =
        "{\"paymentId\":\""
            + enrolled.paymentId()
            + "\",\"enrollmentId\":\""
            + enrolled.enrollmentId()
            + "\",\"outcome\":\"CONFIRM\"}";
    Message duplicate =
        MessageBuilder.withBody(payload.getBytes(StandardCharsets.UTF_8))
            .setMessageId(eventId.toString())
            .setContentType("application/json")
            .build();
    rabbit.send(RabbitTopologyConfig.EXCHANGE, "payment.simulation-requested.v1", duplicate);
    Thread.sleep(300);

    assertThat(
            jdbc.queryForObject(
                "select count(*) from processed_events where consumer_name='payment-simulation-v1' and event_id=?",
                Long.class,
                eventId))
        .isEqualTo(1);
    assertThat(
            jdbc.queryForObject(
                "select count(*) from outbox_events where aggregate_id=? and event_type in ('PaymentConfirmedV1','PaymentFailedV1')",
                Long.class,
                enrolled.paymentId()))
        .isEqualTo(1);
  }

  @Test
  void malformedSimulationMessageIsDeadLettered() {
    Message poison =
        MessageBuilder.withBody("not-json".getBytes(StandardCharsets.UTF_8))
            .setMessageId(UUID.randomUUID().toString())
            .build();

    rabbit.send(RabbitTopologyConfig.EXCHANGE, "payment.simulation-requested.v1", poison);
    Message dead = rabbit.receive(RabbitTopologyConfig.SIMULATION_DLQ, 8000);

    assertThat(dead).isNotNull();
    assertThat(new String(dead.getBody(), StandardCharsets.UTF_8)).isEqualTo("not-json");
  }

  @Test
  void competingPaymentResultsProduceOneTerminalEvent() throws Exception {
    UUID courseId = createPublishedCourse();
    var enrolled = enrollment.enroll(studentActor(), courseId, "payment-race-" + courseId);
    jdbc.update("delete from outbox_events");
    CountDownLatch start = new CountDownLatch(1);

    try (var executor = Executors.newFixedThreadPool(2)) {
      var confirm =
          executor.submit(
              () -> {
                start.await();
                paymentProcessor.process(
                    UUID.randomUUID(),
                    new PaymentSimulationCommand(
                        enrolled.paymentId(), enrolled.enrollmentId(), "CONFIRM"));
                return null;
              });
      var fail =
          executor.submit(
              () -> {
                start.await();
                paymentProcessor.process(
                    UUID.randomUUID(),
                    new PaymentSimulationCommand(
                        enrolled.paymentId(), enrolled.enrollmentId(), "FAIL"));
                return null;
              });
      start.countDown();
      confirm.get();
      fail.get();
    }

    String paymentStatus = recordStatus("payments", enrolled.paymentId());
    var events =
        jdbc.queryForList(
            "select event_type from outbox_events where aggregate_id=? and event_type in ('PaymentConfirmedV1','PaymentFailedV1')",
            String.class,
            enrolled.paymentId());

    assertThat(events).hasSize(1);
    assertThat(events.getFirst())
        .isEqualTo(paymentStatus.equals("CONFIRMED") ? "PaymentConfirmedV1" : "PaymentFailedV1");
  }

  @Test
  void paymentOwnerCanRequestSimulationThroughHttp() throws Exception {
    UUID courseId = createPublishedCourse();
    var enrolled = enrollment.enroll(studentActor(), courseId, "owner-payment-" + courseId);
    jdbc.update("delete from outbox_events");

    mvc.perform(
            post("/api/v1/payments/{id}/simulate", enrolled.paymentId())
                .with(
                    jwt()
                        .jwt(
                            token ->
                                token
                                    .subject(studentActor().userId().toString())
                                    .claim("roles", Set.of("STUDENT"))))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"outcome\":\"CONFIRM\"}"))
        .andExpect(status().isAccepted())
        .andExpect(jsonPath("$.eventId").isNotEmpty())
        .andExpect(jsonPath("$.status").value("ACCEPTED"));
  }

  @Test
  void anotherStudentCannotRequestPaymentSimulation() throws Exception {
    UUID courseId = createPublishedCourse();
    var enrolled = enrollment.enroll(studentActor(), courseId, "foreign-payment-" + courseId);

    mvc.perform(
            post("/api/v1/payments/{id}/simulate", enrolled.paymentId())
                .with(
                    jwt()
                        .jwt(
                            token ->
                                token
                                    .subject(UUID.randomUUID().toString())
                                    .claim("roles", Set.of("STUDENT"))))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"outcome\":\"CONFIRM\"}"))
        .andExpect(status().isForbidden());
  }

  @Test
  void administratorCanRequestPaymentSimulation() throws Exception {
    UUID courseId = createPublishedCourse();
    var enrolled = enrollment.enroll(studentActor(), courseId, "admin-payment-" + courseId);
    jdbc.update("delete from outbox_events");

    mvc.perform(
            post("/api/v1/payments/{id}/simulate", enrolled.paymentId())
                .with(
                    jwt()
                        .jwt(
                            token ->
                                token
                                    .subject(UUID.randomUUID().toString())
                                    .claim("roles", Set.of("ADMIN"))))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"outcome\":\"FAIL\"}"))
        .andExpect(status().isAccepted());
  }

  private UUID createPublishedCourse() {
    UUID categoryId = UUID.randomUUID();
    UUID courseId = UUID.randomUUID();
    jdbc.update(
        "insert into categories(id,name,description,status) values (?,?,?,'ACTIVE')",
        categoryId,
        "Messaging " + categoryId,
        "Payment flow");
    jdbc.update(
        "insert into courses(id,title,description,estimated_hours,level,price,currency,capacity,occupied_seats,status,category_id,instructor_id) values (?,?,?,1,'ADVANCED',?,'EUR',2,0,'PUBLISHED',?,'20000000-0000-0000-0000-000000000002')",
        courseId,
        "Event-driven payment",
        "Payment flow",
        new BigDecimal("20.00"),
        categoryId);
    return courseId;
  }

  private CurrentActor studentActor() {
    return new CurrentActor(
        UUID.fromString("10000000-0000-0000-0000-000000000003"), Set.of("STUDENT"));
  }

  private String recordStatus(String table, UUID id) {
    return jdbc.queryForObject("select status from " + table + " where id=?", String.class, id);
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
