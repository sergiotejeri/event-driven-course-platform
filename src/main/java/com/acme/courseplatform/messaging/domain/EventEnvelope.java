package com.acme.courseplatform.messaging.domain;

import java.time.Instant;
import java.util.UUID;

public record EventEnvelope<T>(
    UUID eventId,
    String eventType,
    int eventVersion,
    String aggregateType,
    UUID aggregateId,
    Instant occurredAt,
    UUID correlationId,
    UUID causationId,
    T payload) {

  public EventEnvelope {
    if (eventVersion < 1) {
      throw new IllegalArgumentException("eventVersion must be positive");
    }
  }
}
