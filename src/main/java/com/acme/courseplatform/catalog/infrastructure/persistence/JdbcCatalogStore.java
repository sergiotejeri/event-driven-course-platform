package com.acme.courseplatform.catalog.infrastructure.persistence;

import com.acme.courseplatform.catalog.application.model.CategoryView;
import com.acme.courseplatform.catalog.application.model.CourseCursor;
import com.acme.courseplatform.catalog.application.model.CourseView;
import com.acme.courseplatform.catalog.application.model.CursorPage;
import com.acme.courseplatform.catalog.application.model.InstructorView;
import com.acme.courseplatform.catalog.application.model.PageResult;
import com.acme.courseplatform.catalog.application.port.CatalogStore;
import com.acme.courseplatform.catalog.domain.CategoryStatus;
import com.acme.courseplatform.shared.api.ConflictException;
import com.acme.courseplatform.shared.api.ResourceNotFoundException;
import com.acme.courseplatform.shared.query.SortSpec;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcCatalogStore implements CatalogStore {

  private final JdbcTemplate jdbc;
  private final PasswordEncoder passwordEncoder;
  private final String provisioningPassword;

  public JdbcCatalogStore(
      JdbcTemplate jdbc,
      PasswordEncoder passwordEncoder,
      @Value("${app.security.provisioning-password}") String provisioningPassword) {
    this.jdbc = jdbc;
    this.passwordEncoder = passwordEncoder;
    this.provisioningPassword = provisioningPassword;
  }

  @Override
  public CategoryView createCategory(String name, String description) {
    UUID id = UUID.randomUUID();
    jdbc.update(
        "insert into categories(id, name, description, status) values (?, ?, ?, 'ACTIVE')",
        id,
        name,
        description);
    return new CategoryView(id, name, description, "ACTIVE");
  }

  @Override
  public CategoryView getCategory(UUID id) {
    List<CategoryView> result =
        jdbc.query(
            "select id, name, description, status from categories where id = ?",
            (row, index) ->
                new CategoryView(
                    row.getObject("id", UUID.class),
                    row.getString("name"),
                    row.getString("description"),
                    row.getString("status")),
            id);
    return required(result);
  }

  @Override
  public CategoryView updateCategory(UUID id, String name, String description) {
    if (jdbc.update(
            "update categories set name = ?, description = ?, updated_at = now() where id = ?",
            name,
            description,
            id)
        == 0) {
      throw new ResourceNotFoundException("Category", id);
    }
    return getCategory(id);
  }

  @Override
  public CategoryView changeCategoryStatus(UUID id, CategoryStatus status) {
    if (jdbc.update(
            "update categories set status = ?, updated_at = now() where id = ?", status.name(), id)
        == 0) {
      throw new ResourceNotFoundException("Category", id);
    }
    return getCategory(id);
  }

  @Override
  public void deleteCategory(UUID id) {
    if (jdbc.update("delete from categories where id = ?", id) == 0) {
      throw new ResourceNotFoundException("Category", id);
    }
  }

  @Override
  public PageResult<CategoryView> listCategories(int page, int size, SortSpec sort) {
    List<CategoryView> content =
        jdbc.query(
            "select id, name, description, status from categories order by "
                + sort.sqlOrder()
                + " limit ? offset ?",
            (result, row) ->
                new CategoryView(
                    result.getObject("id", UUID.class),
                    result.getString("name"),
                    result.getString("description"),
                    result.getString("status")),
            size,
            page * size);
    return new PageResult<>(content, count("categories"), page, size);
  }

  @Override
  public InstructorView createInstructor(String name, String email, String biography) {
    UUID userId = UUID.randomUUID();
    UUID id = UUID.randomUUID();
    jdbc.update(
        "insert into users(id, email, password_hash, enabled) values (?, ?, ?, true)",
        userId,
        email,
        passwordEncoder.encode(provisioningPassword));
    jdbc.update("insert into user_roles(user_id, role_name) values (?, 'INSTRUCTOR')", userId);
    jdbc.update(
        "insert into instructors(id, user_id, name, email, biography) values (?, ?, ?, ?, ?)",
        id,
        userId,
        name,
        email,
        biography);
    return new InstructorView(id, name, email, biography);
  }

  @Override
  public InstructorView getInstructor(UUID id) {
    List<InstructorView> result =
        jdbc.query(
            "select id, name, email, biography from instructors where id = ?",
            (row, index) ->
                new InstructorView(
                    row.getObject("id", UUID.class),
                    row.getString("name"),
                    row.getString("email"),
                    row.getString("biography")),
            id);
    return required(result);
  }

  @Override
  public InstructorView updateInstructor(UUID id, String name, String email, String biography) {
    List<UUID> users =
        jdbc.query(
            "select user_id from instructors where id = ?",
            (row, index) -> row.getObject("user_id", UUID.class),
            id);
    UUID userId = required(users);
    jdbc.update("update users set email = ?, updated_at = now() where id = ?", email, userId);
    jdbc.update(
        "update instructors set name = ?, email = ?, biography = ?, updated_at = now() where id = ?",
        name,
        email,
        biography,
        id);
    return getInstructor(id);
  }

  @Override
  public PageResult<InstructorView> listInstructors(int page, int size, SortSpec sort) {
    List<InstructorView> content =
        jdbc.query(
            "select id, name, email, biography from instructors order by "
                + sort.sqlOrder()
                + " limit ? offset ?",
            (result, row) ->
                new InstructorView(
                    result.getObject("id", UUID.class),
                    result.getString("name"),
                    result.getString("email"),
                    result.getString("biography")),
            size,
            page * size);
    return new PageResult<>(content, count("instructors"), page, size);
  }

  @Override
  public void deleteInstructor(UUID id) {
    Long courses =
        jdbc.queryForObject("select count(*) from courses where instructor_id = ?", Long.class, id);
    if (courses != null && courses > 0) {
      throw new ConflictException("INSTRUCTOR_IN_USE", "Instructor has courses");
    }
    jdbc.update("delete from instructors where id = ?", id);
  }

  @Override
  public PageResult<CourseView> searchCourses(
      UUID categoryId,
      String level,
      BigDecimal minPrice,
      BigDecimal maxPrice,
      String title,
      Boolean available,
      int page,
      int size,
      SortSpec sort) {
    StringBuilder where = new StringBuilder(" where status = 'PUBLISHED'");
    List<Object> parameters = new ArrayList<>();
    if (categoryId != null) {
      where.append(" and category_id = ?");
      parameters.add(categoryId);
    }
    if (level != null) {
      where.append(" and level = ?");
      parameters.add(level);
    }
    if (minPrice != null) {
      where.append(" and price >= ?");
      parameters.add(minPrice);
    }
    if (maxPrice != null) {
      where.append(" and price <= ?");
      parameters.add(maxPrice);
    }
    if (title != null) {
      where.append(" and lower(title) like lower(?)");
      parameters.add("%" + title + "%");
    }
    if (Boolean.TRUE.equals(available)) {
      where.append(" and occupied_seats < capacity");
    }
    Long total =
        jdbc.queryForObject(
            "select count(*) from courses" + where, Long.class, parameters.toArray());
    parameters.add(size);
    parameters.add(page * size);
    List<CourseView> content =
        jdbc.query(
            "select * from courses" + where + " order by " + sort.sqlOrder() + " limit ? offset ?",
            this::course,
            parameters.toArray());
    return new PageResult<>(content, total == null ? 0 : total, page, size);
  }

  @Override
  public CursorPage<CourseView> cursorCourses(CourseCursor cursor, int size) {
    String after = cursor == null ? "" : " and (created_at,id) < (?,?)";
    List<CursorRow> rows;
    if (cursor == null) {
      rows =
          jdbc.query(
              "select * from courses where status='PUBLISHED' order by created_at desc,id desc limit ?",
              this::cursorRow,
              size + 1);
    } else {
      rows =
          jdbc.query(
              "select * from courses where status='PUBLISHED'"
                  + after
                  + " order by created_at desc,id desc limit ?",
              this::cursorRow,
              Timestamp.from(cursor.createdAt()),
              cursor.id(),
              size + 1);
    }
    boolean hasNext = rows.size() > size;
    List<CursorRow> page = hasNext ? rows.subList(0, size) : rows;
    String next =
        hasNext
            ? new CourseCursor(page.getLast().createdAt(), page.getLast().course().id()).encode()
            : null;
    return new CursorPage<>(page.stream().map(CursorRow::course).toList(), next);
  }

  private CursorRow cursorRow(ResultSet result, int row) throws SQLException {
    return new CursorRow(course(result, row), result.getTimestamp("created_at").toInstant());
  }

  private long count(String table) {
    Long result = jdbc.queryForObject("select count(*) from " + table, Long.class);
    return result == null ? 0 : result;
  }

  private CourseView course(ResultSet result, int row) throws SQLException {
    return new CourseView(
        result.getObject("id", UUID.class),
        result.getString("title"),
        result.getString("description"),
        result.getInt("estimated_hours"),
        result.getString("level"),
        result.getObject("price", BigDecimal.class),
        result.getString("currency"),
        result.getInt("capacity"),
        result.getInt("occupied_seats"),
        result.getString("status"),
        result.getObject("category_id", UUID.class),
        result.getObject("instructor_id", UUID.class));
  }

  private static <T> T required(List<T> values) {
    if (values.isEmpty()) {
      throw new ResourceNotFoundException("Catalog resource", "unknown");
    }
    return values.getFirst();
  }

  private record CursorRow(CourseView course, Instant createdAt) {}
}
