package com.tispace.queryservice.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tispace.common.exception.ExternalApiException;
import com.tispace.queryservice.config.SingleFlightProperties;
import com.tispace.common.util.LogRateLimiter;
import com.tispace.queryservice.dto.SingleFlightEnvelope;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * Single-flight pattern: only one operation executes per key, others wait for result.
 * Uses Redis distributed lock; falls back to in-memory if Redis fails (single-instance only).
 * Leader stores result in Redis; followers poll with exponential backoff.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SingleFlightService implements SingleFlightExecutor {

    private static final String LOCK_PREFIX = "lock:singleflight:";
    private static final String RESULT_PREFIX = "result:singleflight:";
    private static final LogRateLimiter LOG_LIMITER = LogRateLimiter.getInstance();
    private static final Duration REDIS_LOG_WINDOW = Duration.ofSeconds(10);

    private final SingleFlightRedisBackend redisBackend;
    private final ObjectMapper objectMapper;
    private final SingleFlightProperties singleFlightProperties;

    private final ConcurrentHashMap<String, CompletableFuture<?>> inFlightRequests = new ConcurrentHashMap<>();

    private static final class FollowerWaitFailureException extends Exception {
        private FollowerWaitFailureException(String message) {
            super(message);
        }

        private FollowerWaitFailureException(String message, Throwable cause) {
            super(message, cause);
        }
    }

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

        long lockSeconds = Math.max(
                singleFlightProperties.getLockTimeoutSeconds(),
                singleFlightProperties.getInFlightTimeoutSeconds() + 5
        );
        Duration lockTtl = Duration.ofSeconds(lockSeconds);
        Duration resultTtl = Duration.ofSeconds(singleFlightProperties.getResultTtlSeconds());

        String token = UUID.randomUUID().toString();

        try {
            SingleFlightEnvelope cached = redisBackend.readResult(resultKey);
            if (cached != null) {
                return unwrapEnvelope(resultKey, cached, resultType);
            }
        } catch (ExternalApiException e) {
            throw e;
        } catch (Exception e) {
            if (LOG_LIMITER.shouldLog("redis:read_failed_fallback", REDIS_LOG_WINDOW)) {
                log.warn("Redis read failed for resultKey={}, fallback to in-memory: [{}] {}", resultKey, e.getClass().getSimpleName(), e.getMessage());
            }
            log.debug("Redis read failed for resultKey={}, fallback to in-memory single-flight", resultKey, e);
            return executeWithInMemorySingleFlight(key, operation);
        }

        SingleFlightRedisBackend.LockAcquireResult lockResult = redisBackend.tryAcquireLock(lockKey, token, lockTtl);

        if (lockResult == SingleFlightRedisBackend.LockAcquireResult.BACKEND_UNAVAILABLE) {
            if (LOG_LIMITER.shouldLog("redis:lock_acquire_failed", REDIS_LOG_WINDOW)) {
                log.warn("Redis unavailable for lockKey={}, fallback to in-memory single-flight", lockKey);
            }
            return executeWithInMemorySingleFlight(key, operation);
        }

        if (lockResult == SingleFlightRedisBackend.LockAcquireResult.ACQUIRED) {
            try {
                T result = operation.execute();

                redisBackend.writeResult(
                        resultKey,
                        new SingleFlightEnvelope(true, objectMapper.writeValueAsString(result), null, null),
                        resultTtl
                );

                return result;
            } catch (Exception leaderError) {
                redisBackend.writeResult(
                        resultKey,
                        new SingleFlightEnvelope(false, null, mapErrorCode(leaderError), safeMessage(leaderError)),
                        resultTtl
                );

                throw leaderError;
            } finally {
                redisBackend.releaseLock(lockKey, token);
            }
        }

        try {
            return waitForEnvelopeWithBackoff(resultKey, resultType);
        } catch (FollowerWaitFailureException waitError) {
            if (LOG_LIMITER.shouldLog("redis:read_failed_fallback", REDIS_LOG_WINDOW)) {
                log.warn("Failed waiting for resultKey={}, fallback to in-memory: [{}] {}", resultKey, waitError.getClass().getSimpleName(), waitError.getMessage());
            }
            log.debug("Failed waiting for resultKey={}, fallback to in-memory", resultKey, waitError);
            return executeWithInMemorySingleFlight(key, operation);
        }
    }

    private <T> T waitForEnvelopeWithBackoff(String resultKey, Class<T> resultType) throws Exception {
        long deadlineNs = System.nanoTime() + TimeUnit.SECONDS.toNanos(singleFlightProperties.getInFlightTimeoutSeconds());

        long sleepMs = Math.max(1, singleFlightProperties.getPollInitialMs());

        while (System.nanoTime() < deadlineNs) {
            if (Thread.currentThread().isInterrupted()) {
                Thread.currentThread().interrupt();
                throw new FollowerWaitFailureException("Interrupted while waiting for single-flight resultKey=" + resultKey);
            }

            SingleFlightEnvelope env;
            try {
                env = redisBackend.readResult(resultKey);
            } catch (Exception redisErr) {
                throw new FollowerWaitFailureException("Redis error while waiting for resultKey=" + resultKey, redisErr);
            }

            if (env != null) {
                return unwrapEnvelope(resultKey, env, resultType);
            }

            long remainingNs = deadlineNs - System.nanoTime();
            if (remainingNs <= 0) break;

            long maxSleepByDeadlineMs = TimeUnit.NANOSECONDS.toMillis(remainingNs);
            long actualSleepMs = Math.min(sleepMs, Math.max(1, maxSleepByDeadlineMs));

            Thread.sleep(actualSleepMs);

            long next = sleepMs * Math.max(1, singleFlightProperties.getPollMultiplier());
            sleepMs = Math.min(Math.max(1, singleFlightProperties.getPollMaxMs()), next);
        }

        throw new FollowerWaitFailureException("Single-flight timeout waiting for resultKey=" + resultKey);
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

