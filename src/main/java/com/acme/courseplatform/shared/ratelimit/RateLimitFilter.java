package com.acme.courseplatform.shared.ratelimit;

import com.acme.courseplatform.observability.BusinessMetrics;
import com.acme.courseplatform.shared.api.ApiProblemWriter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@Order(20)
@ConditionalOnProperty(name = "app.rate-limit.enabled", matchIfMissing = true)
public class RateLimitFilter extends OncePerRequestFilter {

  private final RedisTokenBucket limiter;
  private final ApiProblemWriter problems;
  private final BusinessMetrics metrics;

  public RateLimitFilter(
      RedisTokenBucket limiter, ApiProblemWriter problems, BusinessMetrics metrics) {
    this.limiter = limiter;
    this.problems = problems;
    this.metrics = metrics;
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
        response.setHeader("Retry-After", Long.toString(Math.max(1, decision.retryAfterSeconds())));
        metrics.rateLimited();
        problems.write(
            request,
            response,
            HttpStatus.TOO_MANY_REQUESTS,
            "RATE_LIMIT_EXCEEDED",
            "The request rate limit has been exceeded");
        return;
      }
    } catch (RuntimeException redisUnavailable) {
      metrics.rateLimitDegraded();
      if (policy.failClosed()) {
        problems.write(
            request,
            response,
            HttpStatus.SERVICE_UNAVAILABLE,
            "RATE_LIMIT_UNAVAILABLE",
            "Rate limiting is temporarily unavailable");
        return;
      }
      response.setHeader("X-RateLimit-Degraded", "fail-open");
    }
    chain.doFilter(request, response);
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
