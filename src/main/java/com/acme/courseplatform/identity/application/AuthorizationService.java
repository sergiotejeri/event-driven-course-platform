package com.acme.courseplatform.identity.application;

import java.util.Set;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;

@Service
public class AuthorizationService {

  private final JdbcTemplate jdbc;

  public AuthorizationService(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public void requireCourseInstructorOrAdmin(Authentication authentication, UUID instructorId) {
    requireCourseInstructorOrAdmin(actor(authentication), instructorId);
  }

  public void requireCourseInstructorOrAdmin(CurrentActor actor, UUID instructorId) {
    if (actor.hasRole("ADMIN")) {
      return;
    }
    Boolean ownsInstructor =
        jdbc.queryForObject(
            "select exists(select 1 from instructors where id = ? and user_id = ?)",
            Boolean.class,
            instructorId,
            actor.userId());
    if (!Boolean.TRUE.equals(ownsInstructor)) {
      throw new AccessDeniedException("The resource belongs to another user");
    }
  }

  public void requireAdmin(CurrentActor actor) {
    if (!actor.hasRole("ADMIN")) {
      throw new AccessDeniedException("Administrator role required");
    }
  }

  public void requireCourseOwnerOrAdmin(Authentication authentication, UUID courseId) {
    requireCourseOwnerOrAdmin(actor(authentication), courseId);
  }

  public void requireCourseOwnerOrAdmin(CurrentActor actor, UUID courseId) {
    if (actor.hasRole("ADMIN")) {
      return;
    }
    Boolean ownsCourse =
        jdbc.queryForObject(
            "select exists(select 1 from courses c join instructors i on i.id = c.instructor_id where c.id = ? and i.user_id = ?)",
            Boolean.class,
            courseId,
            actor.userId());
    if (!Boolean.TRUE.equals(ownsCourse)) {
      throw new AccessDeniedException("The resource belongs to another user");
    }
  }

  public void requireStudentOwnerOrAdmin(CurrentActor actor, UUID studentId) {
    if (actor.hasRole("ADMIN")) {
      return;
    }
    Boolean ownsStudent =
        jdbc.queryForObject(
            "select exists(select 1 from students where id=? and user_id=?)",
            Boolean.class,
            studentId,
            actor.userId());
    if (!Boolean.TRUE.equals(ownsStudent)) {
      throw new AccessDeniedException("The resource belongs to another user");
    }
  }

  public void requirePaymentOwnerOrAdmin(CurrentActor actor, UUID paymentId) {
    if (actor.hasRole("ADMIN")) {
      return;
    }
    Boolean ownsPayment =
        jdbc.queryForObject(
            "select exists(select 1 from payments p join enrollments e on e.id = p.enrollment_id join students s on s.id = e.student_id where p.id = ? and s.user_id = ?)",
            Boolean.class,
            paymentId,
            actor.userId());
    if (!Boolean.TRUE.equals(ownsPayment)) {
      throw new AccessDeniedException("The resource belongs to another user");
    }
  }

  public void requireEnrollmentOwnerOrAdmin(CurrentActor actor, UUID enrollmentId) {
    if (actor.hasRole("ADMIN")) {
      return;
    }
    Boolean ownsEnrollment =
        jdbc.queryForObject(
            "select exists(select 1 from enrollments e join students s on s.id=e.student_id where e.id=? and s.user_id=?)",
            Boolean.class,
            enrollmentId,
            actor.userId());
    if (!Boolean.TRUE.equals(ownsEnrollment)) {
      throw new AccessDeniedException("The resource belongs to another user");
    }
  }

  public CurrentActor actor(Authentication authentication) {
    if (authentication == null || !(authentication.getPrincipal() instanceof Jwt jwt)) {
      throw new AccessDeniedException("Authenticated actor required");
    }
    Set<String> roles = Set.copyOf(jwt.getClaimAsStringList("roles"));
    return new CurrentActor(UUID.fromString(jwt.getSubject()), roles);
  }
}
