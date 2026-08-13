package com.acme.courseplatform.certificate.application;

import com.acme.courseplatform.certificate.application.port.CertificateRepository;
import com.acme.courseplatform.certificate.domain.Certificate;
import com.acme.courseplatform.messaging.application.ProcessedEventService;
import com.acme.courseplatform.observability.BusinessMetrics;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class IssueCertificateUseCase {

  private static final String CONSUMER = "certificate-issuer-v1";

  private final CertificateRepository certificates;
  private final ProcessedEventService processedEvents;
  private final BusinessMetrics metrics;
  private final SecureRandom random = new SecureRandom();

  public IssueCertificateUseCase(
      CertificateRepository certificates,
      ProcessedEventService processedEvents,
      BusinessMetrics metrics) {
    this.certificates = certificates;
    this.processedEvents = processedEvents;
    this.metrics = metrics;
  }

  @Transactional
  public void issue(UUID eventId, UUID enrollmentId) {
    if (!processedEvents.claim(CONSUMER, eventId)) {
      return;
    }
    if (certificates.findByEnrollmentId(enrollmentId).isPresent()) {
      return;
    }
    byte[] bytes = new byte[24];
    random.nextBytes(bytes);
    String verificationCode = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    certificates.save(Certificate.issue(UUID.randomUUID(), enrollmentId, verificationCode));
    metrics.certificateIssued();
  }
}
