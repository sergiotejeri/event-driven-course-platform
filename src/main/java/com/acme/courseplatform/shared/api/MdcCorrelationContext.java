package com.acme.courseplatform.shared.api;

import com.acme.courseplatform.shared.application.CorrelationContext;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;

@Component
public class MdcCorrelationContext implements CorrelationContext {

  @Override
  public UUID currentId() {
    String value = MDC.get("correlationId");
    if (value == null || value.isBlank()) {
      return UUID.randomUUID();
    }
    try {
      return UUID.fromString(value);
    } catch (IllegalArgumentException exception) {
      return UUID.nameUUIDFromBytes(value.getBytes(StandardCharsets.UTF_8));
    }
  }
}
