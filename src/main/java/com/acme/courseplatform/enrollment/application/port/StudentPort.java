package com.acme.courseplatform.enrollment.application.port;

import java.util.Optional;
import java.util.UUID;

public interface StudentPort {

  Optional<UUID> findStudentId(UUID userId);
}
