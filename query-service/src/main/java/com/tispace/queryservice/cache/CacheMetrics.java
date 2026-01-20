package com.tispace.queryservice.cache;

public interface CacheMetrics {

    <T> T recordGet(TimerCallable<T> action);

    void recordPut(Runnable action);

    void hit();
    void miss();
    void error();
    void unavailable();

    @FunctionalInterface
    interface TimerCallable<T> {
        T call() throws Exception;
    }
}
