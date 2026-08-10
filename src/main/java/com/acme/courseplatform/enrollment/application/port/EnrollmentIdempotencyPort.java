package com.acme.courseplatform.enrollment.application.port;

import com.acme.courseplatform.enrollment.application.model.EnrollmentResult;
import java.util.Optional;
import java.util.UUID;

public interface EnrollmentIdempotencyPort {

  Optional<Replay> find(UUID actorId, String key);

  boolean start(UUID actorId, String key, String requestHash);

  void complete(UUID actorId, String key, EnrollmentResult result);

  record Replay(String requestHash, UUID enrollmentId, UUID paymentId, boolean completed) {}
}
