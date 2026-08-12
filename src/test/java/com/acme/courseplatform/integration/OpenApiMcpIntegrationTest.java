package com.acme.courseplatform.integration;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.acme.courseplatform.CoursePlatformApplication;
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
@SpringBootTest(
    classes = CoursePlatformApplication.class,
    properties = "spring.rabbitmq.listener.simple.auto-startup=false")
@AutoConfigureMockMvc
class OpenApiMcpIntegrationTest {

  @Container @ServiceConnection
  static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17.6-alpine");

  @Autowired MockMvc mvc;

  @Test
  void openApiContainsBearerIdempotencyAndCoreOperations() throws Exception {
    mvc.perform(get("/v3/api-docs"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.openapi").value("3.1.0"))
        .andExpect(jsonPath("$.info.title").value("Event-driven Course Platform API"))
        .andExpect(jsonPath("$.components.securitySchemes.bearerAuth.type").value("http"))
        .andExpect(jsonPath("$.components.securitySchemes.bearerAuth.scheme").value("bearer"))
        .andExpect(jsonPath("$.components.securitySchemes.bearerAuth.bearerFormat").value("JWT"))
        .andExpect(jsonPath("$.paths['/api/v1/courses/{courseId}/enrollments'].post").exists())
        .andExpect(
            jsonPath(
                    "$.paths['/api/v1/courses/{courseId}/enrollments'].post.parameters[?(@.name == 'Idempotency-Key' && @.in == 'header' && @.required == true)]")
                .isNotEmpty())
        .andExpect(
            jsonPath("$.paths['/api/v1/courses/{courseId}/enrollments'].post.responses['401']")
                .exists())
        .andExpect(
            jsonPath("$.paths['/api/v1/courses/{courseId}/enrollments'].post.responses['403']")
                .exists())
        .andExpect(
            jsonPath("$.paths['/api/v1/courses/{courseId}/enrollments'].post.responses['409']")
                .exists())
        .andExpect(jsonPath("$.paths['/api/v1/courses/{courseId}/students'].get").exists())
        .andExpect(
            jsonPath(
                    "$.paths['/api/v1/courses/{courseId}/students'].get.parameters[?(@.name == 'sort')]")
                .isNotEmpty())
        .andExpect(jsonPath("$.paths['/api/v1/students/{studentId}/courses'].get").exists())
        .andExpect(jsonPath("$.paths['/api/v1/enrollments/{id}'].get").exists())
        .andExpect(jsonPath("$.paths['/api/v1/courses'].post.security[0].bearerAuth").exists())
        .andExpect(jsonPath("$.paths['/api/v1/courses/search'].get.security").doesNotExist())
        .andExpect(
            jsonPath("$.paths['/api/v1/payments/{id}/simulate'].post.security[0].bearerAuth")
                .exists())
        .andExpect(
            jsonPath(
                    "$.paths['/api/v1/certificates/enrollment/{enrollmentId}'].get.security[0].bearerAuth")
                .exists())
        .andExpect(
            jsonPath("$.paths['/api/v1/certificates/verify/{verificationCode}'].get.security")
                .doesNotExist());
  }
}
