package com.tispace.queryservice.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tispace.queryservice.dto.SingleFlightEnvelope;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Collections;

@Component
@RequiredArgsConstructor
@Slf4j
public class RedisSingleFlightBackend implements SingleFlightRedisBackend {
    
    private static final DefaultRedisScript<Long> RELEASE_LOCK_SCRIPT = new DefaultRedisScript<>(
            "if redis.call('get', KEYS[1]) == ARGV[1] then " +
                    "  return redis.call('del', KEYS[1]) " +
                    "else " +
                    "  return 0 " +
                    "end",
            Long.class
    );
    
    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;
    
    @Override
    public LockAcquireResult tryAcquireLock(String lockKey, String token, Duration ttl) {
        try {
            Boolean acquired = redis.opsForValue().setIfAbsent(lockKey, token, ttl);
            return Boolean.TRUE.equals(acquired) ? LockAcquireResult.ACQUIRED : LockAcquireResult.LOCKED;
        } catch (Exception e) {
            log.debug("Failed to acquire distributed lock {}. Redis may be unavailable.", lockKey, e);
            return LockAcquireResult.BACKEND_UNAVAILABLE;
        }
    }
    
    @Override
    public void releaseLock(String lockKey, String token) {
        try {
            redis.execute(RELEASE_LOCK_SCRIPT, Collections.singletonList(lockKey), token);
        } catch (Exception e) {
            log.debug("Failed to release lock safely. lockKey={}", lockKey, e);
        }
    }
    
    @Override
    public SingleFlightEnvelope readResult(String resultKey) throws Exception {
        String json = redis.opsForValue().get(resultKey);
        if (json == null) return null;
        return objectMapper.readValue(json, SingleFlightEnvelope.class);
    }
    
    @Override
    public void writeResult(String resultKey, SingleFlightEnvelope envelope, Duration ttl) {
        try {
            String json = objectMapper.writeValueAsString(envelope);
            redis.opsForValue().set(resultKey, json, ttl);
        } catch (Exception e) {
            log.debug("Failed to store single-flight envelope for resultKey={}", resultKey, e);
        }
    }
}

