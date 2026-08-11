package com.acme.courseplatform.certificate.application.port;

import com.acme.courseplatform.certificate.application.model.CertificateView;
import java.util.Optional;
import java.util.UUID;

public interface CertificateQueryStore {

  Optional<CertificateView> findByVerificationCode(String verificationCode);

  Optional<CertificateView> findByEnrollmentId(UUID enrollmentId);
}
