package com.acme.courseplatform.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.postgresql.util.PSQLException;
import org.testcontainers.postgresql.PostgreSQLContainer;

class MigrationIntegrationTest {

  private static final PostgreSQLContainer POSTGRES =
      new PostgreSQLContainer("postgres:17.6-alpine");

  @BeforeAll
  static void startPostgresAndMigrate() {
    POSTGRES.start();
    Flyway.configure()
        .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
        .load()
        .migrate();
  }

  @AfterAll
  static void stopPostgres() {
    POSTGRES.stop();
  }

  @Test
  void appliesInitialSchemaThroughFlyway() throws SQLException {
    try (Connection connection = connection();
        Statement statement = connection.createStatement();
        ResultSet result =
            statement.executeQuery("select count(*) from flyway_schema_history where success")) {
      result.next();
      assertThat(result.getInt(1)).isEqualTo(4);
    }
  }

  @Test
  void databaseRejectsCourseOccupancyAboveCapacity() throws SQLException {
    try (Connection connection = connection();
        Statement statement = connection.createStatement()) {
      statement.executeUpdate(
          """
          insert into categories(id, name, description, status)
          values ('00000000-0000-0000-0000-000000000001', 'Backend', 'Backend courses', 'ACTIVE')
          """);
      statement.executeUpdate(
          """
          insert into users(id, email, password_hash, enabled)
          values ('00000000-0000-0000-0000-000000000002', 'migration-instructor@example.test', 'hash', true)
          """);
      statement.executeUpdate(
          """
          insert into instructors(id, user_id, name, email, biography)
          values ('00000000-0000-0000-0000-000000000003',
                  '00000000-0000-0000-0000-000000000002',
                  'Ada Instructor', 'migration-instructor@example.test', 'Senior instructor')
          """);

      assertThatThrownBy(
              () ->
                  statement.executeUpdate(
                      """
                      insert into courses(id, title, description, estimated_hours, level, price, currency,
                                          capacity, occupied_seats, status, category_id, instructor_id)
                      values ('00000000-0000-0000-0000-000000000004', 'Concurrency', 'Atomic seats', 8,
                              'ADVANCED', 99.90, 'EUR', 1, 2, 'PUBLISHED',
                              '00000000-0000-0000-0000-000000000001',
                              '00000000-0000-0000-0000-000000000003')
                      """))
          .isInstanceOf(PSQLException.class)
          .hasMessageContaining("courses_occupancy_check");
    }
  }

  private static Connection connection() throws SQLException {
    return DriverManager.getConnection(
        POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
  }
}
