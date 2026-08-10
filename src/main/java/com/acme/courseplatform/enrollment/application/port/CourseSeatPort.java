package com.acme.courseplatform.enrollment.application.port;

import com.acme.courseplatform.enrollment.application.model.ReservedCourse;
import java.util.Optional;
import java.util.UUID;

public interface CourseSeatPort {

  Optional<ReservedCourse> reserve(UUID courseId);
}
