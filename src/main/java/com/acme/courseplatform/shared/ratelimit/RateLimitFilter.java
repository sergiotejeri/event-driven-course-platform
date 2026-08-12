package com.acme.courseplatform.shared.ratelimit;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@Order(20)
@ConditionalOnProperty(name = "app.rate-limit.enabled", matchIfMissing = true)
public class RateLimitFilter extends OncePerRequestFilter {

  private final RedisTokenBucket limiter;

  public RateLimitFilter(RedisTokenBucket limiter) {
    this.limiter = limiter;
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    Policy policy = policy(request);
    if (policy == null) {
      chain.doFilter(request, response);
      return;
    }
    String actor =
        request.getUserPrincipal() == null
            ? request.getRemoteAddr()
            : request.getUserPrincipal().getName();
    try {
      RedisTokenBucket.Decision decision =
          limiter.consume(policy.name() + ":" + actor, policy.capacity(), policy.windowSeconds());
      response.setHeader("X-RateLimit-Limit", Integer.toString(policy.capacity()));
      response.setHeader("X-RateLimit-Remaining", Long.toString(decision.remaining()));
      if (!decision.allowed()) {
        response.setStatus(429);
        response.setHeader("Retry-After", Long.toString(Math.max(1, decision.retryAfterSeconds())));
        writeProblem(response, "RATE_LIMIT_EXCEEDED", 429);
        return;
      }
    } catch (RuntimeException redisUnavailable) {
      if (policy.failClosed()) {
        response.setStatus(503);
        writeProblem(response, "RATE_LIMIT_UNAVAILABLE", 503);
        return;
      }
      response.setHeader("X-RateLimit-Degraded", "fail-open");
    }
    chain.doFilter(request, response);
  }

  private static void writeProblem(HttpServletResponse response, String errorCode, int status)
      throws IOException {
    response.setContentType("application/problem+json");
    response
        .getOutputStream()
        .write(
            ("{\"errorCode\":\"" + errorCode + "\",\"status\":" + status + "}")
                .getBytes(StandardCharsets.UTF_8));
  }

  private static Policy policy(HttpServletRequest request) {
    String path = request.getRequestURI();
    if (path.equals("/api/v1/auth/login")) {
      return new Policy("login", 5, 60, true);
    }
    if (path.matches("/api/v1/payments/[^/]+/simulate")) {
      return new Policy("payment", 10, 60, true);
    }
    if (path.startsWith("/api/")) {
      return new Policy("api", 100, 60, false);
    }
    return null;
  }

  record Policy(String name, int capacity, int windowSeconds, boolean failClosed) {}
}
