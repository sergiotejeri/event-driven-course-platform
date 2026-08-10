package com.acme.courseplatform.enrollment.api;

import com.acme.courseplatform.enrollment.application.EnrollStudentUseCase;
import com.acme.courseplatform.enrollment.application.model.EnrollmentResult;
import com.acme.courseplatform.identity.application.AuthorizationService;
import java.net.URI;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/courses")
public class EnrollmentController {

  private final EnrollStudentUseCase enroll;
  private final AuthorizationService authorization;

  public EnrollmentController(EnrollStudentUseCase enroll, AuthorizationService authorization) {
    this.enroll = enroll;
    this.authorization = authorization;
  }

  @PostMapping("/{courseId}/enrollments")
  ResponseEntity<EnrollmentResponse> enroll(
      @PathVariable UUID courseId,
      @RequestHeader("Idempotency-Key") String idempotencyKey,
      Authentication authentication) {
    EnrollmentResult result =
        enroll.enroll(authorization.actor(authentication), courseId, idempotencyKey);
    EnrollmentResponse response =
        new EnrollmentResponse(result.enrollmentId(), result.paymentId(), result.replayed());
    if (result.replayed()) {
      return ResponseEntity.ok(response);
    }
    return ResponseEntity.created(URI.create("/api/v1/enrollments/" + result.enrollmentId()))
        .body(response);
  }

  record EnrollmentResponse(UUID enrollmentId, UUID paymentId, boolean replayed) {}
}
