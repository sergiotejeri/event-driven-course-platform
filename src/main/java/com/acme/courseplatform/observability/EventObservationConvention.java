package com.acme.courseplatform.observability;

import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import org.springframework.stereotype.Component;

@Component
public class EventObservationConvention {
  private final ObservationRegistry registry;

  public EventObservationConvention(ObservationRegistry registry) {
    this.registry = registry;
  }

  public Observation start(String eventType, String operation) {
    return Observation.createNotStarted("course.platform.event", registry)
        .lowCardinalityKeyValue("event.type", eventType)
        .lowCardinalityKeyValue("messaging.operation", operation)
        .start();
  }
}
