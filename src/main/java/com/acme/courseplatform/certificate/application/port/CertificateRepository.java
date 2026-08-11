package com.acme.courseplatform.certificate.application.port;

import com.acme.courseplatform.certificate.domain.Certificate;
import java.util.Optional;
import java.util.UUID;

public interface CertificateRepository {

  Certificate save(Certificate certificate);

  Optional<Certificate> findByEnrollmentId(UUID enrollmentId);
}
