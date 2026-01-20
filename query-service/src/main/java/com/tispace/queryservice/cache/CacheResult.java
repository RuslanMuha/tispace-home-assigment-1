package com.tispace.queryservice.cache;

public interface CacheResult<T>  {

    record Hit<T>(T value) implements CacheResult<T> {}

    record Miss<T>() implements CacheResult<T> {}

    record Error<T>(Throwable cause) implements CacheResult<T> {}

    static <T> CacheResult<T> hit(T value) {
        return new Hit<>(value);
    }

    static <T> CacheResult<T> miss() {
        return new Miss<>();
    }

    static <T> CacheResult<T> error(Throwable cause) {
        return new Error<>(cause);
    }
}
