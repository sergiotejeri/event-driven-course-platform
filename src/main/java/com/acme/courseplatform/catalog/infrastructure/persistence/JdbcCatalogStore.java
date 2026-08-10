package com.acme.courseplatform.catalog.infrastructure.persistence;

import static org.springframework.http.HttpStatus.CONFLICT;
import static org.springframework.http.HttpStatus.NOT_FOUND;

import com.acme.courseplatform.catalog.application.model.CategoryView;
import com.acme.courseplatform.catalog.application.model.CourseView;
import com.acme.courseplatform.catalog.application.model.InstructorView;
import com.acme.courseplatform.catalog.application.model.PageResult;
import com.acme.courseplatform.catalog.application.port.CatalogStore;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.web.server.ResponseStatusException;

@Repository
public class JdbcCatalogStore implements CatalogStore {

  private final JdbcTemplate jdbc;

  public JdbcCatalogStore(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
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
      throw new ResponseStatusException(NOT_FOUND);
    }
    return getCategory(id);
  }

  @Override
  public void deleteCategory(UUID id) {
    if (jdbc.update("delete from categories where id = ?", id) == 0) {
      throw new ResponseStatusException(NOT_FOUND);
    }
  }

  @Override
  public PageResult<CategoryView> listCategories(int page, int size) {
    List<CategoryView> content =
        jdbc.query(
            "select id, name, description, status from categories order by created_at, id limit ? offset ?",
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
        "catalog-pending-account");
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
  public PageResult<InstructorView> listInstructors(int page, int size) {
    List<InstructorView> content =
        jdbc.query(
            "select id, name, email, biography from instructors order by created_at, id limit ? offset ?",
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
      throw new ResponseStatusException(CONFLICT, "Instructor has courses");
    }
    jdbc.update("delete from instructors where id = ?", id);
  }

  @Override
  public PageResult<CourseView> searchCourses(
      String level, String title, Boolean available, int page, int size) {
    StringBuilder where = new StringBuilder(" where status = 'PUBLISHED'");
    List<Object> parameters = new ArrayList<>();
    if (level != null) {
      where.append(" and level = ?");
      parameters.add(level);
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
            "select * from courses" + where + " order by created_at, id limit ? offset ?",
            this::course,
            parameters.toArray());
    return new PageResult<>(content, total == null ? 0 : total, page, size);
  }

  @Override
  public PageResult<CourseView> cursorCourses(int size) {
    List<CourseView> content =
        jdbc.query("select * from courses order by created_at, id limit ?", this::course, size);
    return new PageResult<>(content, content.size(), 0, size);
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
      throw new ResponseStatusException(NOT_FOUND);
    }
    return values.getFirst();
  }
}
