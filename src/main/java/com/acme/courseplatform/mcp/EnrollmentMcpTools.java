package com.acme.courseplatform.mcp;

import com.acme.courseplatform.catalog.application.model.PageResult;
import com.acme.courseplatform.enrollment.application.CancelEnrollmentUseCase;
import com.acme.courseplatform.enrollment.application.EnrollStudentUseCase;
import com.acme.courseplatform.enrollment.application.EnrollmentQueryUseCase;
import com.acme.courseplatform.enrollment.application.UpdateProgressUseCase;
import com.acme.courseplatform.enrollment.application.model.EnrollmentResult;
import com.acme.courseplatform.enrollment.application.port.EnrollmentQueryStore.EnrollmentView;
import com.acme.courseplatform.enrollment.application.port.EnrollmentQueryStore.StudentCourseView;
import com.acme.courseplatform.enrollment.application.port.EnrollmentQueryStore.StudentEnrollmentView;
import java.util.UUID;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.stereotype.Component;

@Component
public class EnrollmentMcpTools {
  private final EnrollStudentUseCase enroll;
  private final EnrollmentQueryUseCase query;
  private final UpdateProgressUseCase progress;
  private final CancelEnrollmentUseCase cancel;
  private final McpCurrentActor actor;

  public EnrollmentMcpTools(
      EnrollStudentUseCase enroll,
      EnrollmentQueryUseCase query,
      UpdateProgressUseCase progress,
      CancelEnrollmentUseCase cancel,
      McpCurrentActor actor) {
    this.enroll = enroll;
    this.query = query;
    this.progress = progress;
    this.cancel = cancel;
    this.actor = actor;
  }

  @McpTool(name = "enroll_student", description = "Enroll the authenticated student idempotently")
  public EnrollmentResult enrollStudent(UUID courseId, String idempotencyKey) {
    return enroll.enroll(actor.current(), courseId, idempotencyKey);
  }

  @McpTool(name = "get_enrollment", description = "Get an owned enrollment")
  public EnrollmentView getEnrollment(UUID id) {
    return query.get(actor.current(), id);
  }

  @McpTool(name = "list_students_by_course", description = "List students of an owned course")
  public PageResult<StudentEnrollmentView> listStudentsByCourse(
      UUID courseId, int page, int size, String sort) {
    return query.studentsByCourse(actor.current(), courseId, page, size, sort);
  }

  @McpTool(name = "list_courses_by_student", description = "List courses of an owned student")
  public PageResult<StudentCourseView> listCoursesByStudent(
      UUID studentId, int page, int size, String sort) {
    return query.coursesByStudent(actor.current(), studentId, page, size, sort);
  }

  @McpTool(name = "update_enrollment_progress", description = "Update owned enrollment progress")
  public UpdateProgressUseCase.ProgressResult updateEnrollmentProgress(
      UUID enrollmentId, int value) {
    return progress.update(actor.current(), enrollmentId, value);
  }

  @McpTool(name = "cancel_enrollment", description = "Cancel an owned enrollment")
  public CancelEnrollmentUseCase.CancellationResult cancelEnrollment(UUID enrollmentId) {
    return cancel.cancel(actor.current(), enrollmentId);
  }
}
