package com.acme.courseplatform.messaging.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.acme.courseplatform.messaging.application.port.OutboxStore;
import com.acme.courseplatform.messaging.application.port.OutboxStore.OutboxMessage;
import com.acme.courseplatform.messaging.application.port.OutboxStore.OutboxStats;
import com.acme.courseplatform.messaging.infrastructure.OutboxPublisher;
import com.acme.courseplatform.messaging.infrastructure.OutboxPublisher.PublishResult;
import com.acme.courseplatform.observability.BusinessMetrics;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PublishOutboxBatchUseCaseTest {

  private final OutboxStore store = mock(OutboxStore.class);
  private final OutboxPublisher publisher = mock(OutboxPublisher.class);
  private final BusinessMetrics metrics = mock(BusinessMetrics.class);
  private final PublishOutboxBatchUseCase useCase =
      new PublishOutboxBatchUseCase(store, publisher, metrics);

  @Test
  void marksPublishedOnlyAfterBrokerAcknowledgement() {
    OutboxMessage message = message();
    when(store.claimBatch(10)).thenReturn(List.of(message));
    when(store.stats()).thenReturn(new OutboxStats(0, Duration.ZERO));
    when(publisher.publish(message)).thenReturn(PublishResult.ack());

    assertThat(useCase.publishBatch(10)).isEqualTo(1);

    verify(store).markPublished(message.eventId());
    verify(store, never()).recordFailure(any(), anyString());
    verify(metrics).outboxPublished(1);
  }

  @Test
  void recordsFailureAfterBrokerRejection() {
    OutboxMessage message = message();
    when(store.claimBatch(10)).thenReturn(List.of(message));
    when(store.stats()).thenReturn(new OutboxStats(1, Duration.ofSeconds(5)));
    when(publisher.publish(message)).thenReturn(PublishResult.rejected("broker nack"));

    assertThat(useCase.publishBatch(10)).isZero();

    verify(store, never()).markPublished(message.eventId());
    verify(store).recordFailure(message.eventId(), "broker nack");
    verify(metrics).outboxFailed();
  }

  @Test
  void refreshesOutboxGaugesAfterPublishingBatch() {
    when(store.claimBatch(10)).thenReturn(List.of());
    when(store.stats()).thenReturn(new OutboxStats(3, Duration.ofSeconds(42)));

    useCase.publishBatch(10);

    verify(metrics).updateOutboxGauges(3, Duration.ofSeconds(42));
  }

  private OutboxMessage message() {
    return new OutboxMessage(
        UUID.randomUUID(),
        "EnrollmentCreatedV1",
        1,
        "{}",
        UUID.randomUUID(),
        null,
        Instant.parse("2026-08-08T10:00:00Z"));
  }
}
