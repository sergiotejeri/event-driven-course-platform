package com.acme.courseplatform.enrollment.application.port;

import com.acme.courseplatform.catalog.application.model.PageResult;
import com.acme.courseplatform.shared.query.SortSpec;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface EnrollmentQueryStore {

  Optional<EnrollmentView> findById(UUID enrollmentId);

  PageResult<StudentEnrollmentView> findStudentsByCourse(
      UUID courseId, int page, int size, SortSpec sort);

  PageResult<StudentCourseView> findCoursesByStudent(
      UUID studentId, int page, int size, SortSpec sort);

  record EnrollmentView(
      UUID id,
      UUID studentId,
      UUID courseId,
      String status,
      int progress,
      Instant enrolledAt,
      Instant completedAt,
      Instant cancelledAt) {}

  record StudentEnrollmentView(
      UUID studentId,
      String firstName,
      String lastName,
      String email,
      UUID enrollmentId,
      String status,
      int progress) {}

  record StudentCourseView(
      UUID courseId,
      String title,
      String level,
      BigDecimal price,
      String currency,
      UUID enrollmentId,
      String status,
      int progress,
      Instant enrolledAt) {}
}
