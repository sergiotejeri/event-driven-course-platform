package com.acme.courseplatform.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.acme.courseplatform.CoursePlatformApplication;
import com.acme.courseplatform.mcp.CatalogMcpTools;
import com.acme.courseplatform.mcp.EnrollmentMcpTools;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@Testcontainers
@SpringBootTest(
    classes = CoursePlatformApplication.class,
    properties = {
      "spring.ai.mcp.server.enabled=false",
      "spring.rabbitmq.listener.simple.auto-startup=false"
    })
class McpAuthorizationIntegrationTest {

  @Container @ServiceConnection
  static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17.6-alpine");

  @Autowired CatalogMcpTools catalog;

  @AfterEach
  void clearAuthentication() {
    SecurityContextHolder.clearContext();
  }

  @Test
  void registersExactlyTheApprovedToolsWithoutSpoofableActorParameters() {
    Set<String> names =
        Stream.of(CatalogMcpTools.class, EnrollmentMcpTools.class)
            .flatMap(type -> Arrays.stream(type.getDeclaredMethods()))
            .filter(method -> method.isAnnotationPresent(McpTool.class))
            .peek(McpAuthorizationIntegrationTest::assertNoActorParameter)
            .map(method -> method.getAnnotation(McpTool.class).name())
            .collect(Collectors.toSet());

    assertThat(names)
        .containsExactlyInAnyOrder(
            "list_courses",
            "search_courses",
            "get_course",
            "create_course",
            "update_course",
            "delete_course",
            "publish_course",
            "archive_course",
            "list_categories",
            "get_category",
            "create_category",
            "update_category",
            "archive_category",
            "delete_category",
            "list_instructors",
            "get_instructor",
            "create_instructor",
            "update_instructor",
            "delete_instructor",
            "list_students",
            "get_student",
            "create_student",
            "update_student",
            "enroll_student",
            "get_enrollment",
            "list_students_by_course",
            "list_courses_by_student",
            "update_enrollment_progress",
            "cancel_enrollment");
  }

  @Test
  void adminToolDelegatesToPersistentCatalogLogic() {
    authenticate("10000000-0000-0000-0000-000000000001", "ADMIN");
    String name = "MCP-" + System.nanoTime();

    var created = catalog.createCategory(name, "Created through MCP");

    assertThat(catalog.listCategories(0, 100, null).content())
        .extracting("id")
        .contains(created.id());
  }

  @Test
  void studentCannotInvokeAdministratorTool() {
    authenticate("10000000-0000-0000-0000-000000000003", "STUDENT");

    assertThatThrownBy(() -> catalog.createCategory("Forbidden", "Not allowed"))
        .isInstanceOf(AccessDeniedException.class);
  }

  private static void assertNoActorParameter(Method method) {
    Set<String> parameters =
        Arrays.stream(method.getParameters()).map(Parameter::getName).collect(Collectors.toSet());
    assertThat(parameters).doesNotContain("actor", "actorUserId", "userId");
  }

  private static void authenticate(String subject, String role) {
    Instant now = Instant.now();
    Jwt jwt =
        new Jwt(
            "mcp-token",
            now,
            now.plusSeconds(300),
            Map.of("alg", "none"),
            Map.of("sub", subject, "roles", List.of(role)));
    SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));
  }
}
