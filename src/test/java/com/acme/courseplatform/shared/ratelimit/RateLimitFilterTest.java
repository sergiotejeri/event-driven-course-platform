package com.acme.courseplatform.shared.ratelimit;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

class RateLimitFilterTest {

  @Test
  void exhaustedPolicyReturns429AndRetryHeaders() throws Exception {
    RedisTokenBucket bucket = mock(RedisTokenBucket.class);
    when(bucket.consume(anyString(), anyInt(), anyInt()))
        .thenReturn(new RedisTokenBucket.Decision(false, 0, 7));

    mvc(bucket)
        .perform(get("/api/v1/test"))
        .andExpect(status().isTooManyRequests())
        .andExpect(header().string("X-RateLimit-Limit", "100"))
        .andExpect(header().string("X-RateLimit-Remaining", "0"))
        .andExpect(header().string("Retry-After", "7"))
        .andExpect(jsonPath("$.errorCode").value("RATE_LIMIT_EXCEEDED"));
  }

  @Test
  void redisOutageFailsClosedForLoginAndOpenForOrdinaryApi() throws Exception {
    RedisTokenBucket bucket = mock(RedisTokenBucket.class);
    when(bucket.consume(anyString(), anyInt(), anyInt()))
        .thenThrow(new IllegalStateException("redis unavailable"));
    MockMvc mvc = mvc(bucket);

    mvc.perform(post("/api/v1/auth/login"))
        .andExpect(status().isServiceUnavailable())
        .andExpect(jsonPath("$.errorCode").value("RATE_LIMIT_UNAVAILABLE"));

    mvc.perform(get("/api/v1/test"))
        .andExpect(status().isOk())
        .andExpect(header().string("X-RateLimit-Degraded", "fail-open"));
  }

  private static MockMvc mvc(RedisTokenBucket bucket) {
    return MockMvcBuilders.standaloneSetup(new DummyController())
        .addFilters(new RateLimitFilter(bucket))
        .build();
  }

  @RestController
  static class DummyController {

    @GetMapping("/api/v1/test")
    String test() {
      return "ok";
    }

    @PostMapping("/api/v1/auth/login")
    String login() {
      return "ok";
    }
  }
}
