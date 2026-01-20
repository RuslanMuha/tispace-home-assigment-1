package com.tispace.queryservice.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tispace.queryservice.cache.CacheResult;
import io.github.resilience4j.bulkhead.annotation.Bulkhead;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

@Service
@Slf4j
public class CacheService {

    private static final double TTL_JITTER_PERCENT = 0.1; // ±10%

    private final RedisTemplate<String, String> redisTemplate;
    private final ObjectMapper objectMapper;

    // Metrics
    private final Counter cacheHits;
    private final Counter cacheMisses;
    private final Counter cacheErrors;
    private final Counter cacheUnavailable;
    private final Timer cacheGetTimer;
    private final Timer cachePutTimer;

    public CacheService(
            RedisTemplate<String, String> redisTemplate,
            ObjectMapper objectMapper,
            MeterRegistry meterRegistry
    ) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;


        this.cacheHits = Counter.builder("cache.hits")
                .description("Number of cache hits")
                .tag("cache", "summary")
                .register(meterRegistry);

        this.cacheMisses = Counter.builder("cache.misses")
                .description("Number of cache misses")
                .tag("cache", "summary")
                .register(meterRegistry);

        this.cacheErrors = Counter.builder("cache.errors")
                .description("Number of cache errors (serialization/redis errors)")
                .tag("cache", "summary")
                .register(meterRegistry);

        this.cacheUnavailable = Counter.builder("cache.unavailable")
                .description("Number of times cache was unavailable due to circuit breaker/bulkhead")
                .tag("cache", "summary")
                .register(meterRegistry);

        this.cacheGetTimer = Timer.builder("cache.get.duration")
                .description("Cache get operation duration")
                .tag("cache", "summary")
                .register(meterRegistry);

        this.cachePutTimer = Timer.builder("cache.put.duration")
                .description("Cache put operation duration")
                .tag("cache", "summary")
                .register(meterRegistry);
    }

    @CircuitBreaker(name = "redis", fallbackMethod = "getFallback")
    @Bulkhead(name = "redis", fallbackMethod = "getFallback")
    public <T> CacheResult<T> get(String key, Class<T> type) {
        if (isBlank(key)) {
            log.warn("Attempted to get cache with null/blank key");
            return CacheResult.miss();
        }

        try {
            return cacheGetTimer.recordCallable(() -> getFromCache(key, type));
        } catch (Exception e) {
            cacheErrors.increment();
            log.warn("Cache get timer/metrics failed for key={}", key, e);
            return CacheResult.error(e);
        }
    }

    private <T> CacheResult<T> getFromCache(String key, Class<T> type) {
        try {
            String value = redisTemplate.opsForValue().get(key);

            if (value == null) {
                cacheMisses.increment();
                return CacheResult.miss();
            }

            T result = objectMapper.readValue(value, type);
            cacheHits.increment();
            return CacheResult.hit(result);

        } catch (JsonProcessingException e) {
            cacheErrors.increment();
            log.warn("Failed to deserialize cached value for key={}. Treating as cache ERROR.", key, e);
            return CacheResult.error(e);

        } catch (Exception e) {
            cacheErrors.increment();
            log.warn("Unexpected redis/get error for key={}. Treating as cache ERROR.", key, e);
            return CacheResult.error(e);
        }
    }

    /**
     * Fallback for get (circuit open / bulkhead full).
     */
    @SuppressWarnings("unused")
    public <T> CacheResult<T> getFallback(String key, Class<T> type, Throwable t) {
        cacheUnavailable.increment();
        log.warn("Redis get fallback triggered for key={}. Cache UNAVAILABLE.", key, t);
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
            cachePutTimer.record(() -> putToCache(key, value, ttlSeconds));
        } catch (Exception e) {
            cacheErrors.increment();
            log.warn("Cache put timer/metrics failed for key={}. Put skipped.", key, e);
        }
    }

    private void putToCache(String key, Object value, long ttlSeconds) {
        try {
            String jsonValue = objectMapper.writeValueAsString(value);

            long jitteredTtl = addJitter(ttlSeconds);
            redisTemplate.opsForValue().set(key, jsonValue, jitteredTtl, TimeUnit.SECONDS);

            log.debug("Cached key={} ttl={}s (original={}s)", key, jitteredTtl, ttlSeconds);

        } catch (JsonProcessingException e) {
            cacheErrors.increment();
            log.warn("Failed to serialize value for key={}. Cache put failed silently.", key, e);

        } catch (Exception e) {
            cacheErrors.increment();
            log.warn("Unexpected redis/put error for key={}. Cache put failed silently.", key, e);
        }
    }

    @SuppressWarnings("unused")
    public void putFallback(String key, Object value, long ttlSeconds, Throwable t) {
        cacheUnavailable.increment();
        log.warn("Redis put fallback triggered for key={}. Cache UNAVAILABLE.", key, t);
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
            cacheErrors.increment();
            log.warn("Unexpected redis/delete error for key={}. Delete failed silently.", key, e);
        }
    }

    @SuppressWarnings("unused")
    public void deleteFallback(String key, Throwable t) {
        cacheUnavailable.increment();
        log.warn("Redis delete fallback triggered for key={}. Cache UNAVAILABLE.", key, t);
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
