package com.acme.courseplatform.enrollment.api;

import com.acme.courseplatform.enrollment.application.UpdateProgressUseCase;
import com.acme.courseplatform.identity.application.AuthorizationService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/enrollments")
public class ProgressController {

  private final UpdateProgressUseCase progress;
  private final AuthorizationService authorization;

  public ProgressController(UpdateProgressUseCase progress, AuthorizationService authorization) {
    this.progress = progress;
    this.authorization = authorization;
  }

  @PatchMapping("/{id}/progress")
  UpdateProgressUseCase.ProgressResult update(
      Authentication authentication,
      @PathVariable UUID id,
      @Valid @RequestBody ProgressRequest request) {
    return progress.update(authorization.actor(authentication), id, request.progress());
  }

  record ProgressRequest(@Min(0) @Max(100) int progress) {}
}
