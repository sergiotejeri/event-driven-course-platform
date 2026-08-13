package com.acme.courseplatform.observability;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.stereotype.Component;

@Component
public class BusinessMetrics {
  private final Counter enrollmentAttempts;
  private final Counter enrollmentSuccess;
  private final Counter enrollmentRejected;
  private final Counter outboxPublished;
  private final Counter outboxFailed;
  private final Counter paymentConfirmed;
  private final Counter paymentFailed;
  private final Counter certificatesIssued;
  private final Counter rateLimited;
  private final Counter rateLimitDegraded;
  private final Counter cacheHits;
  private final Counter cacheMisses;
  private final Counter cacheErrors;
  private final Timer enrollmentLatency;
  private final AtomicLong pendingOutbox = new AtomicLong();
  private final AtomicLong oldestOutboxSeconds = new AtomicLong();

  public BusinessMetrics(MeterRegistry registry) {
    enrollmentAttempts = registry.counter("course_platform_enrollment_attempts_total");
    enrollmentSuccess = registry.counter("course_platform_enrollment_success_total");
    enrollmentRejected = registry.counter("course_platform_enrollment_rejected_total");
    outboxPublished = registry.counter("course_platform_outbox_published_total");
    outboxFailed = registry.counter("course_platform_outbox_failed_total");
    paymentConfirmed =
        registry.counter("course_platform_payment_outcomes_total", "outcome", "confirmed");
    paymentFailed = registry.counter("course_platform_payment_outcomes_total", "outcome", "failed");
    certificatesIssued = registry.counter("course_platform_certificates_issued_total");
    rateLimited = registry.counter("course_platform_rate_limited_total");
    rateLimitDegraded = registry.counter("course_platform_rate_limit_degraded_total");
    cacheHits = registry.counter("course_platform_catalog_cache_total", "result", "hit");
    cacheMisses = registry.counter("course_platform_catalog_cache_total", "result", "miss");
    cacheErrors = registry.counter("course_platform_catalog_cache_total", "result", "error");
    enrollmentLatency = registry.timer("course_platform_enrollment_duration");
    Gauge.builder("course_platform_outbox_pending", pendingOutbox, AtomicLong::get)
        .register(registry);
    Gauge.builder("course_platform_outbox_oldest_seconds", oldestOutboxSeconds, AtomicLong::get)
        .register(registry);
  }

  public Timer.Sample enrollmentStarted(MeterRegistry registry) {
    enrollmentAttempts.increment();
    return Timer.start(registry);
  }

  public void enrollmentSucceeded(Timer.Sample sample) {
    enrollmentSuccess.increment();
    sample.stop(enrollmentLatency);
  }

  public void enrollmentRejected(Timer.Sample sample) {
    enrollmentRejected.increment();
    sample.stop(enrollmentLatency);
  }

  public void outboxPublished(int count) {
    outboxPublished.increment(count);
  }

  public void outboxFailed() {
    outboxFailed.increment();
  }

  public void paymentOutcome(boolean confirmed) {
    (confirmed ? paymentConfirmed : paymentFailed).increment();
  }

  public void certificateIssued() {
    certificatesIssued.increment();
  }

  public void rateLimited() {
    rateLimited.increment();
  }

  public void rateLimitDegraded() {
    rateLimitDegraded.increment();
  }

  public void cacheHit() {
    cacheHits.increment();
  }

  public void cacheMiss() {
    cacheMisses.increment();
  }

  public void cacheError() {
    cacheErrors.increment();
  }

  public void updateOutboxGauges(long pending, Duration oldest) {
    pendingOutbox.set(pending);
    oldestOutboxSeconds.set(Math.max(0, oldest.toSeconds()));
  }
}
