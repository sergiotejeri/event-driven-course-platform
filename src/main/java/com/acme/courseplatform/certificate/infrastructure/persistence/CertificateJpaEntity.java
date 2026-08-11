package com.acme.courseplatform.certificate.infrastructure.persistence;

import com.acme.courseplatform.certificate.domain.Certificate;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "certificates")
class CertificateJpaEntity {

  @Id UUID id;

  @Column(name = "enrollment_id", nullable = false)
  UUID enrollmentId;

  @Column(name = "verification_code", nullable = false)
  String verificationCode;

  @Column(name = "issued_at", nullable = false)
  Instant issuedAt;

  protected CertificateJpaEntity() {}

  static CertificateJpaEntity from(Certificate certificate) {
    CertificateJpaEntity entity = new CertificateJpaEntity();
    entity.id = certificate.id();
    entity.enrollmentId = certificate.enrollmentId();
    entity.verificationCode = certificate.verificationCode();
    entity.issuedAt = certificate.issuedAt();
    return entity;
  }

  Certificate toDomain() {
    return new Certificate(id, enrollmentId, verificationCode, issuedAt);
  }
}
