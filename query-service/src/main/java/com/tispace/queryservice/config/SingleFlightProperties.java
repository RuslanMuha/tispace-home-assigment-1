package com.tispace.queryservice.config;

import jakarta.validation.constraints.Min;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Getter
@Setter
@Validated
@ConfigurationProperties(prefix = "singleflight")
public class SingleFlightProperties {

    /**
     * Redis lock TTL in seconds.
     * Should cover worst-case operation duration.
     */
    @Min(1)
    private long lockTimeoutSeconds = 30;

    /**
     * Max time followers wait for the result (seconds).
     */
    @Min(1)
    private long inFlightTimeoutSeconds = 10;

    /**
     * TTL for cached result envelope (seconds).
     */
    @Min(1)
    private long resultTtlSeconds = 30;

    /**
     * Backoff polling initial delay (ms).
     */
    @Min(1)
    private long pollInitialMs = 20;

    /**
     * Backoff polling max delay (ms).
     */
    @Min(1)
    private long pollMaxMs = 200;

    /**
     * Backoff multiplier (>=1).
     */
    @Min(1)
    private long pollMultiplier = 2;
}
