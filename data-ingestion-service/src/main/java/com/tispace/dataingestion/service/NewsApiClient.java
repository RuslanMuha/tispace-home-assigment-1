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

@Service
@Slf4j
public class NewsApiClient implements ExternalApiClient {

    private final NewsApiClientCore core;
    private final NewsApiClientMetrics metrics;

    public NewsApiClient(NewsApiClientCore core, NewsApiClientMetrics metrics) {
        this.core = core;
        this.metrics = metrics;
    }

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

