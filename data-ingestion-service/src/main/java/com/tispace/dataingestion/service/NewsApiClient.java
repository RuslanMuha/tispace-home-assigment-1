package com.tispace.dataingestion.service;

import com.tispace.common.entity.Article;
import com.tispace.common.exception.ExternalApiException;
import com.tispace.common.exception.SerializationException;
import io.github.resilience4j.bulkhead.annotation.Bulkhead;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Resilience4j-wrapped client for NewsAPI with circuit breaker, retry, and bulkhead.
 * Protects against external API failures and prevents overload.
 * 
 * <p>Resilience patterns:
 * <ul>
 *   <li>Circuit breaker: Opens after threshold failures, prevents cascading failures</li>
 *   <li>Retry: Automatically retries transient failures</li>
 *   <li>Bulkhead: Limits concurrent requests to prevent resource exhaustion</li>
 * </ul>
 * 
 * <p>Fallback behavior: Returns empty list on all failures (circuit open, bulkhead full, retries exhausted).
 * This ensures system continues operating even when NewsAPI is unavailable.
 * 
 * <p>Side effects: External HTTP calls, metrics recording.
 */
@Service
@Slf4j
public class NewsApiClient implements ExternalApiClient {

    private final NewsApiClientCore core;
    private final NewsApiClientMetrics metrics;

    public NewsApiClient(NewsApiClientCore core, NewsApiClientMetrics metrics) {
        this.core = core;
        this.metrics = metrics;
    }

    /**
     * Fetches articles from NewsAPI with resilience patterns applied.
     * 
     * @param keyword search keyword (can be null/empty)
     * @param category article category (used for mapping, not API query)
     * @return list of articles (empty on fallback or API errors)
     * 
     * @throws ExternalApiException only for unexpected errors in metrics/fallback logic
     *         (normal API failures are handled by fallback)
     */
    @Override
    @CircuitBreaker(name = "newsApi", fallbackMethod = "fetchArticlesFallback")
    @Retry(name = "newsApi")
    @Bulkhead(name = "newsApi", fallbackMethod = "fetchArticlesFallback")
    public List<Article> fetchArticles(String keyword, String category) {
        metrics.onRequest();
        try {
            return metrics.recordLatency(() -> core.fetchArticles(keyword, category));
        } catch (ExternalApiException | SerializationException e) {
            metrics.onError();
            throw e;
        } catch (Exception e) {
            metrics.onError();
            throw new ExternalApiException("Unexpected error fetching articles from NewsAPI", e);
        }
    }

    /**
     * Fallback method invoked by Resilience4j when circuit breaker is open,
     * bulkhead is full, or all retries are exhausted.
     * 
     * <p>Returns empty list to allow system to continue operating.
     * This method is called by Resilience4j framework, not directly.
     * 
     * @param keyword search keyword (unused in fallback)
     * @param category article category (unused in fallback)
     * @param t the exception that triggered fallback
     * @return empty list (graceful degradation)
     */
    @SuppressWarnings("unused")
    public List<Article> fetchArticlesFallback(String keyword, String category, Throwable t) {

        metrics.onFallback();
        metrics.onError();

        log.warn("NewsAPI fallback used. keyword={}, category={}", keyword, category, t);
        return List.of();
    }

    @Override
    public String getApiName() {
        return "NewsAPI";
    }

}

