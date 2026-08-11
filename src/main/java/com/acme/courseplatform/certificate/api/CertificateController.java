package com.acme.courseplatform.certificate.api;

import com.acme.courseplatform.certificate.application.FindCertificateUseCase;
import com.acme.courseplatform.certificate.application.model.CertificateView;
import com.acme.courseplatform.identity.application.AuthorizationService;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/certificates")
public class CertificateController {

  private final FindCertificateUseCase certificates;
  private final AuthorizationService authorization;

  public CertificateController(
      FindCertificateUseCase certificates, AuthorizationService authorization) {
    this.certificates = certificates;
    this.authorization = authorization;
  }

  @GetMapping("/verify/{verificationCode}")
  CertificateView verify(@PathVariable String verificationCode) {
    return certificates.findByVerificationCode(verificationCode);
  }

  @GetMapping("/enrollment/{enrollmentId}")
  CertificateView findByEnrollment(Authentication authentication, @PathVariable UUID enrollmentId) {
    return certificates.findByEnrollment(authorization.actor(authentication), enrollmentId);
  }
}
