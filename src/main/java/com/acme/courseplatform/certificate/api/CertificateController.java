package com.acme.courseplatform.certificate.api;

import com.acme.courseplatform.certificate.application.FindCertificateUseCase;
import com.acme.courseplatform.certificate.application.model.CertificateView;
import com.acme.courseplatform.identity.application.AuthorizationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
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
  @Operation(summary = "Verify a certificate by its public code")
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "Certificate found"),
    @ApiResponse(responseCode = "404", description = "Certificate not found")
  })
  CertificateView verify(@PathVariable String verificationCode) {
    return certificates.findByVerificationCode(verificationCode);
  }

  @GetMapping("/enrollment/{enrollmentId}")
  @Operation(
      summary = "Get the certificate for an owned enrollment",
      security = @SecurityRequirement(name = "bearerAuth"))
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "Certificate found"),
    @ApiResponse(responseCode = "401", description = "Authentication required"),
    @ApiResponse(responseCode = "403", description = "Enrollment ownership required"),
    @ApiResponse(responseCode = "404", description = "Certificate not found")
  })
  CertificateView findByEnrollment(Authentication authentication, @PathVariable UUID enrollmentId) {
    return certificates.findByEnrollment(authorization.actor(authentication), enrollmentId);
  }
}
