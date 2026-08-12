package com.acme.courseplatform.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.acme.courseplatform.catalog.application.model.CourseView;
import com.acme.courseplatform.catalog.application.model.PageResult;
import com.acme.courseplatform.catalog.infrastructure.cache.CatalogCache;
import com.acme.courseplatform.shared.ratelimit.RedisTokenBucket;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import tools.jackson.databind.json.JsonMapper;

@Testcontainers
class RedisFeaturesIntegrationTest {

  @Container
  static final GenericContainer<?> REDIS =
      new GenericContainer<>("redis:8.2-alpine").withExposedPorts(6379);

  LettuceConnectionFactory factory;
  StringRedisTemplate redis;

  @BeforeEach
  void connect() {
    factory = new LettuceConnectionFactory(REDIS.getHost(), REDIS.getMappedPort(6379));
    factory.afterPropertiesSet();
    redis = new StringRedisTemplate(factory);
    redis.afterPropertiesSet();
    redis.getConnectionFactory().getConnection().serverCommands().flushAll();
  }

  @AfterEach
  void close() {
    factory.destroy();
  }

  @Test
  void catalogCacheLoadsOnceAndReloadsAfterInvalidation() {
    CatalogCache cache = new CatalogCache(redis, JsonMapper.builder().findAndAddModules().build());
    AtomicInteger loads = new AtomicInteger();
    UUID id = UUID.randomUUID();
    CourseView expected =
        new CourseView(
            id,
            "Cached",
            "Description",
            3,
            "BEGINNER",
            BigDecimal.TEN,
            "EUR",
            10,
            0,
            "PUBLISHED",
            UUID.randomUUID(),
            UUID.randomUUID());

    CourseView first = cache.course(id, () -> loaded(expected, loads));
    CourseView second = cache.course(id, () -> loaded(expected, loads));

    assertThat(first).isEqualTo(expected);
    assertThat(second).isEqualTo(expected);
    assertThat(loads).hasValue(1);

    cache.evictCourse(id);
    cache.course(id, () -> loaded(expected, loads));

    assertThat(loads).hasValue(2);
  }

  @Test
  void tokenBucketRejectsAfterCapacityAndReturnsRetryInformation() {
    RedisTokenBucket limiter = new RedisTokenBucket(redis);

    for (int request = 0; request < 3; request++) {
      assertThat(limiter.consume("test", 3, 60).allowed()).isTrue();
    }

    RedisTokenBucket.Decision denied = limiter.consume("test", 3, 60);

    assertThat(denied.allowed()).isFalse();
    assertThat(denied.remaining()).isZero();
    assertThat(denied.retryAfterSeconds()).isPositive();
  }

  @Test
  @SuppressWarnings("unchecked")
  void searchFallbackDoesNotLoadTwiceWhenRedisWriteFails() {
    StringRedisTemplate unavailableRedis = mock(StringRedisTemplate.class);
    ValueOperations<String, String> values = mock(ValueOperations.class);
    when(unavailableRedis.opsForValue()).thenReturn(values);
    when(values.get(anyString())).thenReturn(null);
    doThrow(new IllegalStateException("redis unavailable"))
        .when(values)
        .set(anyString(), anyString(), any(Duration.class));
    CatalogCache cache =
        new CatalogCache(unavailableRedis, JsonMapper.builder().findAndAddModules().build());
    AtomicInteger loads = new AtomicInteger();

    PageResult<CourseView> result =
        cache.search(
            "beginner",
            () -> {
              loads.incrementAndGet();
              return new PageResult<>(List.of(), 0, 0, 20);
            });

    assertThat(result.content()).isEmpty();
    assertThat(loads).hasValue(1);
  }

  private static CourseView loaded(CourseView value, AtomicInteger loads) {
    loads.incrementAndGet();
    return value;
  }
}
