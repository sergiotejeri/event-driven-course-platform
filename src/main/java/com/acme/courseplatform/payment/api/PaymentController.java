package com.acme.courseplatform.payment.api;

import com.acme.courseplatform.identity.application.AuthorizationService;
import com.acme.courseplatform.payment.application.RequestPaymentSimulationUseCase;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/payments")
public class PaymentController {

  private final RequestPaymentSimulationUseCase simulation;
  private final AuthorizationService authorization;

  public PaymentController(
      RequestPaymentSimulationUseCase simulation, AuthorizationService authorization) {
    this.simulation = simulation;
    this.authorization = authorization;
  }

  @PostMapping("/{id}/simulate")
  ResponseEntity<Map<String, Object>> simulate(
      Authentication authentication,
      @PathVariable UUID id,
      @Valid @RequestBody SimulationRequest request) {
    UUID eventId = simulation.request(authorization.actor(authentication), id, request.outcome());
    return ResponseEntity.accepted().body(Map.of("eventId", eventId, "status", "ACCEPTED"));
  }

  record SimulationRequest(@Pattern(regexp = "CONFIRM|FAIL") String outcome) {}
}
