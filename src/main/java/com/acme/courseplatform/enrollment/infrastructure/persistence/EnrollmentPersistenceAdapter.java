package com.acme.courseplatform.enrollment.infrastructure.persistence;

import com.acme.courseplatform.enrollment.application.port.EnrollmentRepository;
import java.util.UUID;
import org.springframework.stereotype.Repository;

@Repository
public class EnrollmentPersistenceAdapter implements EnrollmentRepository {

  private final SpringDataEnrollmentRepository enrollments;

  public EnrollmentPersistenceAdapter(SpringDataEnrollmentRepository enrollments) {
    this.enrollments = enrollments;
  }

  @Override
  public void savePendingPayment(UUID enrollmentId, UUID studentId, UUID courseId) {
    enrollments.saveAndFlush(EnrollmentJpaEntity.pendingPayment(enrollmentId, studentId, courseId));
  }
}
