package com.acme.courseplatform.identity.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
    properties = {
      "spring.ai.mcp.server.enabled=true",
      "spring.ai.mcp.server.protocol=STREAMABLE",
      "spring.rabbitmq.listener.simple.auto-startup=false"
    })
@AutoConfigureMockMvc
class SecurityIntegrationTest {

  @Container @ServiceConnection
  static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17.6-alpine");

  @Autowired MockMvc mvc;

  @Test
  void enforcesAnonymousRoleAndAdminMatrix() throws Exception {
    mvc.perform(
            post("/api/v1/categories")
                .contentType("application/json")
                .content("{\"name\":\"Security\",\"description\":\"Role matrix\"}"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.errorCode").value("AUTHENTICATION_REQUIRED"))
        .andExpect(jsonPath("$.correlationId").isNotEmpty());

    String student = login("student@example.test", "password");
    mvc.perform(
            post("/api/v1/categories")
                .header("Authorization", "Bearer " + student)
                .contentType("application/json")
                .content("{\"name\":\"Security\",\"description\":\"Role matrix\"}"))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.errorCode").value("ACCESS_DENIED"))
        .andExpect(jsonPath("$.correlationId").isNotEmpty());

    String admin = login("admin@example.test", "password");
    mvc.perform(
            post("/api/v1/categories")
                .header("Authorization", "Bearer " + admin)
                .contentType("application/json")
                .content("{\"name\":\"Security\",\"description\":\"Role matrix\"}"))
        .andExpect(status().isCreated());
  }

  @Test
  void rejectsInvalidCredentials() throws Exception {
    mvc.perform(
            post("/api/v1/auth/login")
                .contentType("application/json")
                .content("{\"email\":\"admin@example.test\",\"password\":\"wrong\"}"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.errorCode").value("INVALID_CREDENTIALS"));
  }

  @Test
  void mcpTransportRequiresAuthenticationAndExistsForValidToken() throws Exception {
    mvc.perform(post("/mcp").contentType("application/json").content("{}"))
        .andExpect(status().isUnauthorized());

    String admin = login("admin@example.test", "password");
    mvc.perform(
            post("/mcp")
                .header("Authorization", "Bearer " + admin)
                .contentType("application/json")
                .content("{}"))
        .andExpect(
            result ->
                assertThat(result.getResponse().getStatus()).isNotEqualTo(401).isNotEqualTo(404));
  }

  private String login(String email, String password) throws Exception {
    String json =
        mvc.perform(
                post("/api/v1/auth/login")
                    .contentType("application/json")
                    .content("{\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, password)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.token").isNotEmpty())
            .andExpect(jsonPath("$.tokenType").value("Bearer"))
            .andReturn()
            .getResponse()
            .getContentAsString();
    int start = json.indexOf("\"token\":\"") + 9;
    return json.substring(start, json.indexOf('"', start));
  }
}
