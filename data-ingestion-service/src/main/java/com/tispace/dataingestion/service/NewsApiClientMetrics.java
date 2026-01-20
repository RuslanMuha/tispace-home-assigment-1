package com.tispace.dataingestion.service;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

import java.util.concurrent.Callable;

@Component
public class NewsApiClientMetrics {

    private final Counter requests;
    private final Counter errors;
    private final Counter fallback;
    private final Timer latency;

    public NewsApiClientMetrics(MeterRegistry registry) {
        this.requests = Counter.builder("external_api_requests_total")
                .description("External API requests")
                .tag("client", "newsapi")
                .register(registry);

        this.errors = Counter.builder("external_api_errors_total")
                .description("External API errors")
                .tag("client", "newsapi")
                .register(registry);

        this.fallback = Counter.builder("external_api_fallback_total")
                .description("External API fallbacks")
                .tag("client", "newsapi")
                .register(registry);

        this.latency = Timer.builder("external_api_latency_seconds")
                .description("External API latency")
                .tag("client", "newsapi")
                .register(registry);
    }

    public void onRequest() { requests.increment(); }
    public void onError() { errors.increment(); }
    public void onFallback() { fallback.increment(); }

    public <T> T recordLatency(Callable<T> callable) throws Exception {
        return latency.recordCallable(callable);
    }
}
