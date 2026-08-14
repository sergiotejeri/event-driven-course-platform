package com.acme.courseplatform.catalog.application.model;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;

public record CourseCursor(Instant createdAt, UUID id) {

  public String encode() {
    String value = createdAt + "|" + id;
    return Base64.getUrlEncoder()
        .withoutPadding()
        .encodeToString(value.getBytes(StandardCharsets.UTF_8));
  }

  public static CourseCursor decode(String value) {
    if (value == null || value.isBlank()) {
      return null;
    }
    try {
      String decoded = new String(Base64.getUrlDecoder().decode(value), StandardCharsets.UTF_8);
      String[] parts = decoded.split("\\|", -1);
      if (parts.length != 2) {
        throw new IllegalArgumentException();
      }
      return new CourseCursor(Instant.parse(parts[0]), UUID.fromString(parts[1]));
    } catch (RuntimeException exception) {
      throw new IllegalArgumentException("Invalid course cursor");
    }
  }
}
