package com.acme.courseplatform.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.acme.courseplatform.CoursePlatformApplication;
import com.acme.courseplatform.enrollment.application.ApplyPaymentResultUseCase;
import com.acme.courseplatform.enrollment.application.CancelEnrollmentUseCase;
import com.acme.courseplatform.enrollment.application.UpdateProgressUseCase;
import com.acme.courseplatform.identity.application.CurrentActor;
import com.acme.courseplatform.payment.application.ProcessPaymentSimulationUseCase;
import com.acme.courseplatform.payment.application.ProcessPaymentSimulationUseCase.PaymentSimulationCommand;
import com.acme.courseplatform.shared.domain.InvalidTransitionException;
import com.acme.courseplatform.shared.domain.ProgressRegressionException;
import java.math.BigDecimal;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@Testcontainers
@SpringBootTest(classes = CoursePlatformApplication.class)
@AutoConfigureMockMvc
class EnrollmentLifecycleIntegrationTest {

  @Container
  static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17.6-alpine");

  @DynamicPropertySource
  static void database(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
    registry.add("spring.datasource.username", POSTGRES::getUsername);
    registry.add("spring.datasource.password", POSTGRES::getPassword);
  }

  @Autowired JdbcTemplate jdbc;
  @Autowired UpdateProgressUseCase progress;
  @Autowired CancelEnrollmentUseCase cancellation;
  @Autowired ApplyPaymentResultUseCase paymentResult;
  @Autowired ProcessPaymentSimulationUseCase paymentProcessor;
  @Autowired MockMvc mvc;

  UUID courseId;

  @BeforeEach
  void resetDatabase() {
    jdbc.update("delete from processed_events");
    jdbc.update("delete from outbox_events");
    jdbc.update("delete from payments");
    jdbc.update("delete from enrollments");
    jdbc.update("delete from idempotency_records");
    jdbc.update("delete from courses");
    jdbc.update("delete from categories");
    courseId = createCourse();
  }

  @Test
  void concurrentCompletionProducesOneEvent() throws Exception {
    UUID enrollmentId = createEnrollment("ACTIVE", 90);
    CountDownLatch start = new CountDownLatch(1);

    try (var executor = Executors.newFixedThreadPool(2)) {
      var first = executor.submit(() -> updateAfter(start, enrollmentId, 100));
      var second = executor.submit(() -> updateAfter(start, enrollmentId, 100));
      start.countDown();
      first.get();
      second.get();
    }

    assertThat(enrollmentStatus(enrollmentId)).isEqualTo("COMPLETED");
    assertThat(enrollmentProgress(enrollmentId)).isEqualTo(100);
    assertThat(
            count(
                "select count(*) from outbox_events where aggregate_id=? and event_type='EnrollmentCompletedV1'",
                enrollmentId))
        .isEqualTo(1);
  }

  @Test
  void staleProgressCannotOverwriteNewerProgress() {
    UUID enrollmentId = createEnrollment("ACTIVE", 30);
    progress.update(studentActor(), enrollmentId, 70);

    assertThatThrownBy(() -> progress.update(studentActor(), enrollmentId, 50))
        .isInstanceOf(ProgressRegressionException.class);
    assertThat(enrollmentProgress(enrollmentId)).isEqualTo(70);
    assertThat(enrollmentStatus(enrollmentId)).isEqualTo("ACTIVE");
  }

  @Test
  void cancellationAndPaymentConfirmationHaveOneWinner() throws Exception {
    UUID enrollmentId = createEnrollment("PENDING_PAYMENT", 0);
    UUID paymentId = createPendingPayment(enrollmentId);
    CountDownLatch start = new CountDownLatch(1);

    try (var executor = Executors.newFixedThreadPool(2)) {
      var cancel = executor.submit(() -> cancelAfter(start, enrollmentId));
      var confirm =
          executor.submit(
              () -> {
                start.await();
                paymentProcessor.process(
                    UUID.randomUUID(),
                    new PaymentSimulationCommand(paymentId, enrollmentId, "CONFIRM"));
                return null;
              });
      start.countDown();
      cancel.get();
      confirm.get();
    }

    String paymentStatus = paymentStatus(paymentId);
    if (paymentStatus.equals("CONFIRMED")) {
      paymentResult.apply(UUID.randomUUID(), enrollmentId, true);
    }

    assertThat(paymentStatus).isIn("CONFIRMED", "FAILED");
    assertThat(enrollmentStatus(enrollmentId))
        .isEqualTo(paymentStatus.equals("CONFIRMED") ? "ACTIVE" : "CANCELLED");
    assertThat(occupiedSeats()).isEqualTo(paymentStatus.equals("CONFIRMED") ? 1 : 0);
  }

  @Test
  void ownerCanUpdateProgressThroughHttp() throws Exception {
    UUID enrollmentId = createEnrollment("ACTIVE", 20);

    mvc.perform(
            patch("/api/v1/enrollments/{id}/progress", enrollmentId)
                .with(studentJwt(studentActor().userId()))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"progress\":60}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.progress").value(60))
        .andExpect(jsonPath("$.status").value("ACTIVE"))
        .andExpect(jsonPath("$.completedNow").value(false));

    assertThat(enrollmentProgress(enrollmentId)).isEqualTo(60);
  }

  @Test
  void anotherStudentCannotChangeProgress() throws Exception {
    UUID enrollmentId = createEnrollment("ACTIVE", 20);

    mvc.perform(
            patch("/api/v1/enrollments/{id}/progress", enrollmentId)
                .with(studentJwt(UUID.randomUUID()))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"progress\":60}"))
        .andExpect(status().isForbidden());

    assertThat(enrollmentProgress(enrollmentId)).isEqualTo(20);
  }

  @Test
  void ownerCanCancelPendingEnrollmentThroughHttp() throws Exception {
    UUID enrollmentId = createEnrollment("PENDING_PAYMENT", 0);
    UUID paymentId = createPendingPayment(enrollmentId);

    mvc.perform(
            delete("/api/v1/enrollments/{id}", enrollmentId)
                .with(studentJwt(studentActor().userId())))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("CANCELLED"))
        .andExpect(jsonPath("$.replayed").value(false));

    assertThat(enrollmentStatus(enrollmentId)).isEqualTo("CANCELLED");
    assertThat(paymentStatus(paymentId)).isEqualTo("FAILED");
    assertThat(occupiedSeats()).isZero();
  }

  @Test
  void ownerCanCancelActiveEnrollmentWithoutChangingConfirmedPayment() throws Exception {
    UUID enrollmentId = createEnrollment("ACTIVE", 25);
    UUID paymentId = createConfirmedPayment(enrollmentId);

    mvc.perform(
            delete("/api/v1/enrollments/{id}", enrollmentId)
                .with(studentJwt(studentActor().userId())))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("CANCELLED"))
        .andExpect(jsonPath("$.replayed").value(false));

    mvc.perform(
            delete("/api/v1/enrollments/{id}", enrollmentId)
                .with(studentJwt(studentActor().userId())))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.replayed").value(true));

    assertThat(enrollmentStatus(enrollmentId)).isEqualTo("CANCELLED");
    assertThat(paymentStatus(paymentId)).isEqualTo("CONFIRMED");
    assertThat(occupiedSeats()).isZero();
  }

  @Test
  void staleProgressReturnsConflictProblemDetail() throws Exception {
    UUID enrollmentId = createEnrollment("ACTIVE", 70);

    mvc.perform(
            patch("/api/v1/enrollments/{id}/progress", enrollmentId)
                .with(studentJwt(studentActor().userId()))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"progress\":50}"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.errorCode").value("PROGRESS_REGRESSION"));
  }

  private Void updateAfter(CountDownLatch start, UUID enrollmentId, int value) throws Exception {
    start.await();
    progress.update(studentActor(), enrollmentId, value);
    return null;
  }

  private Void cancelAfter(CountDownLatch start, UUID enrollmentId) throws Exception {
    start.await();
    try {
      cancellation.cancel(studentActor(), enrollmentId);
    } catch (InvalidTransitionException expected) {
      return null;
    }
    return null;
  }

  private UUID createCourse() {
    UUID categoryId = UUID.randomUUID();
    UUID id = UUID.randomUUID();
    jdbc.update(
        "insert into categories(id,name,description,status) values (?,?,?,'ACTIVE')",
        categoryId,
        "Lifecycle " + categoryId,
        "Enrollment lifecycle");
    jdbc.update(
        "insert into courses(id,title,description,estimated_hours,level,price,currency,capacity,occupied_seats,status,category_id,instructor_id) values (?,?,?,1,'ADVANCED',?,'EUR',1,1,'PUBLISHED',?,'20000000-0000-0000-0000-000000000002')",
        id,
        "Lifecycle course",
        "Enrollment lifecycle",
        new BigDecimal("40.00"),
        categoryId);
    return id;
  }

  private UUID createEnrollment(String status, int currentProgress) {
    UUID enrollmentId = UUID.randomUUID();
    jdbc.update(
        "insert into enrollments(id,student_id,course_id,status,progress) values (?,'30000000-0000-0000-0000-000000000003',?,?,?)",
        enrollmentId,
        courseId,
        status,
        currentProgress);
    return enrollmentId;
  }

  private UUID createPendingPayment(UUID enrollmentId) {
    UUID paymentId = UUID.randomUUID();
    jdbc.update(
        "insert into payments(id,enrollment_id,amount,currency,status,idempotency_key) values (?,?,40.00,'EUR','PENDING',?)",
        paymentId,
        enrollmentId,
        "lifecycle-" + paymentId);
    return paymentId;
  }

  private UUID createConfirmedPayment(UUID enrollmentId) {
    UUID paymentId = UUID.randomUUID();
    jdbc.update(
        "insert into payments(id,enrollment_id,amount,currency,status,idempotency_key,confirmed_at) values (?,?,40.00,'EUR','CONFIRMED',?,now())",
        paymentId,
        enrollmentId,
        "lifecycle-" + paymentId);
    return paymentId;
  }

  private CurrentActor studentActor() {
    return new CurrentActor(
        UUID.fromString("10000000-0000-0000-0000-000000000003"), Set.of("STUDENT"));
  }

  private RequestPostProcessor studentJwt(UUID userId) {
    return jwt().jwt(token -> token.subject(userId.toString()).claim("roles", Set.of("STUDENT")));
  }

  private String enrollmentStatus(UUID enrollmentId) {
    return jdbc.queryForObject(
        "select status from enrollments where id=?", String.class, enrollmentId);
  }

  private int enrollmentProgress(UUID enrollmentId) {
    return jdbc.queryForObject(
        "select progress from enrollments where id=?", Integer.class, enrollmentId);
  }

  private String paymentStatus(UUID paymentId) {
    return jdbc.queryForObject("select status from payments where id=?", String.class, paymentId);
  }

  private int occupiedSeats() {
    return jdbc.queryForObject(
        "select occupied_seats from courses where id=?", Integer.class, courseId);
  }

  private long count(String sql, Object... parameters) {
    return jdbc.queryForObject(sql, Long.class, parameters);
  }
}
