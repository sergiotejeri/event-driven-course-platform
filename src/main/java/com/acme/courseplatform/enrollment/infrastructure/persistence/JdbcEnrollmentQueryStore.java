package com.acme.courseplatform.enrollment.infrastructure.persistence;

import com.acme.courseplatform.catalog.application.model.PageResult;
import com.acme.courseplatform.enrollment.application.port.EnrollmentQueryStore;
import com.acme.courseplatform.shared.query.SortSpec;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcEnrollmentQueryStore implements EnrollmentQueryStore {

  private final JdbcTemplate jdbc;

  public JdbcEnrollmentQueryStore(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  @Override
  public Optional<EnrollmentView> findById(UUID enrollmentId) {
    return jdbc
        .query(
            "select id,student_id,course_id,status,progress,enrolled_at,completed_at,cancelled_at from enrollments where id=?",
            (result, row) ->
                new EnrollmentView(
                    result.getObject("id", UUID.class),
                    result.getObject("student_id", UUID.class),
                    result.getObject("course_id", UUID.class),
                    result.getString("status"),
                    result.getInt("progress"),
                    result.getTimestamp("enrolled_at").toInstant(),
                    result.getTimestamp("completed_at") == null
                        ? null
                        : result.getTimestamp("completed_at").toInstant(),
                    result.getTimestamp("cancelled_at") == null
                        ? null
                        : result.getTimestamp("cancelled_at").toInstant()),
            enrollmentId)
        .stream()
        .findFirst();
  }

  @Override
  public PageResult<StudentEnrollmentView> findStudentsByCourse(
      UUID courseId, int page, int size, SortSpec sort) {
    List<StudentEnrollmentView> content =
        jdbc.query(
            "select s.id student_id,s.first_name,s.last_name,s.email,e.id enrollment_id,e.status,e.progress from enrollments e join students s on s.id=e.student_id where e.course_id=? order by "
                + sort.sqlOrder()
                + " limit ? offset ?",
            this::student,
            courseId,
            size,
            page * size);
    long total = count("select count(*) from enrollments where course_id=?", courseId);
    return new PageResult<>(content, total, page, size);
  }

  @Override
  public PageResult<StudentCourseView> findCoursesByStudent(
      UUID studentId, int page, int size, SortSpec sort) {
    List<StudentCourseView> content =
        jdbc.query(
            "select c.id course_id,c.title,c.level,c.price,c.currency,e.id enrollment_id,e.status,e.progress,e.enrolled_at from enrollments e join courses c on c.id=e.course_id where e.student_id=? order by "
                + sort.sqlOrder()
                + " limit ? offset ?",
            this::course,
            studentId,
            size,
            page * size);
    long total = count("select count(*) from enrollments where student_id=?", studentId);
    return new PageResult<>(content, total, page, size);
  }

  private long count(String sql, UUID id) {
    Long result = jdbc.queryForObject(sql, Long.class, id);
    return result == null ? 0 : result;
  }

  private StudentEnrollmentView student(ResultSet result, int row) throws SQLException {
    return new StudentEnrollmentView(
        result.getObject("student_id", UUID.class),
        result.getString("first_name"),
        result.getString("last_name"),
        result.getString("email"),
        result.getObject("enrollment_id", UUID.class),
        result.getString("status"),
        result.getInt("progress"));
  }

  private StudentCourseView course(ResultSet result, int row) throws SQLException {
    return new StudentCourseView(
        result.getObject("course_id", UUID.class),
        result.getString("title"),
        result.getString("level"),
        result.getObject("price", BigDecimal.class),
        result.getString("currency"),
        result.getObject("enrollment_id", UUID.class),
        result.getString("status"),
        result.getInt("progress"),
        result.getTimestamp("enrolled_at").toInstant());
  }
}
