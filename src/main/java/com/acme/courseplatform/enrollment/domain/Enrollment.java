package com.acme.courseplatform.enrollment.domain;

import com.acme.courseplatform.shared.domain.InvalidTransitionException;
import com.acme.courseplatform.shared.domain.ProgressRegressionException;
import java.util.UUID;

public class Enrollment {

  private final UUID id;
  private final UUID studentId;
  private final UUID courseId;
  private EnrollmentStatus status;
  private int progress;

  private Enrollment(UUID id, UUID studentId, UUID courseId) {
    this.id = id;
    this.studentId = studentId;
    this.courseId = courseId;
    this.status = EnrollmentStatus.PENDING;
  }

  public static Enrollment pending(UUID id, UUID studentId, UUID courseId) {
    return new Enrollment(id, studentId, courseId);
  }

  public void activate() {
    if (status != EnrollmentStatus.PENDING) {
      throw new InvalidTransitionException("Enrollment can only be activated from PENDING");
    }
    status = EnrollmentStatus.ACTIVE;
  }

  public boolean cancel() {
    if (status == EnrollmentStatus.CANCELLED) {
      return false;
    }
    if (status != EnrollmentStatus.PENDING) {
      throw new InvalidTransitionException("Enrollment can only be cancelled from PENDING");
    }
    status = EnrollmentStatus.CANCELLED;
    return true;
  }

  public boolean updateProgress(int newProgress) {
    if (newProgress < progress) {
      throw new ProgressRegressionException("Enrollment progress cannot decrease");
    }
    if (status == EnrollmentStatus.COMPLETED && newProgress == progress) {
      return false;
    }
    if (status != EnrollmentStatus.ACTIVE) {
      throw new InvalidTransitionException("Progress requires an ACTIVE enrollment");
    }

    progress = newProgress;
    if (progress == 100) {
      status = EnrollmentStatus.COMPLETED;
      return true;
    }
    return false;
  }

  public EnrollmentStatus status() {
    return status;
  }

  public int progress() {
    return progress;
  }
}
