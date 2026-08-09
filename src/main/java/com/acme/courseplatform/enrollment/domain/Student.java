package com.acme.courseplatform.enrollment.domain;

import java.util.UUID;

public record Student(UUID id, UUID userId, String firstName, String lastName, String email) {

  public Student {
    if (email == null || !email.equals(email.trim().toLowerCase())) {
      throw new IllegalArgumentException("Email must be normalized");
    }
  }
}
