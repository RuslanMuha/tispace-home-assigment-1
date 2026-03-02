package com.tispace.common.util;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;

class LogRateLimiterTest {

    @Test
    void firstCallShouldReturnTrue() {
        AtomicLong clock = new AtomicLong(1_000);
        LogRateLimiter limiter = new LogRateLimiter(clock::get);

        assertTrue(limiter.shouldLog("test:key", Duration.ofSeconds(10)));
    }

    @Test
    void subsequentCallWithinWindowShouldReturnFalse() {
        AtomicLong clock = new AtomicLong(1_000);
        LogRateLimiter limiter = new LogRateLimiter(clock::get);

        assertTrue(limiter.shouldLog("test:key", Duration.ofSeconds(10)));

        clock.set(5_000);
        assertFalse(limiter.shouldLog("test:key", Duration.ofSeconds(10)));
    }

    @Test
    void callAfterWindowShouldReturnTrue() {
        AtomicLong clock = new AtomicLong(1_000);
        LogRateLimiter limiter = new LogRateLimiter(clock::get);

        assertTrue(limiter.shouldLog("test:key", Duration.ofSeconds(10)));

        clock.set(11_001);
        assertTrue(limiter.shouldLog("test:key", Duration.ofSeconds(10)));
    }

    @Test
    void differentKeysShouldBeIndependent() {
        AtomicLong clock = new AtomicLong(1_000);
        LogRateLimiter limiter = new LogRateLimiter(clock::get);

        assertTrue(limiter.shouldLog("key:a", Duration.ofSeconds(10)));
        assertTrue(limiter.shouldLog("key:b", Duration.ofSeconds(10)));

        clock.set(5_000);
        assertFalse(limiter.shouldLog("key:a", Duration.ofSeconds(10)));
        assertFalse(limiter.shouldLog("key:b", Duration.ofSeconds(10)));
    }

    @Test
    void singletonInstanceShouldBeAvailable() {
        assertNotNull(LogRateLimiter.getInstance());
    }

    @Test
    void callExactlyAtWindowBoundaryShouldReturnTrue() {
        AtomicLong clock = new AtomicLong(1_000);
        LogRateLimiter limiter = new LogRateLimiter(clock::get);

        assertTrue(limiter.shouldLog("test:key", Duration.ofSeconds(10)));

        clock.set(11_000);
        assertTrue(limiter.shouldLog("test:key", Duration.ofSeconds(10)));
    }
}
