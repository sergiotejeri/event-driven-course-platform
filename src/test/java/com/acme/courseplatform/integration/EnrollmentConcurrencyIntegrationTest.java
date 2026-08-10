package com.acme.courseplatform.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.acme.courseplatform.CoursePlatformApplication;
import com.acme.courseplatform.enrollment.application.EnrollStudentUseCase;
import com.acme.courseplatform.identity.application.CurrentActor;
import com.acme.courseplatform.shared.api.ConflictException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
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
class EnrollmentConcurrencyIntegrationTest {

  @Container @ServiceConnection
  static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17.6-alpine");

  @Autowired JdbcTemplate jdbc;
  @Autowired EnrollStudentUseCase enroll;

  UUID courseId;

  @BeforeEach
  void createCourseWithOneSeat() {
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
        "Concurrency " + categoryId,
        "Atomic enrollment");
    jdbc.update(
        "insert into courses(id,title,description,estimated_hours,level,price,currency,capacity,occupied_seats,status,category_id,instructor_id) values (?,?,?,1,'ADVANCED',?,'EUR',1,0,'PUBLISHED',?,'20000000-0000-0000-0000-000000000002')",
        courseId,
        "Atomic seats",
        "Concurrency",
        new BigDecimal("49.90"),
        categoryId);
  }

  @Test
  void tenStudentsCompetingForOneSeatProduceExactlyOneEnrollment() throws Exception {
    List<CurrentActor> actors = createStudents(10);
    ExecutorService pool = Executors.newFixedThreadPool(10);
    CyclicBarrier gate = new CyclicBarrier(10);
    AtomicInteger winners = new AtomicInteger();
    AtomicInteger rejected = new AtomicInteger();
    List<Future<?>> jobs = new ArrayList<>();

    try {
      for (int index = 0; index < actors.size(); index++) {
        int actorIndex = index;
        jobs.add(
            pool.submit(
                () -> {
                  gate.await();
                  try {
                    enroll.enroll(actors.get(actorIndex), courseId, "race-" + actorIndex);
                    winners.incrementAndGet();
                  } catch (ConflictException expected) {
                    rejected.incrementAndGet();
                  }
                  return null;
                }));
      }
      for (Future<?> job : jobs) {
        job.get(20, TimeUnit.SECONDS);
      }
    } finally {
      pool.shutdownNow();
    }

    assertThat(winners).hasValue(1);
    assertThat(rejected).hasValue(9);
    assertThat(integer("select occupied_seats from courses where id = ?", courseId)).isEqualTo(1);
    assertThat(count("select count(*) from enrollments where course_id = ?", courseId))
        .isEqualTo(1);
    assertThat(count("select count(*) from payments")).isEqualTo(1);
  }

  @Test
  void replayingSameKeyReturnsSameEnrollmentWithoutSecondSeat() {
    CurrentActor actor = studentActor();

    var first = enroll.enroll(actor, courseId, "same-key");
    var replay = enroll.enroll(actor, courseId, "same-key");

    assertThat(replay.enrollmentId()).isEqualTo(first.enrollmentId());
    assertThat(replay.paymentId()).isEqualTo(first.paymentId());
    assertThat(first.replayed()).isFalse();
    assertThat(replay.replayed()).isTrue();
    assertThat(integer("select occupied_seats from courses where id = ?", courseId)).isEqualTo(1);
    assertThat(count("select count(*) from enrollments where course_id = ?", courseId))
        .isEqualTo(1);
    assertThat(count("select count(*) from payments")).isEqualTo(1);
  }

  @Test
  void differentActorsMayReuseTheSameIdempotencyKey() {
    jdbc.update("update courses set capacity = 2 where id = ?", courseId);
    CurrentActor firstActor = studentActor();
    CurrentActor secondActor = createStudents(1).getFirst();

    var first = enroll.enroll(firstActor, courseId, "shared-key");
    var second = enroll.enroll(secondActor, courseId, "shared-key");

    assertThat(second.enrollmentId()).isNotEqualTo(first.enrollmentId());
    assertThat(integer("select occupied_seats from courses where id = ?", courseId)).isEqualTo(2);
    assertThat(count("select count(*) from payments where idempotency_key = ?", "shared-key"))
        .isEqualTo(2);
  }

  private List<CurrentActor> createStudents(int amount) {
    List<CurrentActor> actors = new ArrayList<>();
    for (int index = 0; index < amount; index++) {
      UUID userId = UUID.randomUUID();
      UUID studentId = UUID.randomUUID();
      String email = "race-" + userId + "@example.test";
      jdbc.update(
          "insert into users(id,email,password_hash,enabled) values (?,?,?,true)",
          userId,
          email,
          "not-used");
      jdbc.update(
          "insert into students(id,user_id,first_name,last_name,email) values (?,?,?,?,?)",
          studentId,
          userId,
          "Race",
          "Student",
          email);
      actors.add(new CurrentActor(userId, Set.of("STUDENT")));
    }
    return actors;
  }

  private CurrentActor studentActor() {
    return new CurrentActor(
        UUID.fromString("10000000-0000-0000-0000-000000000003"), Set.of("STUDENT"));
  }

  private long count(String sql, Object... parameters) {
    return jdbc.queryForObject(sql, Long.class, parameters);
  }

  private int integer(String sql, Object... parameters) {
    return jdbc.queryForObject(sql, Integer.class, parameters);
  }
}
