package com.tispace.queryservice.cache;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.resilience4j.bulkhead.annotation.Bulkhead;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

/**
 * Redis cache with circuit breaker and bulkhead protection.
 * Uses ±10% TTL jitter to prevent cache stampede. Returns error results instead of throwing.
 */
@Service
@Slf4j
public class CacheService {

    private static final double TTL_JITTER_PERCENT = 0.1; // ±10%

    private final RedisTemplate<String, String> redisTemplate;
    private final ObjectMapper objectMapper;
    private final CacheMetrics metrics;

    public CacheService(
            RedisTemplate<String, String> redisTemplate,
            ObjectMapper objectMapper,
            CacheMetrics metrics
    ) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.metrics = metrics;
    }

    @CircuitBreaker(name = "redis", fallbackMethod = "getFallback")
    @Bulkhead(name = "redis", fallbackMethod = "getFallback")
    public <T> CacheResult<T> get(String key, Class<T> type) {
        if (isBlank(key)) {
            log.warn("Attempted to get cache with null/blank key");
            return CacheResult.miss();
        }

        try {
            return metrics.recordGet(() -> getFromCache(key, type));
        } catch (Exception e) {
            metrics.error();
            log.warn("Cache get failed for key={}", key, e);
            return CacheResult.error(e);
        }
    }

    private <T> CacheResult<T> getFromCache(String key, Class<T> type) {
        try {
            String value = redisTemplate.opsForValue().get(key);

            if (value == null) {
                metrics.miss();
                return CacheResult.miss();
            }

            T result = objectMapper.readValue(value, type);
            metrics.hit();
            return CacheResult.hit(result);

        } catch (JsonProcessingException e) {
            metrics.error();
            log.warn("Failed to deserialize cached value for key={}", key, e);
            return CacheResult.error(e);

        } catch (Exception e) {
            metrics.error();
            log.warn("Unexpected redis/get error for key={}", key, e);
            return CacheResult.error(e);
        }
    }

    @SuppressWarnings("unused")
    public <T> CacheResult<T> getFallback(String key, Class<T> type, Throwable t) {
        metrics.unavailable();
        log.warn("Redis get fallback triggered for key={}", key, t);
        return CacheResult.error(t);
    }

    @CircuitBreaker(name = "redis", fallbackMethod = "putFallback")
    @Bulkhead(name = "redis", fallbackMethod = "putFallback")
    public void put(String key, Object value, long ttlSeconds) {
        if (isBlank(key)) {
            log.warn("Attempted to put cache with null/blank key");
            return;
        }
        if (value == null) {
            log.warn("Attempted to cache null value for key={}", key);
            return;
        }
        if (ttlSeconds <= 0) {
            log.warn("Invalid TTL={} seconds for key={}. Skipping cache put.", ttlSeconds, key);
            return;
        }

        try {
            metrics.recordPut(() -> putToCache(key, value, ttlSeconds));
        } catch (Exception e) {
            metrics.error();
            log.warn("Cache put failed for key={}", key, e);
        }
    }

    private void putToCache(String key, Object value, long ttlSeconds) {
        try {
            String jsonValue = objectMapper.writeValueAsString(value);

            long jitteredTtl = addJitter(ttlSeconds);
            redisTemplate.opsForValue().set(key, jsonValue, jitteredTtl, TimeUnit.SECONDS);

            log.debug("Cached key={} ttl={}s (original={}s)", key, jitteredTtl, ttlSeconds);

        } catch (JsonProcessingException e) {
            metrics.error();
            log.warn("Failed to serialize value for key={}", key, e);

        } catch (Exception e) {
            metrics.error();
            log.warn("Unexpected redis/put error for key={}", key, e);
        }
    }

    @SuppressWarnings("unused")
    public void putFallback(String key, Object value, long ttlSeconds, Throwable t) {
        metrics.unavailable();
        log.warn("Redis put fallback triggered for key={}", key, t);
    }

    @CircuitBreaker(name = "redis", fallbackMethod = "deleteFallback")
    public void delete(String key) {
        if (isBlank(key)) {
            log.warn("Attempted to delete cache with null/blank key");
            return;
        }

        try {
            redisTemplate.delete(key);
        } catch (Exception e) {
            metrics.error();
            log.warn("Unexpected redis/delete error for key={}", key, e);
        }
    }

    @SuppressWarnings("unused")
    public void deleteFallback(String key, Throwable t) {
        metrics.unavailable();
        log.warn("Redis delete fallback triggered for key={}", key, t);
    }

    private long addJitter(long ttlSeconds) {
        double rnd = ThreadLocalRandom.current().nextDouble(); // 0..1
        double jitter = (rnd * 2 - 1) * TTL_JITTER_PERCENT;    // -0.1..+0.1
        long jittered = (long) (ttlSeconds * (1 + jitter));
        return Math.max(1, jittered);
    }

    private boolean isBlank(String key) {
        return key == null || key.isBlank();
    }
}
