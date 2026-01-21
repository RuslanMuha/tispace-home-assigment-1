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

    @Min(1)
    private long lockTimeoutSeconds = 30;

    @Min(1)
    private long inFlightTimeoutSeconds = 10;

    @Min(1)
    private long resultTtlSeconds = 30;

    @Min(1)
    private long pollInitialMs = 20;

    @Min(1)
    private long pollMaxMs = 200;

    @Min(1)
    private long pollMultiplier = 2;
}
