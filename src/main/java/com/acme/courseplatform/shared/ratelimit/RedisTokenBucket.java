package com.acme.courseplatform.shared.ratelimit;

import java.time.Instant;
import java.util.List;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

@Component
public class RedisTokenBucket {

  private static final DefaultRedisScript<List> SCRIPT =
      new DefaultRedisScript<>(
          """
          local capacity=tonumber(ARGV[1]); local window=tonumber(ARGV[2]); local now=tonumber(ARGV[3]);
          local values=redis.call('HMGET',KEYS[1],'tokens','ts'); local tokens=tonumber(values[1]) or capacity; local ts=tonumber(values[2]) or now;
          local rate=capacity/window; tokens=math.min(capacity,tokens+math.max(0,now-ts)*rate); local allowed=0;
          if tokens>=1 then tokens=tokens-1; allowed=1 end; redis.call('HSET',KEYS[1],'tokens',tokens,'ts',now); redis.call('PEXPIRE',KEYS[1],window*2);
          local retry=0; if allowed==0 then retry=math.ceil((1-tokens)/rate/1000) end; return {allowed,math.floor(tokens),retry};
          """,
          List.class);

  private final StringRedisTemplate redis;

  public RedisTokenBucket(StringRedisTemplate redis) {
    this.redis = redis;
  }

  public Decision consume(String key, int capacity, int windowSeconds) {
    if (capacity < 1 || windowSeconds < 1) {
      throw new IllegalArgumentException("Invalid rate limit policy");
    }
    List<?> result =
        redis.execute(
            SCRIPT,
            List.of("rate:v1:" + key),
            Integer.toString(capacity),
            Long.toString(windowSeconds * 1000L),
            Long.toString(Instant.now().toEpochMilli()));
    if (result == null || result.size() < 3) {
      throw new IllegalStateException("Invalid Redis rate limit response");
    }
    return new Decision(number(result, 0) == 1, number(result, 1), Math.max(0, number(result, 2)));
  }

  private static long number(List<?> values, int index) {
    return ((Number) values.get(index)).longValue();
  }

  public record Decision(boolean allowed, long remaining, long retryAfterSeconds) {}
}
