package com.acme.courseplatform.shared.ratelimit;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.acme.courseplatform.observability.BusinessMetrics;
import com.acme.courseplatform.shared.api.ApiProblemWriter;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.json.JsonMapper;

class RateLimitFilterTest {

  @Test
  void exhaustedPolicyReturns429AndRetryHeaders() throws Exception {
    RedisTokenBucket bucket = mock(RedisTokenBucket.class);
    when(bucket.consume(anyString(), anyInt(), anyInt()))
        .thenReturn(new RedisTokenBucket.Decision(false, 0, 7));
    BusinessMetrics metrics = mock(BusinessMetrics.class);

    mvc(bucket, metrics)
        .perform(get("/api/v1/test"))
        .andExpect(status().isTooManyRequests())
        .andExpect(header().string("X-RateLimit-Limit", "100"))
        .andExpect(header().string("X-RateLimit-Remaining", "0"))
        .andExpect(header().string("Retry-After", "7"))
        .andExpect(jsonPath("$.errorCode").value("RATE_LIMIT_EXCEEDED"))
        .andExpect(jsonPath("$.title").value("Too Many Requests"))
        .andExpect(jsonPath("$.detail").isNotEmpty())
        .andExpect(jsonPath("$.correlationId").isNotEmpty())
        .andExpect(jsonPath("$.timestamp").isNotEmpty());
    verify(metrics).rateLimited();
  }

  @Test
  void redisOutageFailsClosedForLoginAndOpenForOrdinaryApi() throws Exception {
    RedisTokenBucket bucket = mock(RedisTokenBucket.class);
    when(bucket.consume(anyString(), anyInt(), anyInt()))
        .thenThrow(new IllegalStateException("redis unavailable"));
    BusinessMetrics metrics = mock(BusinessMetrics.class);
    MockMvc mvc = mvc(bucket, metrics);

    mvc.perform(post("/api/v1/auth/login"))
        .andExpect(status().isServiceUnavailable())
        .andExpect(jsonPath("$.errorCode").value("RATE_LIMIT_UNAVAILABLE"));

    mvc.perform(get("/api/v1/test"))
        .andExpect(status().isOk())
        .andExpect(header().string("X-RateLimit-Degraded", "fail-open"));
    verify(metrics, org.mockito.Mockito.times(2)).rateLimitDegraded();
  }

  private static MockMvc mvc(RedisTokenBucket bucket, BusinessMetrics metrics) {
    return MockMvcBuilders.standaloneSetup(new DummyController())
        .addFilters(
            new RateLimitFilter(
                bucket,
                new ApiProblemWriter(JsonMapper.builder().findAndAddModules().build()),
                metrics))
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
