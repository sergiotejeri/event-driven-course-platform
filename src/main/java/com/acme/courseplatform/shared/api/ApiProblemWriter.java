package com.acme.courseplatform.shared.api;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

@Component
public class ApiProblemWriter {

  private final ObjectMapper json;

  public ApiProblemWriter(ObjectMapper json) {
    this.json = json;
  }

  public void write(
      HttpServletRequest request,
      HttpServletResponse response,
      HttpStatus status,
      String errorCode,
      String detail)
      throws IOException {
    String correlationId = correlationId(request);
    response.setStatus(status.value());
    response.setContentType("application/problem+json");
    response.setHeader(CorrelationIdFilter.HEADER, correlationId);
    Map<String, Object> problem = new LinkedHashMap<>();
    problem.put("title", status.getReasonPhrase());
    problem.put("status", status.value());
    problem.put("detail", detail);
    problem.put("instance", request.getRequestURI());
    problem.put("errorCode", errorCode);
    problem.put("correlationId", correlationId);
    problem.put("timestamp", Instant.now());
    json.writeValue(response.getOutputStream(), problem);
  }

  private static String correlationId(HttpServletRequest request) {
    Object value = request.getAttribute(CorrelationIdFilter.ATTRIBUTE);
    return value == null ? UUID.randomUUID().toString() : value.toString();
  }
}
