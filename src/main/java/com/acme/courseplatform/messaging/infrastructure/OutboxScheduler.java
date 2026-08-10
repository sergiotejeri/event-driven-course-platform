package com.acme.courseplatform.messaging.infrastructure;

import com.acme.courseplatform.messaging.application.PublishOutboxBatchUseCase;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "app.outbox.scheduling-enabled", havingValue = "true")
public class OutboxScheduler {

  private final PublishOutboxBatchUseCase outbox;

  public OutboxScheduler(PublishOutboxBatchUseCase outbox) {
    this.outbox = outbox;
  }

  @Scheduled(fixedDelayString = "${app.outbox.fixed-delay:PT1S}")
  public void publish() {
    outbox.publishBatch(100);
  }
}
