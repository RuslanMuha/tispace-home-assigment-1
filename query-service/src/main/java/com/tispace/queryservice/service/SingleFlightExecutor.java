package com.tispace.queryservice.service;

/**
 * Interface for executing operations with single-flight pattern.
 * Ensures that only one execution happens for the same key, even with concurrent requests.
 */
public interface SingleFlightExecutor {


    /**
     * Executes the operation with distributed single-flight pattern.
     * If multiple requests for the same key happen concurrently across instances,
     * only one "leader" executes the operation and stores the result,
     * while others wait for the result.
     *
     * @param key the unique key for the operation
     * @param resultType class of the result type (needed for safe deserialization)
     * @param operation the operation to execute
     * @param <T> the return type
     * @return the result of the operation
     * @throws Exception if the operation fails or waiting times out
     */
    <T> T execute(String key, Class<T> resultType, SingleFlightOperation<T> operation) throws Exception;

    /**
     * Functional interface for operations executed with single-flight pattern.
     *
     * @param <T> the return type
     */
    @FunctionalInterface
    interface SingleFlightOperation<T> {
        T execute() throws Exception;
    }
}

