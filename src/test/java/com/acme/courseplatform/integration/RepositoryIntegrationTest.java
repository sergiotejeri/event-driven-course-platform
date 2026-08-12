package com.acme.courseplatform.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.acme.courseplatform.CoursePlatformApplication;
import com.acme.courseplatform.enrollment.application.port.EnrollmentQueryStore;
import com.acme.courseplatform.shared.query.SortDirection;
import com.acme.courseplatform.shared.query.SortSpec;
import java.util.Map;
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
@SpringBootTest(
    classes = CoursePlatformApplication.class,
    properties = "spring.rabbitmq.listener.simple.auto-startup=false")
class RepositoryIntegrationTest {

  @Container @ServiceConnection
  static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17.6-alpine");

  @Autowired JdbcTemplate jdbc;
  @Autowired EnrollmentQueryStore queries;

  private UUID courseId;
  private UUID studentId;

  @BeforeEach
  void setUp() {
    jdbc.update("delete from certificates");
    jdbc.update("delete from payments");
    jdbc.update("delete from enrollments");
    jdbc.update("delete from courses");
    jdbc.update("delete from categories");
    jdbc.update("delete from students where id <> '30000000-0000-0000-0000-000000000003'");
    jdbc.update(
        "delete from users where id not in ('10000000-0000-0000-0000-000000000001','10000000-0000-0000-0000-000000000002','10000000-0000-0000-0000-000000000003')");

    UUID categoryId = UUID.randomUUID();
    courseId = UUID.randomUUID();
    studentId = UUID.fromString("30000000-0000-0000-0000-000000000003");
    jdbc.update(
        "insert into categories(id,name,description,status) values (?,?,?,'ACTIVE')",
        categoryId,
        "Relations " + categoryId,
        "Relational queries");
    jdbc.update(
        "insert into courses(id,title,description,estimated_hours,level,price,currency,capacity,occupied_seats,status,category_id,instructor_id) values (?,?,?,1,'BEGINNER',10,'EUR',10,3,'PUBLISHED',?,'20000000-0000-0000-0000-000000000002')",
        courseId,
        "Relational course",
        "Relational queries",
        categoryId);
    addEnrollment(studentId);
    addEnrollment(createStudent("Ana", "Zulu"));
    addEnrollment(createStudent("Ana", "Zulu"));
  }

  @Test
  void relationalListingsArePagedWithStableOrderingAndExactTotals() {
    SortSpec studentsSort =
        SortSpec.parse(
            "firstName,asc",
            Map.of("firstName", "s.first_name"),
            "firstName",
            SortDirection.ASC,
            "s.id");
    var students = queries.findStudentsByCourse(courseId, 0, 2, studentsSort);

    assertThat(students.content()).hasSize(2);
    assertThat(students.totalElements()).isEqualTo(3);
    assertThat(students.content()).extracting(student -> student.firstName()).containsOnly("Ana");
    assertThat(students.content().get(0).studentId().toString())
        .isLessThan(students.content().get(1).studentId().toString());
    assertThat(queries.findStudentsByCourse(courseId, 1, 2, studentsSort).content())
        .extracting(student -> student.firstName())
        .containsExactly("Demo");

    SortSpec coursesSort =
        SortSpec.parse("title,asc", Map.of("title", "c.title"), "title", SortDirection.ASC, "c.id");
    var courses = queries.findCoursesByStudent(studentId, 0, 1, coursesSort);

    assertThat(courses.content()).hasSize(1);
    assertThat(courses.totalElements()).isEqualTo(1);
    assertThat(courses.content().getFirst().courseId()).isEqualTo(courseId);
  }

  private UUID createStudent(String firstName, String lastName) {
    UUID userId = UUID.randomUUID();
    UUID id = UUID.randomUUID();
    String email = id + "@example.test";
    jdbc.update(
        "insert into users(id,email,password_hash,enabled) values (?,?,?,true)",
        userId,
        email,
        "not-used");
    jdbc.update(
        "insert into students(id,user_id,first_name,last_name,email) values (?,?,?,?,?)",
        id,
        userId,
        firstName,
        lastName,
        email);
    return id;
  }

  private void addEnrollment(UUID id) {
    jdbc.update(
        "insert into enrollments(id,student_id,course_id,status,progress) values (?, ?, ?, 'ACTIVE', 0)",
        UUID.randomUUID(),
        id,
        courseId);
  }
}
