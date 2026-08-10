package com.acme.courseplatform.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.acme.courseplatform.CoursePlatformApplication;
import com.acme.courseplatform.enrollment.application.EnrollStudentUseCase;
import com.acme.courseplatform.identity.application.CurrentActor;
import com.acme.courseplatform.messaging.application.ProcessedEventService;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@Testcontainers
@SpringBootTest(classes = CoursePlatformApplication.class)
class OutboxTransactionalIntegrationTest {

  @Container @ServiceConnection
  static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17.6-alpine");

  @Autowired JdbcTemplate jdbc;
  @Autowired EnrollStudentUseCase enroll;
  @Autowired ProcessedEventService processedEvents;

  UUID courseId;

  @BeforeEach
  void preparePublishedCourse() {
    jdbc.update("delete from outbox_events");
    jdbc.update("delete from idempotency_records");
    jdbc.update("delete from payments");
    jdbc.update("delete from enrollments");
    jdbc.update("delete from courses");
    jdbc.update("delete from categories");

    UUID categoryId = UUID.randomUUID();
    courseId = UUID.randomUUID();
    jdbc.update(
        "insert into categories(id,name,description,status) values (?,?,?,'ACTIVE')",
        categoryId,
        "Outbox " + categoryId,
        "Transactional outbox");
    jdbc.update(
        "insert into courses(id,title,description,estimated_hours,level,price,currency,capacity,occupied_seats,status,category_id,instructor_id) values (?,?,?,1,'ADVANCED',?,'EUR',1,0,'PUBLISHED',?,'20000000-0000-0000-0000-000000000002')",
        courseId,
        "Reliable messaging",
        "Outbox course",
        new BigDecimal("49.90"),
        categoryId);
  }

  @Test
  void enrollmentAndCreatedEventAreStoredTogether() {
    var result = enroll.enroll(studentActor(), courseId, "outbox-success");

    List<Map<String, Object>> events =
        jdbc.queryForList(
            "select event_type,aggregate_id,payload from outbox_events where aggregate_id = ?",
            result.enrollmentId());
    assertThat(events).singleElement();
    Map<String, Object> event = events.getFirst();

    assertThat(event.get("event_type")).isEqualTo("EnrollmentCreatedV1");
    assertThat(event.get("aggregate_id")).isEqualTo(result.enrollmentId());
    assertThat(event.get("payload").toString())
        .contains("\"enrollmentId\": \"" + result.enrollmentId() + "\"")
        .contains("\"courseId\": \"" + courseId + "\"");
  }

  @Test
  void outboxFailureRollsBackTheWholeEnrollment() {
    jdbc.execute(
        "alter table outbox_events add constraint reject_outbox_insert check (event_type <> 'EnrollmentCreatedV1')");
    try {
      assertThatThrownBy(() -> enroll.enroll(studentActor(), courseId, "outbox-failure"))
          .isInstanceOf(RuntimeException.class);
    } finally {
      jdbc.execute("alter table outbox_events drop constraint reject_outbox_insert");
    }

    assertThat(count("select count(*) from outbox_events")).isZero();
    assertThat(count("select count(*) from enrollments")).isZero();
    assertThat(count("select count(*) from payments")).isZero();
    assertThat(count("select count(*) from idempotency_records")).isZero();
    assertThat(count("select occupied_seats from courses where id = ?", courseId)).isZero();
  }

  @Test
  void processedEventIsClaimedOncePerConsumer() {
    UUID eventId = UUID.randomUUID();

    assertThat(processedEvents.claim("payment-simulation-v1", eventId)).isTrue();
    assertThat(processedEvents.claim("payment-simulation-v1", eventId)).isFalse();
    assertThat(processedEvents.claim("certificate-issuer-v1", eventId)).isTrue();
  }

  private CurrentActor studentActor() {
    return new CurrentActor(
        UUID.fromString("10000000-0000-0000-0000-000000000003"), Set.of("STUDENT"));
  }

  private long count(String sql, Object... parameters) {
    return jdbc.queryForObject(sql, Long.class, parameters);
  }
}
