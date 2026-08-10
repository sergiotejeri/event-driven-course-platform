package com.acme.courseplatform.shared.api;

import com.acme.courseplatform.shared.domain.InvalidTransitionException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;

@RestControllerAdvice
public class GlobalExceptionHandler {

  @ExceptionHandler(ResourceNotFoundException.class)
  ResponseEntity<ProblemDetail> notFound(
      ResourceNotFoundException exception, HttpServletRequest request) {
    return response(
        HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND", exception.getMessage(), request, Map.of());
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  ResponseEntity<ProblemDetail> invalidBody(
      MethodArgumentNotValidException exception, HttpServletRequest request) {
    Map<String, String> fields = new LinkedHashMap<>();
    exception
        .getBindingResult()
        .getFieldErrors()
        .forEach(error -> fields.putIfAbsent(error.getField(), error.getDefaultMessage()));
    return response(
        HttpStatus.BAD_REQUEST,
        "VALIDATION_FAILED",
        "Request validation failed",
        request,
        Map.of("fieldErrors", fields));
  }

  @ExceptionHandler({IllegalArgumentException.class, ConstraintViolationException.class})
  ResponseEntity<ProblemDetail> invalidRequest(Exception exception, HttpServletRequest request) {
    return response(
        HttpStatus.BAD_REQUEST, "INVALID_REQUEST", exception.getMessage(), request, Map.of());
  }

  @ExceptionHandler(HandlerMethodValidationException.class)
  ResponseEntity<ProblemDetail> invalidMethodArguments(
      HandlerMethodValidationException exception, HttpServletRequest request) {
    return response(
        HttpStatus.BAD_REQUEST,
        "INVALID_REQUEST",
        "Request parameters are invalid",
        request,
        Map.of());
  }

  @ExceptionHandler(ConflictException.class)
  ResponseEntity<ProblemDetail> conflict(ConflictException exception, HttpServletRequest request) {
    return response(
        HttpStatus.CONFLICT, exception.errorCode(), exception.getMessage(), request, Map.of());
  }

  @ExceptionHandler(InvalidTransitionException.class)
  ResponseEntity<ProblemDetail> invalidTransition(
      InvalidTransitionException exception, HttpServletRequest request) {
    return response(
        HttpStatus.CONFLICT, "INVALID_STATE_TRANSITION", exception.getMessage(), request, Map.of());
  }

  @ExceptionHandler(DataIntegrityViolationException.class)
  ResponseEntity<ProblemDetail> integrity(
      DataIntegrityViolationException exception, HttpServletRequest request) {
    return response(
        HttpStatus.CONFLICT,
        "DATA_INTEGRITY_CONFLICT",
        "The operation conflicts with existing data",
        request,
        Map.of());
  }

  private ResponseEntity<ProblemDetail> response(
      HttpStatus status,
      String errorCode,
      String detail,
      HttpServletRequest request,
      Map<String, Object> properties) {
    ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
    problem.setTitle(status.getReasonPhrase());
    problem.setProperty("errorCode", errorCode);
    problem.setProperty("correlationId", correlationId(request));
    problem.setProperty("timestamp", Instant.now());
    properties.forEach(problem::setProperty);
    return ResponseEntity.status(status).body(problem);
  }

  private String correlationId(HttpServletRequest request) {
    Object value = request.getAttribute(CorrelationIdFilter.ATTRIBUTE);
    return value == null ? java.util.UUID.randomUUID().toString() : value.toString();
  }
}
