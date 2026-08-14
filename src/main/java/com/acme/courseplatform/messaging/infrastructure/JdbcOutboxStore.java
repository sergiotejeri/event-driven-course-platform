package com.acme.courseplatform.messaging.infrastructure;

import com.acme.courseplatform.messaging.application.port.OutboxStore;
import com.acme.courseplatform.messaging.domain.EventEnvelope;
import java.sql.Timestamp;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

@Repository
public class JdbcOutboxStore implements OutboxStore {

  private final JdbcTemplate jdbc;
  private final ObjectMapper json;

  public JdbcOutboxStore(JdbcTemplate jdbc, ObjectMapper json) {
    this.jdbc = jdbc;
    this.json = json;
  }

  @Override
  public void append(EventEnvelope<?> event) {
    jdbc.update(
        "insert into outbox_events(event_id,event_type,event_version,aggregate_type,aggregate_id,payload,correlation_id,causation_id,occurred_at) values (?,?,?,?,?,?::jsonb,?,?,?)",
        event.eventId(),
        event.eventType(),
        event.eventVersion(),
        event.aggregateType(),
        event.aggregateId(),
        json.writeValueAsString(event.payload()),
        event.correlationId(),
        event.causationId(),
        Timestamp.from(event.occurredAt()));
  }

  @Override
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public List<OutboxMessage> claimBatch(int batchSize) {
    return jdbc.query(
        "with candidates as (select event_id from outbox_events where published_at is null and available_at <= now() order by available_at,occurred_at for update skip locked limit ?) update outbox_events event set available_at = now() + interval '30 seconds' from candidates where event.event_id = candidates.event_id returning event.event_id,event.event_type,event.event_version,event.payload::text,event.correlation_id,event.causation_id,event.occurred_at",
        (result, row) ->
            new OutboxMessage(
                result.getObject("event_id", UUID.class),
                result.getString("event_type"),
                result.getInt("event_version"),
                result.getString("payload"),
                result.getObject("correlation_id", UUID.class),
                result.getObject("causation_id", UUID.class),
                result.getTimestamp("occurred_at").toInstant()),
        batchSize);
  }

  @Override
  public void markPublished(UUID eventId) {
    jdbc.update(
        "update outbox_events set published_at=now(),attempts=attempts+1,last_error=null where event_id=?",
        eventId);
  }

  @Override
  public void recordFailure(UUID eventId, String error) {
    String bounded =
        error == null
            ? "publisher confirm failed"
            : error.substring(0, Math.min(error.length(), 1900));
    jdbc.update(
        "update outbox_events set attempts=attempts+1,last_error=?,available_at=now()+interval '5 seconds' where event_id=?",
        bounded,
        eventId);
  }

  @Override
  public OutboxStats stats() {
    return jdbc.queryForObject(
        "select count(*) as pending,coalesce(extract(epoch from now()-min(occurred_at)),0)::bigint as oldest_seconds from outbox_events where published_at is null",
        (result, row) ->
            new OutboxStats(
                result.getLong("pending"), Duration.ofSeconds(result.getLong("oldest_seconds"))));
  }
}
