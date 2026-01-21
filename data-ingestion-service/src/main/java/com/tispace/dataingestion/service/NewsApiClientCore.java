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
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.ArrayList;
import java.util.List;

/**
 * Core implementation for NewsAPI integration.
 * Handles HTTP requests, JSON parsing, and article mapping/validation.
 * 
 * <p>Validation: Only articles passing ArticleValidator are returned.
 * Invalid articles are silently skipped (no exceptions thrown).
 * 
 * <p>Error handling:
 * <ul>
 *   <li>Transport errors → ExternalApiException</li>
 *   <li>Non-2xx HTTP → ExternalApiException</li>
 *   <li>JSON parsing errors → SerializationException</li>
 *   <li>NewsAPI status != "ok" → ExternalApiException</li>
 * </ul>
 * 
 * <p>Side effects: External HTTP calls to NewsAPI.
 * 
 * <p>Constraints: apiKey must be configured at startup (throws IllegalStateException if missing).
 */
@Service
@Slf4j
public class NewsApiClientCore {

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final NewsApiArticleMapper mapper;
    private final ArticleValidator validator;
    private final String newsApiUrl;
    private final String apiKey;

    public NewsApiClientCore(
            RestTemplate restTemplate,
            ObjectMapper objectMapper,
            NewsApiArticleMapper mapper,
            ArticleValidator validator,
            @Value("${external-api.news-api.url:https://newsapi.org/v2/everything}") String newsApiUrl,
            @Value("${external-api.news-api.api-key:}") String apiKey
    ) {
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
        this.mapper = mapper;
        this.validator = validator;
        this.newsApiUrl = newsApiUrl;
        this.apiKey = apiKey;

        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("NewsAPI apiKey is missing (external-api.news-api.api-key)");
        }
    }

    /**
     * Fetches and processes articles from NewsAPI.
     * 
     * @param keyword search keyword (optional, can be null/empty)
     * @param category category to assign to articles (not used in API query, only for mapping)
     * @return list of valid articles (empty if API returns no articles or all are invalid)
     * 
     * @throws ExternalApiException if HTTP call fails, non-2xx response, or NewsAPI status != "ok"
     * @throws SerializationException if JSON parsing fails
     */
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
        for (var r : raw) {
            if (r == null) continue;

            try {
                Article article = mapper.toArticle(r);
                if (article == null) continue;

                mapper.updateCategory(article, category);

                if (!validator.isValid(article)) continue;

                result.add(article);
            } catch (Exception ignored) {
                // skip broken item
            }
        }
        return result;
    }
}
