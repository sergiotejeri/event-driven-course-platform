package com.acme.courseplatform.enrollment.application;

import com.acme.courseplatform.catalog.application.model.PageResult;
import com.acme.courseplatform.enrollment.application.port.EnrollmentQueryStore;
import com.acme.courseplatform.enrollment.application.port.EnrollmentQueryStore.EnrollmentView;
import com.acme.courseplatform.enrollment.application.port.EnrollmentQueryStore.StudentCourseView;
import com.acme.courseplatform.enrollment.application.port.EnrollmentQueryStore.StudentEnrollmentView;
import com.acme.courseplatform.identity.application.AuthorizationService;
import com.acme.courseplatform.identity.application.CurrentActor;
import com.acme.courseplatform.shared.api.ResourceNotFoundException;
import com.acme.courseplatform.shared.query.SortDirection;
import com.acme.courseplatform.shared.query.SortSpec;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EnrollmentQueryUseCase {

  private final EnrollmentQueryStore queries;
  private final AuthorizationService authorization;

  public EnrollmentQueryUseCase(EnrollmentQueryStore queries, AuthorizationService authorization) {
    this.queries = queries;
    this.authorization = authorization;
  }

  @Transactional(readOnly = true)
  public EnrollmentView get(CurrentActor actor, UUID enrollmentId) {
    authorization.requireEnrollmentOwnerOrAdmin(actor, enrollmentId);
    return queries
        .findById(enrollmentId)
        .orElseThrow(() -> new ResourceNotFoundException("enrollment", enrollmentId));
  }

  @Transactional(readOnly = true)
  public PageResult<StudentEnrollmentView> studentsByCourse(
      CurrentActor actor, UUID courseId, int page, int size, String sort) {
    validatePage(page, size);
    authorization.requireCourseOwnerOrAdmin(actor, courseId);
    SortSpec order =
        SortSpec.parse(
            sort,
            Map.of(
                "firstName", "s.first_name",
                "lastName", "s.last_name",
                "registeredAt", "s.registered_at",
                "progress", "e.progress"),
            "lastName",
            SortDirection.ASC,
            "s.id");
    return queries.findStudentsByCourse(courseId, page, size, order);
  }

  @Transactional(readOnly = true)
  public PageResult<StudentCourseView> coursesByStudent(
      CurrentActor actor, UUID studentId, int page, int size, String sort) {
    validatePage(page, size);
    authorization.requireStudentOwnerOrAdmin(actor, studentId);
    SortSpec order =
        SortSpec.parse(
            sort,
            Map.of(
                "enrolledAt", "e.enrolled_at",
                "title", "c.title",
                "progress", "e.progress"),
            "enrolledAt",
            SortDirection.DESC,
            "e.id");
    return queries.findCoursesByStudent(studentId, page, size, order);
  }

  private void validatePage(int page, int size) {
    if (page < 0 || size < 1 || size > 100) {
      throw new IllegalArgumentException("Invalid page or size");
    }
  }
}
