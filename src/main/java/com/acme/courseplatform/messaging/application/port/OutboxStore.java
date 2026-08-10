package com.acme.courseplatform.messaging.application.port;

import com.acme.courseplatform.messaging.domain.EventEnvelope;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface OutboxStore {

  void append(EventEnvelope<?> event);

  List<OutboxMessage> claimBatch(int batchSize);

  void markPublished(UUID eventId);

  void recordFailure(UUID eventId, String error);

  record OutboxMessage(
      UUID eventId,
      String eventType,
      int eventVersion,
      String payload,
      UUID correlationId,
      UUID causationId,
      Instant occurredAt) {}
}
