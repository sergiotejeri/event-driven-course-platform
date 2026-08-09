package com.acme.courseplatform.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.acme.courseplatform.catalog.domain.Category;
import com.acme.courseplatform.catalog.domain.CategoryStatus;
import com.acme.courseplatform.catalog.domain.Course;
import com.acme.courseplatform.catalog.domain.CourseStatus;
import com.acme.courseplatform.catalog.domain.Instructor;
import com.acme.courseplatform.certificate.domain.Certificate;
import com.acme.courseplatform.enrollment.domain.Enrollment;
import com.acme.courseplatform.enrollment.domain.EnrollmentStatus;
import com.acme.courseplatform.enrollment.domain.Student;
import com.acme.courseplatform.payment.domain.Payment;
import com.acme.courseplatform.payment.domain.PaymentStatus;
import com.acme.courseplatform.shared.domain.InvalidTransitionException;
import com.acme.courseplatform.shared.domain.ProgressRegressionException;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class DomainInvariantTest {

  @Test
  void courseOnlyPublishesFromDraft() {
    Course course = Course.draft(UUID.randomUUID(), "Event-driven Java", 1);

    course.publish();

    assertThat(course.status()).isEqualTo(CourseStatus.PUBLISHED);
    assertThatThrownBy(course::publish).isInstanceOf(InvalidTransitionException.class);
  }

  @Test
  void archivedCourseCannotReturnToPublished() {
    Course course = Course.draft(UUID.randomUUID(), "Event-driven Java", 1);
    course.publish();
    course.archive();

    assertThat(course.status()).isEqualTo(CourseStatus.ARCHIVED);
    assertThatThrownBy(course::publish).isInstanceOf(InvalidTransitionException.class);
  }

  @Test
  void enrollmentProgressIsMonotonicAndCompletesAtOneHundred() {
    Enrollment enrollment = pendingEnrollment();
    enrollment.activate();
    enrollment.updateProgress(60);

    assertThatThrownBy(() -> enrollment.updateProgress(59))
        .isInstanceOf(ProgressRegressionException.class);

    boolean completedNow = enrollment.updateProgress(100);

    assertThat(completedNow).isTrue();
    assertThat(enrollment.status()).isEqualTo(EnrollmentStatus.COMPLETED);
    assertThat(enrollment.progress()).isEqualTo(100);
    assertThat(enrollment.updateProgress(100)).isFalse();
  }

  @Test
  void pendingEnrollmentCanCancelOnlyOnce() {
    Enrollment enrollment = pendingEnrollment();

    assertThat(enrollment.cancel()).isTrue();
    assertThat(enrollment.status()).isEqualTo(EnrollmentStatus.CANCELLED);
    assertThat(enrollment.cancel()).isFalse();
    assertThatThrownBy(enrollment::activate).isInstanceOf(InvalidTransitionException.class);
  }

  @Test
  void paymentReachesExactlyOneTerminalState() {
    Payment payment =
        Payment.pending(UUID.randomUUID(), UUID.randomUUID(), new BigDecimal("49.90"), "EUR");

    assertThat(payment.confirm()).isTrue();
    assertThat(payment.status()).isEqualTo(PaymentStatus.CONFIRMED);
    assertThat(payment.confirm()).isFalse();
    assertThatThrownBy(payment::fail).isInstanceOf(InvalidTransitionException.class);
  }

  @Test
  void certificateRequiresNonBlankVerificationCode() {
    assertThatThrownBy(() -> Certificate.issue(UUID.randomUUID(), UUID.randomUUID(), "  "))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void categoryArchivesOnlyOnce() {
    Category category = Category.active(UUID.randomUUID(), "Backend", "Reliable systems");

    assertThat(category.archive()).isTrue();
    assertThat(category.status()).isEqualTo(CategoryStatus.ARCHIVED);
    assertThat(category.archive()).isFalse();
  }

  @Test
  void peopleRequireNormalizedEmails() {
    assertThatThrownBy(
            () ->
                new Instructor(
                    UUID.randomUUID(), UUID.randomUUID(), "Ada", "ADA@example.test", "Bio"))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(
            () ->
                new Student(
                    UUID.randomUUID(), UUID.randomUUID(), "Ada", "Lovelace", "ada@example.test "))
        .isInstanceOf(IllegalArgumentException.class);
  }

  private static Enrollment pendingEnrollment() {
    return Enrollment.pending(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());
  }
}
