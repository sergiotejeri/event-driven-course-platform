package com.acme.courseplatform.catalog.infrastructure.cache;

import com.acme.courseplatform.catalog.application.model.CourseView;
import com.acme.courseplatform.catalog.application.model.PageResult;
import java.time.Duration;
import java.util.UUID;
import java.util.function.Supplier;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

@Component
public class CatalogCache {

  private final StringRedisTemplate redis;
  private final ObjectMapper json;
  private final boolean enabled;

  @Autowired
  public CatalogCache(
      ObjectProvider<StringRedisTemplate> redis,
      ObjectMapper json,
      @Value("${spring.cache.type:redis}") String cacheType) {
    this.redis = redis.getIfAvailable();
    this.json = json;
    this.enabled = !"none".equalsIgnoreCase(cacheType) && this.redis != null;
  }

  public CatalogCache(StringRedisTemplate redis, ObjectMapper json) {
    this.redis = redis;
    this.json = json;
    this.enabled = true;
  }

  public CourseView course(UUID id, Supplier<CourseView> loader) {
    if (!enabled) {
      return loader.get();
    }
    String key = "catalog:v1:course:" + id;
    try {
      String cached = redis.opsForValue().get(key);
      if (cached != null) {
        return json.readValue(cached, CourseView.class);
      }
    } catch (Exception ignored) {
    }
    CourseView value = loader.get();
    try {
      redis.opsForValue().set(key, json.writeValueAsString(value), Duration.ofMinutes(10));
    } catch (Exception ignored) {
    }
    return value;
  }

  public PageResult<CourseView> search(String signature, Supplier<PageResult<CourseView>> loader) {
    if (!enabled) {
      return loader.get();
    }
    String key;
    try {
      String version = redis.opsForValue().get("catalog:v1:search-version");
      key = "catalog:v1:search:" + (version == null ? "0" : version) + ":" + signature;
      String cached = redis.opsForValue().get(key);
      if (cached != null) {
        return json.readValue(
            cached,
            json.getTypeFactory().constructParametricType(PageResult.class, CourseView.class));
      }
    } catch (Exception ignored) {
      return loader.get();
    }
    PageResult<CourseView> value = loader.get();
    try {
      redis.opsForValue().set(key, json.writeValueAsString(value), Duration.ofMinutes(2));
    } catch (Exception ignored) {
    }
    return value;
  }

  public void evictCourse(UUID id) {
    if (!enabled) {
      return;
    }
    try {
      redis.delete("catalog:v1:course:" + id);
    } catch (Exception ignored) {
    }
  }

  public void invalidateSearch() {
    if (!enabled) {
      return;
    }
    try {
      redis.opsForValue().increment("catalog:v1:search-version");
    } catch (Exception ignored) {
    }
  }
}
