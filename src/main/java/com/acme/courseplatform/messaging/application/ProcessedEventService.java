package com.acme.courseplatform.messaging.application;

import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class ProcessedEventService {

  private final JdbcTemplate jdbc;

  public ProcessedEventService(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public boolean claim(String consumer, UUID eventId) {
    return jdbc.update(
            "insert into processed_events(consumer_name,event_id) values (?,?) on conflict do nothing",
            consumer,
            eventId)
        == 1;
  }
}
