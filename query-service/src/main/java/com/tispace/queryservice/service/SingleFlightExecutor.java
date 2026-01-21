package com.tispace.queryservice.service;

public interface SingleFlightExecutor {
    <T> T execute(String key, Class<T> resultType, SingleFlightOperation<T> operation) throws Exception;

    @FunctionalInterface
    interface SingleFlightOperation<T> {
        T execute() throws Exception;
    }
}

