package com.acme.courseplatform.identity.api;

import com.acme.courseplatform.identity.application.LoginUseCase;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import java.time.Instant;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

  private final LoginUseCase login;

  public AuthController(LoginUseCase login) {
    this.login = login;
  }

  @PostMapping("/login")
  @Operation(
      summary = "Authenticate a user",
      description = "Validates local credentials and returns a short-lived JWT bearer token")
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "Authentication successful"),
    @ApiResponse(responseCode = "400", description = "Invalid request body"),
    @ApiResponse(responseCode = "401", description = "Invalid credentials")
  })
  LoginResponse login(@Valid @RequestBody LoginRequest request) {
    LoginUseCase.LoginResult result = login.login(request.email(), request.password());
    return new LoginResponse(result.token(), "Bearer", result.expiresAt());
  }

  record LoginRequest(@NotBlank @Email String email, @NotBlank String password) {}

  record LoginResponse(String token, String tokenType, Instant expiresAt) {}
}
