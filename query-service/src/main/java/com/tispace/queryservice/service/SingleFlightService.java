package com.tispace.queryservice.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tispace.common.exception.ExternalApiException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Collections;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * Service implementing single-flight pattern to prevent stampede/thundering herd.
 * Uses distributed lock via Redis as primary mechanism, falls back to in-memory single-flight.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SingleFlightService implements SingleFlightExecutor {

    private static final String LOCK_PREFIX = "lock:singleflight:";
    private static final String RESULT_PREFIX = "result:singleflight:";

    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;

    @Value("${singleflight.lock-timeout-seconds:30}")
    private long lockTimeoutSeconds;

    @Value("${singleflight.in-flight-timeout-seconds:10}")
    private long inFlightTimeoutSeconds;

    @Value("${singleflight.result-ttl-seconds:30}")
    private long resultTtlSeconds;

    @Value("${singleflight.poll-interval-ms:50}")
    private long pollIntervalMs;

    // local fallback
    private final ConcurrentHashMap<String, CompletableFuture<?>> inFlightRequests = new ConcurrentHashMap<>();

    // Lua: delete only if token matches
    private static final DefaultRedisScript<Long> RELEASE_LOCK_SCRIPT = new DefaultRedisScript<>(
            "if redis.call('get', KEYS[1]) == ARGV[1] then " +
                    "  return redis.call('del', KEYS[1]) " +
                    "else " +
                    "  return 0 " +
                    "end",
            Long.class
    );

    /**
     * Distributed single-flight:
     * - One instance computes and stores the result in Redis
     * - Others wait for result-key instead of recomputing
     */
    @Override
    public <T> T execute(String key, Class<T> resultType, SingleFlightOperation<T> operation) throws Exception {
        if (key == null || key.isBlank()) {
            throw new IllegalArgumentException("Key cannot be null or empty");
        }
        if (resultType == null) {
            throw new IllegalArgumentException("resultType cannot be null");
        }

        String lockKey = LOCK_PREFIX + key;
        String resultKey = RESULT_PREFIX + key;

        Duration lockTtl = Duration.ofSeconds(lockTimeoutSeconds + 5);
        Duration resultTtl = Duration.ofSeconds(resultTtlSeconds);

        String token = UUID.randomUUID().toString();

        // Optimization: if result already exists (another instance finished)
        try {
            String cached = redis.opsForValue().get(resultKey);
            if (cached != null) {
                return objectMapper.readValue(cached, resultType);
            }
        } catch (Exception e) {
            // Redis might be down => local fallback
            log.warn("Redis read failed for resultKey={}, fallback to in-memory single-flight", resultKey, e);
            return executeWithInMemorySingleFlight(key, operation);
        }

        Boolean acquired = tryAcquireLock(lockKey, token, lockTtl);

        if (Boolean.TRUE.equals(acquired)) {
            // We are the leader
            try {
                T result = operation.execute();

                // store result for followers
                try {
                    String json = objectMapper.writeValueAsString(result);
                    redis.opsForValue().set(resultKey, json, resultTtl);
                } catch (Exception e) {
                    // Do NOT fail the main operation if caching failed
                    log.warn("Failed to store single-flight result for key={}", key, e);
                }

                return result;
            } finally {
                releaseLockSafely(lockKey, token);
            }
        }

        // Someone else is computing => wait for the result in Redis
        try {
            return waitForResult(resultKey, resultType);
        } catch (Exception waitError) {
            // If waiting fails (timeout, redis issues) => fallback to in-memory single-flight
            log.warn("Failed waiting for single-flight result key={}, fallback to in-memory", resultKey, waitError);
            return executeWithInMemorySingleFlight(key, operation);
        }
    }



    private <T> T waitForResult(String resultKey, Class<T> resultType) throws Exception {
        long deadlineNs = System.nanoTime() + TimeUnit.SECONDS.toNanos(inFlightTimeoutSeconds);

        while (System.nanoTime() < deadlineNs) {
            String json = redis.opsForValue().get(resultKey);
            if (json != null) {
                return objectMapper.readValue(json, resultType);
            }

            // simple bounded sleep
            Thread.sleep(pollIntervalMs);
        }

        throw new ExternalApiException("Single-flight timeout waiting for resultKey=" + resultKey);
    }

    /**
     * Local in-memory single-flight (safe, no commonPool).
     */
    @SuppressWarnings("unchecked")
    private <T> T executeWithInMemorySingleFlight(String key, SingleFlightOperation<T> operation) throws Exception {
        CompletableFuture<T> newFuture = new CompletableFuture<>();
        CompletableFuture<T> existing = (CompletableFuture<T>) inFlightRequests.putIfAbsent(key, newFuture);

        if (existing == null) {
            try {
                T result = operation.execute();
                newFuture.complete(result);
                return result;
            } catch (Exception e) {
                newFuture.completeExceptionally(e);
                throw e;
            } finally {
                inFlightRequests.remove(key, newFuture);
            }
        }

        try {
            return existing.get(inFlightTimeoutSeconds, TimeUnit.SECONDS);
        } catch (Exception e) {
            throw new ExternalApiException("In-flight wait failed for key=" + key, e);
        }
    }

    private Boolean tryAcquireLock(String lockKey, String token, Duration ttl) {
        try {
            // SET lockKey token NX EX ttl
            return redis.opsForValue().setIfAbsent(lockKey, token, ttl);
        } catch (Exception e) {
            log.warn("Failed to acquire distributed lock {}. Redis may be unavailable.", lockKey, e);
            return false;
        }
    }

    private void releaseLockSafely(String lockKey, String token) {
        try {
            redis.execute(RELEASE_LOCK_SCRIPT, Collections.singletonList(lockKey), token);
        } catch (Exception e) {
            log.warn("Failed to release lock safely. lockKey={}", lockKey, e);
        }
    }
}

