package com.acme.courseplatform.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.acme.courseplatform.messaging.domain.EventEnvelope;
import com.acme.courseplatform.messaging.domain.Events.EnrollmentCompletedV1;
import com.acme.courseplatform.messaging.domain.Events.EnrollmentCreatedV1;
import com.acme.courseplatform.messaging.domain.Events.PaymentConfirmedV1;
import com.acme.courseplatform.messaging.domain.Events.PaymentFailedV1;
import com.acme.courseplatform.messaging.domain.Events.PaymentSimulationRequestedV1;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class EventContractTest {

  private final JsonMapper json = JsonMapper.builder().findAndAddModules().build();

  @Test
  void fiveVersionedEventsHaveStableJsonContracts() throws Exception {
    UUID eventId = UUID.fromString("10000000-0000-0000-0000-000000000001");
    UUID aggregateId = UUID.fromString("20000000-0000-0000-0000-000000000002");
    UUID correlationId = UUID.fromString("30000000-0000-0000-0000-000000000003");
    Instant occurredAt = Instant.parse("2026-08-07T12:00:00Z");
    UUID courseId = UUID.fromString("40000000-0000-0000-0000-000000000004");
    Object[] payloads = {
      new EnrollmentCreatedV1(aggregateId, UUID.randomUUID(), UUID.randomUUID()),
      new PaymentSimulationRequestedV1(UUID.randomUUID(), aggregateId, "CONFIRM"),
      new PaymentConfirmedV1(UUID.randomUUID(), aggregateId, new BigDecimal("49.90"), "EUR"),
      new PaymentFailedV1(UUID.randomUUID(), aggregateId, "DECLINED"),
      new EnrollmentCompletedV1(aggregateId, courseId, occurredAt)
    };

    for (Object payload : payloads) {
      String eventType = payload.getClass().getSimpleName();
      EventEnvelope<Object> envelope =
          new EventEnvelope<>(
              eventId,
              eventType,
              1,
              "Enrollment",
              aggregateId,
              occurredAt,
              correlationId,
              null,
              payload);

      String encoded = json.writeValueAsString(envelope);

      assertThat(encoded)
          .contains("\"eventId\":\"" + eventId + "\"")
          .contains("\"eventType\":\"" + eventType + "\"")
          .contains("\"eventVersion\":1")
          .contains("\"aggregateType\":\"Enrollment\"")
          .contains("\"correlationId\":\"" + correlationId + "\"")
          .contains("\"payload\":")
          .doesNotContain("JpaEntity");
      EventEnvelope<?> decoded = json.readValue(encoded, EventEnvelope.class);
      assertThat(decoded.eventId()).isEqualTo(eventId);
    }

    String completed = json.writeValueAsString(payloads[4]);
    assertThat(completed).contains("\"courseId\":\"" + courseId + "\"").doesNotContain("studentId");
  }

  @Test
  void eventVersionMustBePositive() {
    assertThatThrownBy(
            () ->
                new EventEnvelope<>(
                    UUID.randomUUID(),
                    "EnrollmentCreatedV1",
                    0,
                    "Enrollment",
                    UUID.randomUUID(),
                    Instant.now(),
                    UUID.randomUUID(),
                    null,
                    new Object()))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("eventVersion must be positive");
  }
}
