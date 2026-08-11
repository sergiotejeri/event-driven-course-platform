package com.acme.courseplatform.certificate.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record Certificate(UUID id, UUID enrollmentId, String verificationCode, Instant issuedAt) {

  public Certificate {
    Objects.requireNonNull(id, "id");
    Objects.requireNonNull(enrollmentId, "enrollmentId");
    Objects.requireNonNull(issuedAt, "issuedAt");
    if (verificationCode == null || verificationCode.isBlank()) {
      throw new IllegalArgumentException("Verification code must not be blank");
    }
    verificationCode = verificationCode.trim();
  }

  public static Certificate issue(UUID id, UUID enrollmentId, String verificationCode) {
    return new Certificate(id, enrollmentId, verificationCode, Instant.now());
  }
}
