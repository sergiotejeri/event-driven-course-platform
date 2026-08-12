package com.acme.courseplatform.enrollment.api;

import com.acme.courseplatform.catalog.application.model.PageResult;
import com.acme.courseplatform.enrollment.application.CancelEnrollmentUseCase;
import com.acme.courseplatform.enrollment.application.EnrollStudentUseCase;
import com.acme.courseplatform.enrollment.application.EnrollmentQueryUseCase;
import com.acme.courseplatform.enrollment.application.model.EnrollmentResult;
import com.acme.courseplatform.enrollment.application.port.EnrollmentQueryStore.EnrollmentView;
import com.acme.courseplatform.enrollment.application.port.EnrollmentQueryStore.StudentCourseView;
import com.acme.courseplatform.enrollment.application.port.EnrollmentQueryStore.StudentEnrollmentView;
import com.acme.courseplatform.identity.application.AuthorizationService;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import java.net.URI;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class EnrollmentController {

  private final EnrollStudentUseCase enroll;
  private final CancelEnrollmentUseCase cancellation;
  private final AuthorizationService authorization;
  private final EnrollmentQueryUseCase queries;

  public EnrollmentController(
      EnrollStudentUseCase enroll,
      CancelEnrollmentUseCase cancellation,
      AuthorizationService authorization,
      EnrollmentQueryUseCase queries) {
    this.enroll = enroll;
    this.cancellation = cancellation;
    this.authorization = authorization;
    this.queries = queries;
  }

  @PostMapping("/courses/{courseId}/enrollments")
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

  @DeleteMapping("/enrollments/{id}")
  CancelEnrollmentUseCase.CancellationResult cancel(
      @PathVariable UUID id, Authentication authentication) {
    return cancellation.cancel(authorization.actor(authentication), id);
  }

  @GetMapping("/enrollments/{id}")
  EnrollmentView get(@PathVariable UUID id, Authentication authentication) {
    return queries.get(authorization.actor(authentication), id);
  }

  @GetMapping("/courses/{courseId}/students")
  PageResult<StudentEnrollmentView> studentsByCourse(
      @PathVariable UUID courseId,
      @RequestParam(defaultValue = "0") @Min(0) int page,
      @RequestParam(defaultValue = "20") @Positive int size,
      @RequestParam(required = false) String sort,
      Authentication authentication) {
    return queries.studentsByCourse(
        authorization.actor(authentication), courseId, page, size, sort);
  }

  @GetMapping("/students/{studentId}/courses")
  PageResult<StudentCourseView> coursesByStudent(
      @PathVariable UUID studentId,
      @RequestParam(defaultValue = "0") @Min(0) int page,
      @RequestParam(defaultValue = "20") @Positive int size,
      @RequestParam(required = false) String sort,
      Authentication authentication) {
    return queries.coursesByStudent(
        authorization.actor(authentication), studentId, page, size, sort);
  }

  record EnrollmentResponse(UUID enrollmentId, UUID paymentId, boolean replayed) {}
}
