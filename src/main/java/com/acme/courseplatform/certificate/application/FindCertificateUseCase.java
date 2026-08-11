package com.acme.courseplatform.certificate.application;

import com.acme.courseplatform.certificate.application.model.CertificateView;
import com.acme.courseplatform.certificate.application.port.CertificateQueryStore;
import com.acme.courseplatform.identity.application.AuthorizationService;
import com.acme.courseplatform.identity.application.CurrentActor;
import com.acme.courseplatform.shared.api.ResourceNotFoundException;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class FindCertificateUseCase {

  private final CertificateQueryStore certificates;
  private final AuthorizationService authorization;

  public FindCertificateUseCase(
      CertificateQueryStore certificates, AuthorizationService authorization) {
    this.certificates = certificates;
    this.authorization = authorization;
  }

  public CertificateView findByVerificationCode(String verificationCode) {
    return certificates
        .findByVerificationCode(verificationCode)
        .orElseThrow(() -> new ResourceNotFoundException("certificate", verificationCode));
  }

  public CertificateView findByEnrollment(CurrentActor actor, UUID enrollmentId) {
    authorization.requireEnrollmentOwnerOrAdmin(actor, enrollmentId);
    return certificates
        .findByEnrollmentId(enrollmentId)
        .orElseThrow(
            () -> new ResourceNotFoundException("certificate for enrollment", enrollmentId));
  }
}
