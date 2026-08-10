package com.acme.courseplatform.catalog.api;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.acme.courseplatform.CoursePlatformApplication;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@Testcontainers
@SpringBootTest(classes = CoursePlatformApplication.class)
@AutoConfigureMockMvc
class CatalogControllerTest {

  @Container @ServiceConnection
  static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17.6-alpine");

  @Autowired MockMvc mvc;

  @Test
  void managesCompleteCatalogAndSearchesWithCorrelation() throws Exception {
    String category =
        mvc.perform(
                post("/api/v1/categories")
                    .with(admin())
                    .header("X-Correlation-Id", "catalog-test")
                    .contentType("application/json")
                    .content("{\"name\":\"Backend\",\"description\":\"Server engineering\"}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.status").value("ACTIVE"))
            .andReturn()
            .getResponse()
            .getContentAsString();
    String categoryId = value(category, "id");

    String instructor =
        mvc.perform(
                post("/api/v1/instructors")
                    .with(admin())
                    .contentType("application/json")
                    .content(
                        "{\"name\":\"Ada\",\"email\":\"ada@example.test\",\"biography\":\"Senior\"}"))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    String instructorId = value(instructor, "id");

    mvc.perform(get("/api/v1/categories").param("page", "0").param("size", "10"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content[0].id").value(categoryId))
        .andExpect(jsonPath("$.totalElements").value(1));
    mvc.perform(get("/api/v1/instructors").param("page", "0").param("size", "10"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content[0].id").value(instructorId))
        .andExpect(jsonPath("$.totalElements").value(2));

    String course =
        mvc.perform(
                post("/api/v1/courses")
                    .with(admin())
                    .contentType("application/json")
                    .content(
                        ("{\"title\":\"Java avanzado\",\"description\":\"Concurrency\",\"estimatedHours\":12,"
                                + "\"level\":\"ADVANCED\",\"price\":99.90,\"currency\":\"EUR\",\"capacity\":2,"
                                + "\"categoryId\":\"%s\",\"instructorId\":\"%s\"}")
                            .formatted(categoryId, instructorId)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.status").value("DRAFT"))
            .andReturn()
            .getResponse()
            .getContentAsString();
    String courseId = value(course, "id");

    mvc.perform(get("/api/v1/courses/{id}", courseId))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.title").value("Java avanzado"));

    String update =
        ("{\"title\":\"Java concurrente\",\"description\":\"Atomic updates\",\"estimatedHours\":14,"
                + "\"level\":\"ADVANCED\",\"price\":109.90,\"currency\":\"EUR\",\"capacity\":3,"
                + "\"categoryId\":\"%s\",\"instructorId\":\"%s\"}")
            .formatted(categoryId, instructorId);
    mvc.perform(
            put("/api/v1/courses/{id}", courseId)
                .with(admin())
                .contentType("application/json")
                .content(update))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.title").value("Java concurrente"))
        .andExpect(jsonPath("$.capacity").value(3));

    mvc.perform(delete("/api/v1/instructors/{id}", instructorId).with(admin()))
        .andExpect(status().isConflict());
    mvc.perform(post("/api/v1/courses/{id}/publish", courseId).with(admin()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("PUBLISHED"));
    mvc.perform(
            get("/api/v1/courses/search")
                .param("level", "ADVANCED")
                .param("title", "Java concurrente")
                .param("available", "true")
                .param("page", "0")
                .param("size", "10"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content[0].title").value("Java concurrente"))
        .andExpect(jsonPath("$.totalElements").value(1));
    mvc.perform(get("/api/v1/courses/search/cursor").param("size", "1"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content[0].id").value(courseId));
    mvc.perform(post("/api/v1/courses/{id}/archive", courseId).with(admin()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("ARCHIVED"));
    mvc.perform(delete("/api/v1/courses/{id}", courseId).with(admin()))
        .andExpect(status().isNoContent());
    mvc.perform(delete("/api/v1/instructors/{id}", instructorId).with(admin()))
        .andExpect(status().isNoContent());
  }

  @Test
  void updatesReadsAndDeletesCategoriesAndInstructors() throws Exception {
    String category =
        mvc.perform(
                post("/api/v1/categories")
                    .with(admin())
                    .contentType("application/json")
                    .content("{\"name\":\"Data\",\"description\":\"Databases\"}"))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    String categoryId = value(category, "id");

    mvc.perform(
            put("/api/v1/categories/{id}", categoryId)
                .with(admin())
                .contentType("application/json")
                .content("{\"name\":\"Data Engineering\",\"description\":\"Reliable data\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.name").value("Data Engineering"));
    mvc.perform(get("/api/v1/categories/{id}", categoryId))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.description").value("Reliable data"));

    String instructor =
        mvc.perform(
                post("/api/v1/instructors")
                    .with(admin())
                    .contentType("application/json")
                    .content(
                        "{\"name\":\"Grace\",\"email\":\"grace@example.test\",\"biography\":\"Compiler pioneer\"}"))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    String instructorId = value(instructor, "id");

    mvc.perform(
            put("/api/v1/instructors/{id}", instructorId)
                .with(admin())
                .contentType("application/json")
                .content(
                    "{\"name\":\"Grace Hopper\",\"email\":\"grace@example.test\",\"biography\":\"Rear admiral\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.name").value("Grace Hopper"));
    mvc.perform(get("/api/v1/instructors/{id}", instructorId))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.biography").value("Rear admiral"));

    mvc.perform(delete("/api/v1/instructors/{id}", instructorId).with(admin()))
        .andExpect(status().isNoContent());
    mvc.perform(delete("/api/v1/categories/{id}", categoryId).with(admin()))
        .andExpect(status().isNoContent());
  }

  @Test
  void reportsValidationAndMissingResourcesAsProblemDetails() throws Exception {
    mvc.perform(
            post("/api/v1/categories")
                .with(admin())
                .contentType("application/json")
                .content("{\"name\":\"\",\"description\":\"x\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"))
        .andExpect(jsonPath("$.fieldErrors.name").exists());
    mvc.perform(get("/api/v1/courses/00000000-0000-0000-0000-000000000099"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.errorCode").value("RESOURCE_NOT_FOUND"));
    mvc.perform(get("/api/v1/categories").param("page", "0").param("size", "0"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errorCode").value("INVALID_REQUEST"));
  }

  @Test
  void duplicateCategoryNameReturnsFunctionalConflict() throws Exception {
    String name = "Duplicate category " + UUID.randomUUID();
    String body = "{\"name\":\"%s\",\"description\":\"first\"}".formatted(name);
    mvc.perform(
            post("/api/v1/categories").with(admin()).contentType("application/json").content(body))
        .andExpect(status().isCreated());

    mvc.perform(
            post("/api/v1/categories").with(admin()).contentType("application/json").content(body))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.errorCode").value("CATEGORY_NAME_CONFLICT"))
        .andExpect(jsonPath("$.timestamp").isNotEmpty())
        .andExpect(jsonPath("$.correlationId").isNotEmpty());
  }

  @Test
  void duplicateInstructorEmailReturnsFunctionalConflict() throws Exception {
    String email = "duplicate-instructor-" + UUID.randomUUID() + "@example.test";
    String first = "{\"name\":\"Ada\",\"email\":\"%s\",\"biography\":\"first\"}".formatted(email);
    String second =
        "{\"name\":\"Grace\",\"email\":\"%s\",\"biography\":\"second\"}".formatted(email);
    mvc.perform(
            post("/api/v1/instructors")
                .with(admin())
                .contentType("application/json")
                .content(first))
        .andExpect(status().isCreated());

    mvc.perform(
            post("/api/v1/instructors")
                .with(admin())
                .contentType("application/json")
                .content(second))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.errorCode").value("INSTRUCTOR_EMAIL_CONFLICT"))
        .andExpect(jsonPath("$.timestamp").isNotEmpty())
        .andExpect(jsonPath("$.correlationId").isNotEmpty());
  }

  @Test
  void rejectsUnsupportedCatalogSort() throws Exception {
    mvc.perform(get("/api/v1/categories").param("sort", "dropTable,asc"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errorCode").value("INVALID_REQUEST"));
    mvc.perform(get("/api/v1/instructors").param("sort", "email,sideways"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errorCode").value("INVALID_REQUEST"));
    mvc.perform(get("/api/v1/courses/search").param("sort", "unknown,desc"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errorCode").value("INVALID_REQUEST"));
  }

  private static String value(String json, String field) {
    String marker = "\"" + field + "\":\"";
    int start = json.indexOf(marker) + marker.length();
    return json.substring(start, json.indexOf('"', start));
  }

  private static RequestPostProcessor admin() {
    return jwt()
        .jwt(
            token ->
                token
                    .subject("10000000-0000-0000-0000-000000000001")
                    .claim("roles", java.util.List.of("ADMIN")))
        .authorities(new SimpleGrantedAuthority("ROLE_ADMIN"));
  }
}
