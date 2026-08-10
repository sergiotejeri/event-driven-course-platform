package com.acme.courseplatform.identity.api;

import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.acme.courseplatform.CoursePlatformApplication;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@Testcontainers
@SpringBootTest(classes = CoursePlatformApplication.class)
@AutoConfigureMockMvc
class OwnershipIntegrationTest {

  private static final UUID DEMO_INSTRUCTOR_ID =
      UUID.fromString("20000000-0000-0000-0000-000000000002");

  @Container @ServiceConnection
  static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17.6-alpine");

  @Autowired MockMvc mvc;

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
