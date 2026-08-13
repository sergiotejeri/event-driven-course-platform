package com.acme.courseplatform.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.acme.courseplatform.CoursePlatformApplication;
import com.acme.courseplatform.observability.BusinessMetrics;
import io.micrometer.core.instrument.MeterRegistry;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@Testcontainers
@SpringBootTest(classes = CoursePlatformApplication.class)
@AutoConfigureMockMvc
class ObservabilityIntegrationTest {
  @Container
  static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17.6-alpine");

  @DynamicPropertySource
  static void properties(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
    registry.add("spring.datasource.username", POSTGRES::getUsername);
    registry.add("spring.datasource.password", POSTGRES::getPassword);
    registry.add("spring.cache.type", () -> "none");
    registry.add("spring.ai.mcp.server.enabled", () -> "false");
    registry.add("spring.rabbitmq.listener.simple.auto-startup", () -> "false");
    registry.add("management.otlp.tracing.export.enabled", () -> "false");
  }

  @Autowired MockMvc mvc;
  @Autowired BusinessMetrics metrics;
  @Autowired MeterRegistry registry;

  @Test
  void exposesProbesPrometheusBusinessMetricsAndCorrelation() throws Exception {
    var sample = metrics.enrollmentStarted(registry);
    metrics.enrollmentSucceeded(sample);

    mvc.perform(get("/actuator/health/liveness"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("UP"));
    mvc.perform(get("/actuator/health/readiness"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("UP"));
    mvc.perform(get("/actuator/prometheus"))
        .andExpect(status().isOk())
        .andExpect(
            content().string(Matchers.containsString("course_platform_enrollment_attempts_total")));
    mvc.perform(get("/v3/api-docs").header("X-Correlation-Id", "obs-test-123"))
        .andExpect(header().string("X-Correlation-Id", "obs-test-123"));

    assertThat(registry.get("course_platform_enrollment_success_total").counter().count())
        .isGreaterThanOrEqualTo(1);
  }
}
