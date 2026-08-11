package com.acme.courseplatform.certificate.infrastructure.persistence;

import com.acme.courseplatform.certificate.application.model.CertificateView;
import com.acme.courseplatform.certificate.application.port.CertificateQueryStore;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcCertificateQueryStore implements CertificateQueryStore {

  private static final String SELECT =
      "select c.id,c.verification_code,c.issued_at,e.id enrollment_id,e.completed_at from certificates c join enrollments e on e.id=c.enrollment_id ";

  private final JdbcTemplate jdbc;

  public JdbcCertificateQueryStore(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  @Override
  public Optional<CertificateView> findByVerificationCode(String verificationCode) {
    return findOne("where c.verification_code=?", verificationCode);
  }

  @Override
  public Optional<CertificateView> findByEnrollmentId(UUID enrollmentId) {
    return findOne("where c.enrollment_id=?", enrollmentId);
  }

  private Optional<CertificateView> findOne(String condition, Object value) {
    try {
      return Optional.of(jdbc.queryForObject(SELECT + condition, this::map, value));
    } catch (EmptyResultDataAccessException exception) {
      return Optional.empty();
    }
  }

  private CertificateView map(ResultSet result, int rowNumber) throws SQLException {
    return new CertificateView(
        result.getObject("id", UUID.class),
        result.getString("verification_code"),
        result.getTimestamp("issued_at").toInstant(),
        result.getObject("enrollment_id", UUID.class),
        result.getTimestamp("completed_at").toInstant());
  }
}
