package com.tispace.dataingestion.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tispace.common.entity.Article;
import com.tispace.common.exception.ExternalApiException;
import com.tispace.common.exception.SerializationException;
import com.tispace.common.validation.ArticleValidator;
import com.tispace.dataingestion.adapter.NewsApiAdapter;
import com.tispace.dataingestion.constants.NewsApiConstants;
import com.tispace.dataingestion.mapper.NewsApiArticleMapper;
import io.github.resilience4j.bulkhead.annotation.Bulkhead;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.ArrayList;
import java.util.List;

@Service
@Slf4j
public class NewsApiClient implements ExternalApiClient {

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final NewsApiArticleMapper newsApiArticleMapper;
    private final ArticleValidator articleValidator;

    private final Counter requestsCounter;
    private final Counter errorsCounter;
    private final Counter fallbackCounter;
    private final Timer latencyTimer;

    private final String newsApiUrl;
    private final String apiKey;

    public NewsApiClient(
            RestTemplate restTemplate,
            ObjectMapper objectMapper,
            NewsApiArticleMapper newsApiArticleMapper,
            ArticleValidator articleValidator,
            MeterRegistry meterRegistry,
            @Value("${external-api.news-api.url:https://newsapi.org/v2/everything}") String newsApiUrl,
            @Value("${external-api.news-api.api-key:}") String apiKey
    ) {
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
        this.newsApiArticleMapper = newsApiArticleMapper;
        this.articleValidator = articleValidator;

        this.newsApiUrl = newsApiUrl;
        this.apiKey = apiKey;

        if (StringUtils.isBlank(apiKey)) {
            throw new IllegalStateException("NewsAPI apiKey is missing (external-api.news-api.api-key)");
        }

        this.requestsCounter = Counter.builder("newsapi.requests")
                .description("Number of NewsAPI requests")
                .tag("client", "newsapi")
                .register(meterRegistry);

        this.errorsCounter = Counter.builder("newsapi.errors")
                .description("Number of NewsAPI errors")
                .tag("client", "newsapi")
                .register(meterRegistry);

        this.fallbackCounter = Counter.builder("newsapi.fallback.used")
                .description("Number of NewsAPI fallback invocations")
                .tag("client", "newsapi")
                .register(meterRegistry);

        this.latencyTimer = Timer.builder("newsapi.latency")
                .description("NewsAPI request latency")
                .tag("client", "newsapi")
                .register(meterRegistry);
    }

    @Override
    @CircuitBreaker(name = "newsApi", fallbackMethod = "fetchArticlesFallback")
    @Retry(name = "newsApi")
    @Bulkhead(name = "newsApi", fallbackMethod = "fetchArticlesFallback")
    public List<Article> fetchArticles(String keyword, String category) {
        requestsCounter.increment();

        try {
            return latencyTimer.recordCallable(() -> doFetchArticles(keyword, category));
        } catch (ExternalApiException | SerializationException e) {
            errorsCounter.increment();
            throw e;
        } catch (Exception e) {
            errorsCounter.increment();
            throw new ExternalApiException("Unexpected error fetching articles from NewsAPI", e);
        }
    }


    private List<Article> doFetchArticles(String keyword, String category) {

        log.debug("Fetching articles from NewsAPI. keyword={}, category={}", keyword, category);

        String url = buildUrl(keyword);
        log.debug("NewsAPI URL (masked): {}", maskApiKey(url));

        ResponseEntity<String> response;
        try {
            response = restTemplate.getForEntity(url, String.class);
        } catch (RestClientException e) {
            throw new ExternalApiException("NewsAPI call failed (transport error)", e);
        }

        if (!response.getStatusCode().is2xxSuccessful()) {
            throw new ExternalApiException("NewsAPI returned HTTP status: " + response.getStatusCode());
        }

        String body = response.getBody();
        if (StringUtils.isBlank(body)) {
            log.warn("NewsAPI returned empty response body. keyword={}, category={}", keyword, category);
            return List.of();
        }

        NewsApiAdapter adapter = parseResponse(body);

        if (!NewsApiConstants.STATUS_OK.equalsIgnoreCase(adapter.getStatus())) {
            throw new ExternalApiException("NewsAPI returned status: " + adapter.getStatus());
        }

        return mapToArticles(adapter, category);
    }

    private NewsApiAdapter parseResponse(String responseBody) {
        try {
            return objectMapper.readValue(responseBody, NewsApiAdapter.class);
        } catch (JsonProcessingException e) {
            log.error("Failed to parse NewsAPI response as JSON", e);
            throw new SerializationException("Failed to parse NewsAPI response", e);
        }
    }


    private String buildUrl(String keyword) {
        UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(newsApiUrl)
                .queryParam(NewsApiConstants.PARAM_API_KEY, apiKey)
                .queryParam(NewsApiConstants.PARAM_PAGE_SIZE, NewsApiConstants.DEFAULT_PAGE_SIZE)
                .queryParam(NewsApiConstants.PARAM_SORT_BY, NewsApiConstants.DEFAULT_SORT_BY);

        if (StringUtils.isNotBlank(keyword)) {
            builder.queryParam(NewsApiConstants.PARAM_QUERY, keyword);
        }

        return builder.toUriString();
    }

    /**
     * Masks API key in URL for logging purposes.
     */
    private String maskApiKey(String url) {
        if (url == null || StringUtils.isBlank(apiKey)) {
            return url;
        }
        return url.replace("apiKey=" + apiKey, "apiKey=***");
    }

    private List<Article> mapToArticles(NewsApiAdapter adapter, String category) {
        List<NewsApiAdapter.ArticleResponse> raw = (adapter != null) ? adapter.getArticles() : null;
        if (raw == null || raw.isEmpty()) {
            return List.of();
        }

        List<Article> articles = new ArrayList<>(raw.size());
        MappingStatistics stats = new MappingStatistics();

        for (NewsApiAdapter.ArticleResponse articleResponse : raw) {
            if (articleResponse == null) {
                stats.skippedNull++;
                continue;
            }

            try {
                Article article = newsApiArticleMapper.toArticle(articleResponse);
                if (article == null) {
                    stats.skippedMapping++;
                    continue;
                }

                newsApiArticleMapper.updateCategory(article, category);

                if (!articleValidator.isValid(article)) {
                    stats.skippedInvalid++;
                    continue;
                }

                articles.add(article);

            } catch (Exception e) {
                stats.mappingErrors++;
                if (stats.mappingErrors <= 3) {
                    log.warn("Error mapping NewsAPI article, skipping. cause={}", e.toString());
                }
            }
        }

        if (stats.hasSkipped()) {
            log.debug("NewsAPI mapping stats: mapped={}, skippedTotal={}, null={}, mappingNull={}, invalid={}, errors={}",
                    articles.size(), stats.totalSkipped(),
                    stats.skippedNull, stats.skippedMapping, stats.skippedInvalid, stats.mappingErrors);
        }

        return articles;
    }

    private static class MappingStatistics {
        int skippedNull = 0;
        int skippedMapping = 0;
        int skippedInvalid = 0;
        int mappingErrors = 0;

        int totalSkipped() {
            return skippedNull + skippedMapping + skippedInvalid + mappingErrors;
        }

        boolean hasSkipped() {
            return totalSkipped() > 0;
        }
    }

    @SuppressWarnings("unused")
    public List<Article> fetchArticlesFallback(String keyword, String category, Throwable t) {
        fallbackCounter.increment();
        errorsCounter.increment();

        log.warn("NewsAPI fallback used. keyword={}, category={}", keyword, category, t);

        return List.of();
    }

    @Override
    public String getApiName() {
        return "NewsAPI";
    }

}

