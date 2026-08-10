package com.acme.courseplatform.enrollment.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "enrollments")
public class EnrollmentJpaEntity {

  @Id private UUID id;

  @Column(name = "student_id")
  private UUID studentId;

  @Column(name = "course_id")
  private UUID courseId;

  private String status;
  private int progress;

  @Column(name = "enrolled_at")
  private Instant enrolledAt;

  @Column(name = "updated_at")
  private Instant updatedAt;

  protected EnrollmentJpaEntity() {}

  private EnrollmentJpaEntity(UUID id, UUID studentId, UUID courseId) {
    this.id = id;
    this.studentId = studentId;
    this.courseId = courseId;
    this.status = "PENDING_PAYMENT";
    this.progress = 0;
    this.enrolledAt = Instant.now();
    this.updatedAt = enrolledAt;
  }

  public static EnrollmentJpaEntity pendingPayment(UUID id, UUID studentId, UUID courseId) {
    return new EnrollmentJpaEntity(id, studentId, courseId);
  }
}
