package com.acme.courseplatform.catalog.infrastructure.persistence;

import com.acme.courseplatform.catalog.application.model.PageResult;
import com.acme.courseplatform.catalog.application.model.StudentView;
import com.acme.courseplatform.catalog.application.port.StudentCatalogStore;
import com.acme.courseplatform.shared.api.ResourceNotFoundException;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcStudentCatalogStore implements StudentCatalogStore {

  private final JdbcTemplate jdbc;
  private final PasswordEncoder passwordEncoder;
  private final String provisioningPassword;

  public JdbcStudentCatalogStore(
      JdbcTemplate jdbc,
      PasswordEncoder passwordEncoder,
      @Value("${app.security.provisioning-password}") String provisioningPassword) {
    this.jdbc = jdbc;
    this.passwordEncoder = passwordEncoder;
    this.provisioningPassword = provisioningPassword;
  }

  @Override
  public PageResult<StudentView> list(int page, int size) {
    List<StudentView> content =
        jdbc.query(
            "select id, first_name, last_name, email from students order by registered_at desc, id limit ? offset ?",
            (row, index) ->
                view(
                    row.getObject("id", UUID.class),
                    row.getString("first_name"),
                    row.getString("last_name"),
                    row.getString("email")),
            size,
            page * size);
    Long total = jdbc.queryForObject("select count(*) from students", Long.class);
    return new PageResult<>(content, total == null ? 0 : total, page, size);
  }

  @Override
  public StudentView get(UUID id) {
    List<StudentView> students =
        jdbc.query(
            "select id, first_name, last_name, email from students where id = ?",
            (row, index) ->
                view(
                    row.getObject("id", UUID.class),
                    row.getString("first_name"),
                    row.getString("last_name"),
                    row.getString("email")),
            id);
    if (students.isEmpty()) {
      throw new ResourceNotFoundException("Student", id);
    }
    return students.getFirst();
  }

  @Override
  public StudentView create(String firstName, String lastName, String email) {
    UUID userId = UUID.randomUUID();
    UUID studentId = UUID.randomUUID();
    jdbc.update(
        "insert into users(id, email, password_hash, enabled) values (?, ?, ?, true)",
        userId,
        email,
        passwordEncoder.encode(provisioningPassword));
    jdbc.update("insert into user_roles(user_id, role_name) values (?, 'STUDENT')", userId);
    jdbc.update(
        "insert into students(id, user_id, first_name, last_name, email) values (?, ?, ?, ?, ?)",
        studentId,
        userId,
        firstName,
        lastName,
        email);
    return view(studentId, firstName, lastName, email);
  }

  @Override
  public StudentView update(UUID id, String firstName, String lastName, String email) {
    List<UUID> users =
        jdbc.query(
            "select user_id from students where id = ?",
            (row, index) -> row.getObject("user_id", UUID.class),
            id);
    if (users.isEmpty()) {
      throw new ResourceNotFoundException("Student", id);
    }
    jdbc.update(
        "update users set email = ?, updated_at = now() where id = ?", email, users.getFirst());
    jdbc.update(
        "update students set first_name = ?, last_name = ?, email = ?, updated_at = now() where id = ?",
        firstName,
        lastName,
        email,
        id);
    return view(id, firstName, lastName, email);
  }

  private static StudentView view(UUID id, String firstName, String lastName, String email) {
    return new StudentView(id, firstName, lastName, email);
  }
}
