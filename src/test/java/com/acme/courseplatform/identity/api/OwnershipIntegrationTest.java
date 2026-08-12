package com.acme.courseplatform.identity.api;

import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.acme.courseplatform.CoursePlatformApplication;
import java.util.UUID;
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
@SpringBootTest(
    classes = CoursePlatformApplication.class,
    properties = "spring.rabbitmq.listener.simple.auto-startup=false")
@AutoConfigureMockMvc
class OwnershipIntegrationTest {

  private static final UUID DEMO_INSTRUCTOR_ID =
      UUID.fromString("20000000-0000-0000-0000-000000000002");

  @Container @ServiceConnection
  static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17.6-alpine");

  @Autowired MockMvc mvc;
  @Autowired JdbcTemplate jdbc;

  @Test
  void instructorCannotCreateCourseForAnotherInstructorButAdminCan() throws Exception {
    String categoryId = createCategory(login("admin@example.test"));
    String foreignInstructorId = createInstructor(login("admin@example.test"));
    String request = courseRequest(categoryId, foreignInstructorId);

    mvc.perform(
            post("/api/v1/courses")
                .header(AUTHORIZATION, bearer(login("instructor@example.test")))
                .contentType(APPLICATION_JSON)
                .content(request))
        .andExpect(status().isForbidden());

    mvc.perform(
            post("/api/v1/courses")
                .header(AUTHORIZATION, bearer(login("admin@example.test")))
                .contentType(APPLICATION_JSON)
                .content(request))
        .andExpect(status().isCreated());
  }

  @Test
  void instructorCanCreateCourseForOwnProfile() throws Exception {
    String admin = login("admin@example.test");
    String categoryId = createCategory(admin);

    mvc.perform(
            post("/api/v1/courses")
                .header(AUTHORIZATION, bearer(login("instructor@example.test")))
                .contentType(APPLICATION_JSON)
                .content(courseRequest(categoryId, DEMO_INSTRUCTOR_ID.toString())))
        .andExpect(status().isCreated());
  }

  @Test
  void instructorCannotPublishCourseOwnedByAnotherInstructor() throws Exception {
    String admin = login("admin@example.test");
    String categoryId = createCategory(admin);
    String foreignInstructorId = createInstructor(admin);
    String courseId = createCourse(admin, categoryId, foreignInstructorId);

    mvc.perform(
            post("/api/v1/courses/{id}/publish", courseId)
                .header(AUTHORIZATION, bearer(login("instructor@example.test"))))
        .andExpect(status().isForbidden());
  }

  @Test
  void relationalListingsEnforceCourseAndStudentOwnership() throws Exception {
    String admin = login("admin@example.test");
    String instructor = login("instructor@example.test");
    String student = login("student@example.test");
    String categoryId = createCategory(admin);
    String ownCourseId = createCourse(admin, categoryId, DEMO_INSTRUCTOR_ID.toString());
    String foreignInstructorId = createInstructor(admin);
    String foreignCourseId = createCourse(admin, categoryId, foreignInstructorId);
    UUID enrollmentId = UUID.randomUUID();
    jdbc.update(
        "insert into enrollments(id,student_id,course_id,status,progress) values (?,'30000000-0000-0000-0000-000000000003',?,'ACTIVE',40)",
        enrollmentId,
        UUID.fromString(ownCourseId));

    mvc.perform(
            get("/api/v1/enrollments/{id}", enrollmentId).header(AUTHORIZATION, bearer(student)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(enrollmentId.toString()))
        .andExpect(jsonPath("$.progress").value(40));

    mvc.perform(
            get("/api/v1/courses/{id}/students", ownCourseId)
                .header(AUTHORIZATION, bearer(instructor))
                .param("page", "0")
                .param("size", "10")
                .param("sort", "progress,desc"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content[0].studentId").value("30000000-0000-0000-0000-000000000003"))
        .andExpect(jsonPath("$.totalElements").value(1));

    mvc.perform(
            get("/api/v1/courses/{id}/students", foreignCourseId)
                .header(AUTHORIZATION, bearer(instructor)))
        .andExpect(status().isForbidden());

    mvc.perform(
            get("/api/v1/students/{id}/courses", "30000000-0000-0000-0000-000000000003")
                .header(AUTHORIZATION, bearer(student))
                .param("sort", "title,asc"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content[0].courseId").value(ownCourseId));

    mvc.perform(
            get("/api/v1/students/{id}/courses", UUID.randomUUID())
                .header(AUTHORIZATION, bearer(student)))
        .andExpect(status().isForbidden());

    mvc.perform(get("/api/v1/students/{id}/courses", "30000000-0000-0000-0000-000000000003"))
        .andExpect(status().isUnauthorized());
  }

  private String createCategory(String token) throws Exception {
    String json =
        mvc.perform(
                post("/api/v1/categories")
                    .header(AUTHORIZATION, bearer(token))
                    .contentType(APPLICATION_JSON)
                    .content(
                        "{\"name\":\"Ownership %s\",\"description\":\"Security\"}"
                            .formatted(UUID.randomUUID())))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return value(json, "id");
  }

  private String createInstructor(String token) throws Exception {
    String json =
        mvc.perform(
                post("/api/v1/instructors")
                    .header(AUTHORIZATION, bearer(token))
                    .contentType(APPLICATION_JSON)
                    .content(
                        "{\"name\":\"Foreign\",\"email\":\"%s@example.test\",\"biography\":\"Security\"}"
                            .formatted(UUID.randomUUID())))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return value(json, "id");
  }

  private String createCourse(String token, String categoryId, String instructorId)
      throws Exception {
    String json =
        mvc.perform(
                post("/api/v1/courses")
                    .header(AUTHORIZATION, bearer(token))
                    .contentType(APPLICATION_JSON)
                    .content(courseRequest(categoryId, instructorId)))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return value(json, "id");
  }

  private String login(String email) throws Exception {
    String json =
        mvc.perform(
                post("/api/v1/auth/login")
                    .contentType(APPLICATION_JSON)
                    .content("{\"email\":\"%s\",\"password\":\"password\"}".formatted(email)))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return value(json, "token");
  }

  private static String courseRequest(String categoryId, String instructorId) {
    return ("{\"title\":\"Ownership\",\"description\":\"Security\",\"estimatedHours\":2,"
            + "\"level\":\"BEGINNER\",\"price\":10.00,\"currency\":\"EUR\",\"capacity\":5,"
            + "\"categoryId\":\"%s\",\"instructorId\":\"%s\"}")
        .formatted(categoryId, instructorId);
  }

  private static String bearer(String token) {
    return "Bearer " + token;
  }

  private static String value(String json, String field) {
    String marker = "\"" + field + "\":\"";
    int start = json.indexOf(marker) + marker.length();
    return json.substring(start, json.indexOf('"', start));
  }
}
