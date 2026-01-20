package com.tispace.queryservice.cache;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

@Component
public class MicrometerCacheMetrics implements CacheMetrics {

    private final Counter hits;
    private final Counter misses;
    private final Counter errors;
    private final Counter unavailable;

    private final Timer getTimer;
    private final Timer putTimer;

    public MicrometerCacheMetrics(MeterRegistry meterRegistry) {
        this.hits = Counter.builder("cache.hits")
                .description("Number of cache hits")
                .tag("cache", "summary")
                .register(meterRegistry);

        this.misses = Counter.builder("cache.misses")
                .description("Number of cache misses")
                .tag("cache", "summary")
                .register(meterRegistry);

        this.errors = Counter.builder("cache.errors")
                .description("Number of cache errors (serialization/redis errors)")
                .tag("cache", "summary")
                .register(meterRegistry);

        this.unavailable = Counter.builder("cache.unavailable")
                .description("Number of times cache was unavailable due to circuit breaker/bulkhead")
                .tag("cache", "summary")
                .register(meterRegistry);

        this.getTimer = Timer.builder("cache.get.duration")
                .description("Cache get operation duration")
                .tag("cache", "summary")
                .register(meterRegistry);

        this.putTimer = Timer.builder("cache.put.duration")
                .description("Cache put operation duration")
                .tag("cache", "summary")
                .register(meterRegistry);
    }

    @Override
    public <T> T recordGet(TimerCallable<T> action) {
        try {
            return getTimer.recordCallable(action::call);
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void recordPut(Runnable action) {
        putTimer.record(action);
    }

    @Override
    public void hit() {
        hits.increment();
    }

    @Override
    public void miss() {
        misses.increment();
    }

    @Override
    public void error() {
        errors.increment();
    }

    @Override
    public void unavailable() {
        unavailable.increment();
    }

}
