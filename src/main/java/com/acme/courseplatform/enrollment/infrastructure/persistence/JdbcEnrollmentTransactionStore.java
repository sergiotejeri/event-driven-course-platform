package com.acme.courseplatform.enrollment.infrastructure.persistence;

import com.acme.courseplatform.enrollment.application.port.EnrollmentTransactionStore;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcEnrollmentTransactionStore implements EnrollmentTransactionStore {

  private final JdbcTemplate jdbc;

  public JdbcEnrollmentTransactionStore(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  @Override
  public boolean activatePendingPayment(UUID enrollmentId) {
    return jdbc.update(
            "update enrollments set status='ACTIVE',updated_at=now() where id=? and status='PENDING_PAYMENT'",
            enrollmentId)
        == 1;
  }

  @Override
  public boolean cancelPendingPayment(UUID enrollmentId) {
    return jdbc.update(
            "with cancelled as (update enrollments set status='CANCELLED',cancelled_at=now(),updated_at=now() where id=? and status='PENDING_PAYMENT' returning course_id) update courses set occupied_seats=occupied_seats-1,updated_at=now() where id=(select course_id from cancelled)",
            enrollmentId)
        == 1;
  }
}
