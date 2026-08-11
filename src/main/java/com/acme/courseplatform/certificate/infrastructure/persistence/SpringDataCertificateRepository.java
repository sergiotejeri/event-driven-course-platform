package com.acme.courseplatform.certificate.infrastructure.persistence;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface SpringDataCertificateRepository extends JpaRepository<CertificateJpaEntity, UUID> {

  Optional<CertificateJpaEntity> findByEnrollmentId(UUID enrollmentId);
}
