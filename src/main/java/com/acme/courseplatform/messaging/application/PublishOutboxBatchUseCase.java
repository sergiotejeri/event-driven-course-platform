package com.acme.courseplatform.messaging.application;

import com.acme.courseplatform.messaging.application.port.OutboxStore;
import com.acme.courseplatform.messaging.infrastructure.OutboxPublisher;
import com.acme.courseplatform.observability.BusinessMetrics;
import org.springframework.stereotype.Service;

@Service
public class PublishOutboxBatchUseCase {

  private final OutboxStore store;
  private final OutboxPublisher publisher;
  private final BusinessMetrics metrics;

  public PublishOutboxBatchUseCase(
      OutboxStore store, OutboxPublisher publisher, BusinessMetrics metrics) {
    this.store = store;
    this.publisher = publisher;
    this.metrics = metrics;
  }

  public int publishBatch(int batchSize) {
    int published = 0;
    for (var message : store.claimBatch(batchSize)) {
      var result = publisher.publish(message);
      if (result.acknowledged()) {
        store.markPublished(message.eventId());
        published++;
      } else {
        metrics.outboxFailed();
        store.recordFailure(message.eventId(), result.error());
      }
    }
    metrics.outboxPublished(published);
    return published;
  }
}
