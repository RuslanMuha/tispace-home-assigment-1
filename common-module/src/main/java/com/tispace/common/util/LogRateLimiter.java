package com.tispace.common.util;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.LongSupplier;

public final class LogRateLimiter {

    private static final LogRateLimiter INSTANCE = new LogRateLimiter(System::currentTimeMillis);

    private final ConcurrentHashMap<String, AtomicLong> lastLogMillis = new ConcurrentHashMap<>();
    private final LongSupplier clock;

    LogRateLimiter(LongSupplier clock) {
        this.clock = clock;
    }

    public static LogRateLimiter getInstance() {
        return INSTANCE;
    }

    /**
     * @param key    fixed operational key (e.g. "redis:cache_get_failed")
     * @param window minimum interval between log emissions for this key
     * @return true if the caller should emit the log line
     */
    public boolean shouldLog(String key, Duration window) {
        long now = clock.getAsLong();
        long windowMs = window.toMillis();
        AtomicLong last = lastLogMillis.computeIfAbsent(key, k -> new AtomicLong(0));
        long prev = last.get();
        if (prev == 0 || now - prev >= windowMs) {
            return last.compareAndSet(prev, now);
        }
        return false;
    }
}
