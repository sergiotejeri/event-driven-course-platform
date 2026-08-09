package com.acme.courseplatform.certificate.domain;

import java.util.UUID;

public record Certificate(UUID id, UUID enrollmentId, String verificationCode) {

  public static Certificate issue(UUID id, UUID enrollmentId, String verificationCode) {
    if (verificationCode == null || verificationCode.isBlank()) {
      throw new IllegalArgumentException("Verification code must not be blank");
    }
    return new Certificate(id, enrollmentId, verificationCode);
  }
}
