package com.acme.courseplatform.certificate.infrastructure.persistence;

import com.acme.courseplatform.certificate.application.port.CertificateRepository;
import com.acme.courseplatform.certificate.domain.Certificate;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;

@Repository
class CertificatePersistenceAdapter implements CertificateRepository {

  private final SpringDataCertificateRepository repository;

  CertificatePersistenceAdapter(SpringDataCertificateRepository repository) {
    this.repository = repository;
  }

  @Override
  public Certificate save(Certificate certificate) {
    return repository.saveAndFlush(CertificateJpaEntity.from(certificate)).toDomain();
  }

  @Override
  public Optional<Certificate> findByEnrollmentId(UUID enrollmentId) {
    return repository.findByEnrollmentId(enrollmentId).map(CertificateJpaEntity::toDomain);
  }
}
