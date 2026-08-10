package com.acme.courseplatform.identity.application;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

@Service
public class JwtService {

  private final JwtEncoder encoder;
  private final Duration tokenTtl;

  public JwtService(JwtEncoder encoder, @Value("${app.security.token-ttl}") Duration tokenTtl) {
    this.encoder = encoder;
    this.tokenTtl = tokenTtl;
  }

  public Token issue(UUID userId, String email, List<String> roles) {
    Instant issuedAt = Instant.now();
    Instant expiresAt = issuedAt.plus(tokenTtl);
    JwtClaimsSet claims =
        JwtClaimsSet.builder()
            .issuer("course-platform")
            .issuedAt(issuedAt)
            .expiresAt(expiresAt)
            .subject(userId.toString())
            .claim("email", email)
            .claim("roles", roles)
            .build();
    JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
    String value = encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    return new Token(value, expiresAt);
  }

  public record Token(String value, Instant expiresAt) {}
}
