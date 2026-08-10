package com.acme.courseplatform.enrollment.infrastructure.persistence;

import com.acme.courseplatform.enrollment.application.model.ReservedCourse;
import com.acme.courseplatform.enrollment.application.port.CourseSeatPort;
import com.acme.courseplatform.enrollment.application.port.EnrollmentIdempotencyPort;
import com.acme.courseplatform.enrollment.application.port.StudentPort;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcEnrollmentStore implements CourseSeatPort, StudentPort, EnrollmentIdempotencyPort {

  private final JdbcTemplate jdbc;

  public JdbcEnrollmentStore(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  @Override
  public Optional<ReservedCourse> reserve(UUID courseId) {
    return jdbc
        .query(
            "update courses set occupied_seats = occupied_seats + 1, updated_at = now() where id = ? and status = 'PUBLISHED' and occupied_seats < capacity returning price, currency",
            (result, row) ->
                new ReservedCourse(result.getBigDecimal("price"), result.getString("currency")),
            courseId)
        .stream()
        .findFirst();
  }

  @Override
  public Optional<UUID> findStudentId(UUID userId) {
    return jdbc
        .query(
            "select id from students where user_id = ?",
            (result, row) -> result.getObject("id", UUID.class),
            userId)
        .stream()
        .findFirst();
  }

  @Override
  public Optional<Replay> find(UUID actorId, String key) {
    return jdbc
        .query(
            "select r.request_hash,r.resource_id,r.status,p.id payment_id from idempotency_records r left join payments p on p.enrollment_id = r.resource_id where r.actor_id = ? and r.operation = ? and r.idempotency_key = ?",
            this::replay,
            actorId,
            "ENROLL_STUDENT",
            key)
        .stream()
        .findFirst();
  }

  @Override
  public boolean start(UUID actorId, String key, String requestHash) {
    return jdbc.update(
            "insert into idempotency_records(id,actor_id,operation,idempotency_key,request_hash,status) values (?,?,?, ?,?,'PROCESSING') on conflict(actor_id,operation,idempotency_key) do nothing",
            UUID.randomUUID(),
            actorId,
            "ENROLL_STUDENT",
            key,
            requestHash)
        == 1;
  }

  @Override
  public void complete(
      UUID actorId,
      String key,
      com.acme.courseplatform.enrollment.application.model.EnrollmentResult result) {
    jdbc.update(
        "update idempotency_records set resource_type='ENROLLMENT',resource_id=?,response_status=201,status='COMPLETED',completed_at=now() where actor_id=? and operation=? and idempotency_key=?",
        result.enrollmentId(),
        actorId,
        "ENROLL_STUDENT",
        key);
  }

  private Replay replay(ResultSet result, int row) throws SQLException {
    return new Replay(
        result.getString("request_hash"),
        result.getObject("resource_id", UUID.class),
        result.getObject("payment_id", UUID.class),
        "COMPLETED".equals(result.getString("status")));
  }
}
