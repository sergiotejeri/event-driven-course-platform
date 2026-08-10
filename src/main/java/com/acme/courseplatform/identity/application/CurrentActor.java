package com.acme.courseplatform.identity.application;

import java.util.Set;
import java.util.UUID;

public record CurrentActor(UUID userId, Set<String> roles) {

  public boolean hasRole(String role) {
    return roles.contains(role);
  }
}
