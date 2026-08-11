package com.acme.courseplatform.enrollment.application.port;

import java.util.UUID;

public interface EnrollmentTransactionStore {

  boolean activatePendingPayment(UUID enrollmentId);

  boolean cancelPendingPayment(UUID enrollmentId);
}
