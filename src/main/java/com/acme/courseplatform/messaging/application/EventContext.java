package com.acme.courseplatform.messaging.application;

import java.util.UUID;

public record EventContext(UUID correlationId, UUID causationId) {

  public EventContext {
    if (correlationId == null) {
      throw new IllegalArgumentException("correlationId is required");
    }
  }

  public static EventContext causedBy(UUID correlationId, UUID eventId) {
    return new EventContext(correlationId, eventId);
  }
}
