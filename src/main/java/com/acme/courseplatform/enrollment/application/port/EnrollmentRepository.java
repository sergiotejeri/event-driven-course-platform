package com.acme.courseplatform.enrollment.application.port;

import java.util.UUID;

public interface EnrollmentRepository {

  void savePendingPayment(UUID enrollmentId, UUID studentId, UUID courseId);
}
