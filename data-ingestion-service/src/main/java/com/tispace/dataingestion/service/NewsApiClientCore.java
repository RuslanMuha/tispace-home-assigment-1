package com.tispace.dataingestion.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tispace.dataingestion.domain.entity.Article;
import com.tispace.common.exception.ExternalApiException;
import com.tispace.common.exception.SerializationException;
import com.tispace.dataingestion.domain.validation.ArticleValidator;
import com.tispace.dataingestion.adapter.NewsApiAdapter;
import com.tispace.dataingestion.constants.NewsApiConstants;
import com.tispace.dataingestion.mapper.NewsApiArticleMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Core NewsAPI client: HTTP calls, JSON parsing, article mapping.
 * Invalid articles are skipped silently. Requires apiKey at startup.
 */
@Service
@Slf4j
public class NewsApiClientCore {

    private static final int DEBUG_SAMPLE_LIMIT = 3;

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final NewsApiArticleMapper mapper;
    private final ArticleValidator validator;
    private final NewsApiClientMetrics metrics;
    private final String newsApiUrl;
    private final String apiKey;

    public NewsApiClientCore(
            RestTemplate restTemplate,
            ObjectMapper objectMapper,
            NewsApiArticleMapper mapper,
            ArticleValidator validator,
            NewsApiClientMetrics metrics,
            @Value("${external-api.news-api.url:https://newsapi.org/v2/everything}") String newsApiUrl,
            @Value("${external-api.news-api.api-key:}") String apiKey
    ) {
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
        this.mapper = mapper;
        this.validator = validator;
        this.metrics = metrics;
        this.newsApiUrl = newsApiUrl;
        this.apiKey = apiKey;

        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("NewsAPI apiKey is missing (external-api.news-api.api-key)");
        }
    }

    public List<Article> fetchArticles(String keyword, String category) {
        String url = buildUrl(keyword);

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
        if (body == null || body.isBlank()) {
            return List.of();
        }

        NewsApiAdapter adapter = parseResponse(body);

        if (!NewsApiConstants.STATUS_OK.equalsIgnoreCase(adapter.getStatus())) {
            throw new ExternalApiException("NewsAPI returned status: " + adapter.getStatus());
        }

        return mapToArticles(adapter, category);
    }

    private NewsApiAdapter parseResponse(String body) {
        try {
            return objectMapper.readValue(body, NewsApiAdapter.class);
        } catch (JsonProcessingException e) {
            throw new SerializationException("Failed to parse NewsAPI response", e);
        }
    }

    private String buildUrl(String keyword) {
        var builder = UriComponentsBuilder.fromUriString(newsApiUrl)
                .queryParam(NewsApiConstants.PARAM_API_KEY, apiKey)
                .queryParam(NewsApiConstants.PARAM_PAGE_SIZE, NewsApiConstants.DEFAULT_PAGE_SIZE)
                .queryParam(NewsApiConstants.PARAM_SORT_BY, NewsApiConstants.DEFAULT_SORT_BY);

        if (keyword != null && !keyword.isBlank()) {
            builder.queryParam(NewsApiConstants.PARAM_QUERY, keyword);
        }

        return builder.toUriString();
    }

    private List<Article> mapToArticles(NewsApiAdapter adapter, String category) {
        var raw = adapter.getArticles();
        if (raw == null || raw.isEmpty()) return List.of();

        List<Article> result = new ArrayList<>(raw.size());
        int droppedCount = 0;
        var reasonCounts = new LinkedHashMap<String, Integer>();
        int debugSamples = 0;

        for (var r : raw) {
            if (r == null) continue;

            try {
                Article article = mapper.toArticle(r);
                if (article == null) {
                    droppedCount++;
                    reasonCounts.merge("mapping returned null", 1, Integer::sum);
                    if (debugSamples++ < DEBUG_SAMPLE_LIMIT) {
                        log.debug("Article dropped: title={}, reason=mapping returned null", r.getTitle());
                    }
                    metrics.onArticleDropped();
                    continue;
                }

                mapper.updateCategory(article, category);

                if (!validator.isValid(article)) {
                    droppedCount++;
                    reasonCounts.merge("validation failed", 1, Integer::sum);
                    if (debugSamples++ < DEBUG_SAMPLE_LIMIT) {
                        log.debug("Article dropped: title={}, reason=validation failed", article.getTitle());
                    }
                    metrics.onArticleDropped();
                    continue;
                }

                result.add(article);
            } catch (Exception e) {
                droppedCount++;
                String reason = safeReason(e.getMessage());
                reasonCounts.merge(reason, 1, Integer::sum);
                if (debugSamples++ < DEBUG_SAMPLE_LIMIT) {
                    String title = null;
                    try { title = r.getTitle(); } catch (Exception ignored) { }
                    log.debug("Article dropped: title={}, reason={}", title != null ? title : "(no title)", reason);
                }
                metrics.onArticleDropped();
            }
        }

        if (droppedCount > 0) {
            var topReasons = reasonCounts.entrySet().stream()
                    .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                    .limit(3)
                    .map(entry -> entry.getKey() + "=" + entry.getValue())
                    .toList();
            log.warn("Dropped articles during mapping/validation: count={}, topReasons={}", droppedCount, topReasons);
        }

        return result;
    }

    private static String safeReason(String reason) {
        if (reason == null || reason.isBlank()) return "unknown";
        return reason.length() > 200 ? reason.substring(0, 200) + "..." : reason;
    }
}
