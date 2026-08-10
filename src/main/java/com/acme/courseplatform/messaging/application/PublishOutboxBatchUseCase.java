package com.acme.courseplatform.messaging.application;

import com.acme.courseplatform.messaging.application.port.OutboxStore;
import com.acme.courseplatform.messaging.infrastructure.OutboxPublisher;
import org.springframework.stereotype.Service;

@Service
public class PublishOutboxBatchUseCase {

  private final OutboxStore store;
  private final OutboxPublisher publisher;

  public PublishOutboxBatchUseCase(OutboxStore store, OutboxPublisher publisher) {
    this.store = store;
    this.publisher = publisher;
  }

  public int publishBatch(int batchSize) {
    int published = 0;
    for (var message : store.claimBatch(batchSize)) {
      var result = publisher.publish(message);
      if (result.acknowledged()) {
        store.markPublished(message.eventId());
        published++;
      } else {
        store.recordFailure(message.eventId(), result.error());
      }
    }
    return published;
  }
}
