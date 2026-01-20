package com.tispace.queryservice.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tispace.common.exception.ExternalApiException;
import com.tispace.queryservice.config.SingleFlightProperties;
import com.tispace.queryservice.dto.SingleFlightEnvelope;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
    private final SingleFlightProperties singleFlightProperties;

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

        // lock ttl must cover wait time (+ small buffer)
        long lockSeconds = Math.max(
                singleFlightProperties.getLockTimeoutSeconds(),
                singleFlightProperties.getInFlightTimeoutSeconds() + 5
        );
        Duration lockTtl = Duration.ofSeconds(lockSeconds);
        Duration resultTtl = Duration.ofSeconds(singleFlightProperties.getResultTtlSeconds());

        String token = UUID.randomUUID().toString();

        // fast-path: if leader already stored envelope
        try {
            SingleFlightEnvelope cached = readEnvelope(resultKey);
            if (cached != null) {
                return unwrapEnvelope(resultKey, cached, resultType);
            }
        } catch (ExternalApiException e) {
            // This is NOT a Redis failure and MUST be propagated (tests expect this).
            throw e;
        } catch (Exception e) {
            // fallback only on real Redis/serialization issues
            log.warn("Redis read failed for resultKey={}, fallback to in-memory single-flight", resultKey, e);
            return executeWithInMemorySingleFlight(key, operation);
        }

        Boolean acquired = tryAcquireLock(lockKey, token, lockTtl);

        if (Boolean.TRUE.equals(acquired)) {
            // leader path
            try {
                T result = operation.execute();

                // store SUCCESS envelope for followers
                safeWriteEnvelope(
                        resultKey,
                        new SingleFlightEnvelope(true, objectMapper.writeValueAsString(result), null, null),
                        resultTtl
                );

                return result;
            } catch (Exception leaderError) {
                // store FAILURE envelope so followers fail fast
                safeWriteEnvelope(
                        resultKey,
                        new SingleFlightEnvelope(false, null, mapErrorCode(leaderError), safeMessage(leaderError)),
                        resultTtl
                );

                throw leaderError;
            } finally {
                releaseLockSafely(lockKey, token);
            }
        }

        // follower path: wait for envelope with backoff polling
        try {
            return waitForEnvelopeWithBackoff(resultKey, resultType);
        } catch (ExternalApiException e) {
            // If the envelope indicates error OR is malformed (missing payload),
            // it's a valid single-flight failure and must NOT fallback to in-memory.
            throw e;
        } catch (Exception waitError) {
            //fallback only for Redis waiting failures (timeouts, redis errors, etc.)
            log.warn("Failed waiting for resultKey={}, fallback to in-memory", resultKey, waitError);
            return executeWithInMemorySingleFlight(key, operation);
        }
    }

    private <T> T waitForEnvelopeWithBackoff(String resultKey, Class<T> resultType) throws Exception {
        long deadlineNs = System.nanoTime() + TimeUnit.SECONDS.toNanos(singleFlightProperties.getInFlightTimeoutSeconds());

        long sleepMs = Math.max(1, singleFlightProperties.getPollInitialMs());

        while (System.nanoTime() < deadlineNs) {
            if (Thread.currentThread().isInterrupted()) {
                Thread.currentThread().interrupt();
                throw new ExternalApiException("Interrupted while waiting for single-flight resultKey=" + resultKey);
            }

            SingleFlightEnvelope env;
            try {
                env = readEnvelope(resultKey);
            } catch (Exception redisErr) {
                // real Redis error while waiting -> caller may choose fallback
                throw new ExternalApiException("Redis error while waiting for resultKey=" + resultKey, redisErr);
            }

            if (env != null) {
                // If env indicates error / missing payload -> unwrapEnvelope throws ExternalApiException
                return unwrapEnvelope(resultKey, env, resultType);
            }

            long remainingNs = deadlineNs - System.nanoTime();
            if (remainingNs <= 0) break;

            long maxSleepByDeadlineMs = TimeUnit.NANOSECONDS.toMillis(remainingNs);
            long actualSleepMs = Math.min(sleepMs, Math.max(1, maxSleepByDeadlineMs));

            Thread.sleep(actualSleepMs);

            // exponential backoff up to max
            long next = sleepMs * Math.max(1, singleFlightProperties.getPollMultiplier());
            sleepMs = Math.min(Math.max(1, singleFlightProperties.getPollMaxMs()), next);
        }

        throw new ExternalApiException("Single-flight timeout waiting for resultKey=" + resultKey);
    }

    private SingleFlightEnvelope readEnvelope(String resultKey) throws Exception {
        String json = redis.opsForValue().get(resultKey);
        if (json == null) return null;
        return objectMapper.readValue(json, SingleFlightEnvelope.class);
    }

    private void safeWriteEnvelope(String resultKey, SingleFlightEnvelope envelope, Duration ttl) {
        try {
            redis.opsForValue().set(resultKey, objectMapper.writeValueAsString(envelope), ttl);
        } catch (Exception e) {
            // caching failure shouldn't break business logic
            log.warn("Failed to store single-flight envelope for resultKey={}", resultKey, e);
        }
    }

    private <T> T unwrapEnvelope(String resultKey, SingleFlightEnvelope envelope, Class<T> resultType) throws Exception {
        if (envelope.isSuccess()) {
            if (envelope.getPayload() == null) {
                throw new ExternalApiException("Single-flight envelope missing payload for key=" + resultKey);
            }
            return objectMapper.readValue(envelope.getPayload(), resultType);
        }

        String code = envelope.getErrorCode() != null ? envelope.getErrorCode() : "UPSTREAM_ERROR";
        String msg = envelope.getMessage() != null ? envelope.getMessage() : "Single-flight operation failed";
        throw new ExternalApiException("Single-flight failed (" + code + "): " + msg);
    }

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
            return existing.get(singleFlightProperties.getInFlightTimeoutSeconds(), TimeUnit.SECONDS);
        } catch (Exception e) {
            throw new ExternalApiException("In-flight wait failed for key=" + key, e);
        }
    }

    private Boolean tryAcquireLock(String lockKey, String token, Duration ttl) {
        try {
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

    private String mapErrorCode(Throwable t) {
        return (t instanceof ExternalApiException) ? "EXTERNAL_API" : "INTERNAL_ERROR";
    }

    private String safeMessage(Throwable t) {
        String raw = t.getMessage();
        if (raw == null || raw.isBlank()) {
            return t.getClass().getSimpleName();
        }
        return raw.length() > 300 ? raw.substring(0, 300) : raw;
    }
}

