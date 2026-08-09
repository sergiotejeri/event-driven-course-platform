package com.acme.courseplatform.catalog.domain;

import java.util.UUID;

public record Instructor(UUID id, UUID userId, String name, String email, String biography) {

  public Instructor {
    requireNormalizedEmail(email);
  }

  private static void requireNormalizedEmail(String email) {
    if (email == null || !email.equals(email.trim().toLowerCase())) {
      throw new IllegalArgumentException("Email must be normalized");
    }
  }
}
