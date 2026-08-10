package com.acme.courseplatform.identity.application;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LoginUseCase {

  private final JdbcTemplate jdbc;
  private final PasswordEncoder passwordEncoder;
  private final JwtService jwtService;

  public LoginUseCase(JdbcTemplate jdbc, PasswordEncoder passwordEncoder, JwtService jwtService) {
    this.jdbc = jdbc;
    this.passwordEncoder = passwordEncoder;
    this.jwtService = jwtService;
  }

  @Transactional(readOnly = true)
  public LoginResult login(String email, String password) {
    UserAccount account =
        jdbc
            .query(
                "select id,email,password_hash from users where email = ? and enabled = true",
                this::account,
                email.strip().toLowerCase())
            .stream()
            .findFirst()
            .orElseThrow(InvalidCredentialsException::new);
    if (!passwordEncoder.matches(password, account.passwordHash())) {
      throw new InvalidCredentialsException();
    }
    List<String> roles =
        jdbc.queryForList(
            "select role_name from user_roles where user_id = ? order by role_name",
            String.class,
            account.id());
    JwtService.Token token = jwtService.issue(account.id(), account.email(), roles);
    return new LoginResult(token.value(), token.expiresAt());
  }

  private UserAccount account(ResultSet result, int row) throws SQLException {
    return new UserAccount(
        result.getObject("id", UUID.class),
        result.getString("email"),
        result.getString("password_hash"));
  }

  private record UserAccount(UUID id, String email, String passwordHash) {}

  public record LoginResult(String token, Instant expiresAt) {}
}
