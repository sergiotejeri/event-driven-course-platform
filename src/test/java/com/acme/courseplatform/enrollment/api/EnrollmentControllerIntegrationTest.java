package com.acme.courseplatform.enrollment.api;

import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.acme.courseplatform.CoursePlatformApplication;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@Testcontainers
@SpringBootTest(classes = CoursePlatformApplication.class)
@AutoConfigureMockMvc
class EnrollmentControllerIntegrationTest {

  @Container @ServiceConnection
  static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17.6-alpine");

  @Autowired MockMvc mvc;
  @Autowired JdbcTemplate jdbc;

  UUID courseId;

  @BeforeEach
  void createPublishedCourse() {
    UUID categoryId = UUID.randomUUID();
    courseId = UUID.randomUUID();
    jdbc.update(
        "insert into categories(id,name,description,status) values (?,?,?,'ACTIVE')",
        categoryId,
        "HTTP " + categoryId,
        "Enrollment endpoint");
    jdbc.update(
        "insert into courses(id,title,description,estimated_hours,level,price,currency,capacity,occupied_seats,status,category_id,instructor_id) values (?,?,?,1,'BEGINNER',?,'EUR',1,0,'PUBLISHED',?,'20000000-0000-0000-0000-000000000002')",
        courseId,
        "HTTP enrollment",
        "Enrollment endpoint",
        new BigDecimal("19.90"),
        categoryId);
  }

  @Test
  void createsEnrollmentAndReturnsReplayForSameStudentKey() throws Exception {
    String token = loginStudent();

    String first =
        mvc.perform(
                post("/api/v1/courses/{courseId}/enrollments", courseId)
                    .header(AUTHORIZATION, "Bearer " + token)
                    .header("Idempotency-Key", "http-enrollment"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.replayed").value(false))
            .andReturn()
            .getResponse()
            .getContentAsString();
    String enrollmentId = value(first, "enrollmentId");
    String paymentId = value(first, "paymentId");

    mvc.perform(
            post("/api/v1/courses/{courseId}/enrollments", courseId)
                .header(AUTHORIZATION, "Bearer " + token)
                .header("Idempotency-Key", "http-enrollment"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.enrollmentId").value(enrollmentId))
        .andExpect(jsonPath("$.paymentId").value(paymentId))
        .andExpect(jsonPath("$.replayed").value(true));
  }

  private String loginStudent() throws Exception {
    String json =
        mvc.perform(
                post("/api/v1/auth/login")
                    .contentType("application/json")
                    .content("{\"email\":\"student@example.test\",\"password\":\"password\"}"))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return value(json, "token");
  }

  private static String value(String json, String field) {
    String marker = "\"" + field + "\":\"";
    int start = json.indexOf(marker) + marker.length();
    return json.substring(start, json.indexOf('"', start));
  }
}
