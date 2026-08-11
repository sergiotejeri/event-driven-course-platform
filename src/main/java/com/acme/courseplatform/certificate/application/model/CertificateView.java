package com.acme.courseplatform.certificate.application.model;

import java.time.Instant;
import java.util.UUID;

public record CertificateView(
    UUID id, String verificationCode, Instant issuedAt, UUID enrollmentId, Instant completedAt) {}
