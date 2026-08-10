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
    CurrentActor actor = actor(authentication);
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

  public void requireCourseOwnerOrAdmin(Authentication authentication, UUID courseId) {
    CurrentActor actor = actor(authentication);
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

  public CurrentActor actor(Authentication authentication) {
    if (authentication == null || !(authentication.getPrincipal() instanceof Jwt jwt)) {
      throw new AccessDeniedException("Authenticated actor required");
    }
    Set<String> roles = Set.copyOf(jwt.getClaimAsStringList("roles"));
    return new CurrentActor(UUID.fromString(jwt.getSubject()), roles);
  }
}
