package com.tispace.queryservice.service;

import com.tispace.common.dto.ArticleDTO;
import com.tispace.common.dto.SummaryDTO;
import com.tispace.common.exception.BusinessException;
import com.tispace.queryservice.cache.CacheResult;
import com.tispace.queryservice.cache.CacheService;
import com.tispace.queryservice.constants.ArticleConstants;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.UUID;

/**
 * Service for generating and caching article summaries.
 * Implements cache-aside pattern with single-flight protection against stampede.
 * Uses Strategy pattern (SummaryProvider) for extensibility.
 */
@Service
@Slf4j
public class ArticleSummaryService {

    private static final Duration ONE_HOUR = Duration.ofHours(1);
    private static final int DEFAULT_CACHE_TTL_HOURS = 24;

    private final CacheService cacheService;
    private final SummaryProvider summaryProvider;
    private final SingleFlightExecutor singleFlightExecutor;

    private final Duration cacheTtl;

    public ArticleSummaryService(
            CacheService cacheService,
            SummaryProvider summaryProvider,
            SingleFlightExecutor singleFlightExecutor,
            @Value("${cache.summary.ttl-hours:24}") int cacheTtlHours
    ) {
        this.cacheService = cacheService;
        this.summaryProvider = summaryProvider;
        this.singleFlightExecutor = singleFlightExecutor;

        if (cacheTtlHours <= 0) {
            log.warn("Invalid cache.summary.ttl-hours={} -> forcing to 24 hours (safe default).", cacheTtlHours);
            cacheTtlHours = DEFAULT_CACHE_TTL_HOURS;
        }
        this.cacheTtl = Duration.ofHours(cacheTtlHours);
    }

    /**
     * Gets summary for an article. The article data should be provided by the caller.
     * This method handles caching and ChatGPT generation with single-flight protection.
     *
     * @param articleId the article ID
     * @param article   the article data
     * @return the summary DTO
     * @throws IllegalArgumentException if articleId/article invalid
     * @throws IllegalStateException    if generated summary is empty or single-flight infrastructure is down
     */
    public SummaryDTO getSummary(UUID articleId, ArticleDTO article) {
        validateInput(articleId, article);

        log.debug("Fetching summary for articleId={}", articleId);

        // Cache-aside: check cache first
        String cacheKey = ArticleConstants.buildCacheKey(articleId);
        CacheResult<SummaryDTO> cached = cacheService.get(cacheKey, SummaryDTO.class);

        if (cached instanceof CacheResult.Hit<SummaryDTO>(SummaryDTO value)) {
            log.debug("Summary found in cache for articleId={}", articleId);
            return copyAsCached(value);
        }

        if (cached instanceof CacheResult.Error<SummaryDTO>(Throwable cause)) {
            throw new IllegalStateException("Cache unavailable, refusing to generate summary to protect provider", cause);
        }

        return getSummaryWithSingleFlight(articleId, article, cacheKey);
    }

    /**
     * Gets summary with single-flight protection to prevent concurrent generation.
     * Implements double-check locking: checks cache again after acquiring single-flight lock.
     * 
     * <p>This prevents race condition where multiple threads miss cache simultaneously
     * and all try to generate summary concurrently.
     */
    private SummaryDTO getSummaryWithSingleFlight(UUID articleId, ArticleDTO article, String cacheKey) {
        String singleFlightKey = ArticleConstants.buildSingleFlightKey(cacheKey);

        try {
            return singleFlightExecutor.execute(singleFlightKey, SummaryDTO.class, () -> {
                // Double-check cache after acquiring lock / becoming leader
                CacheResult<SummaryDTO> cachedAfterLock = cacheService.get(cacheKey, SummaryDTO.class);

                if (cachedAfterLock instanceof CacheResult.Hit<SummaryDTO>(SummaryDTO value)) {
                    return copyAsCached(value);
                }

                if (cachedAfterLock instanceof CacheResult.Error<SummaryDTO>(Throwable cause)) {
                    throw new IllegalStateException("Cache unavailable, refusing to generate summary to protect provider", cause);
                }

                // Generate and cache summary
                return generateAndCacheSummary(articleId, article, cacheKey);
            });
        } catch (Exception e) {
            log.error("Single-flight execution failed for articleId={}, cacheKey={}", articleId, cacheKey, e);
            throw new IllegalStateException("Summary generation temporarily unavailable", e);
        }
    }

    /**
     * Generates summary using configured SummaryProvider and caches it.
     */
    private SummaryDTO generateAndCacheSummary(UUID articleId, ArticleDTO article, String cacheKey) {
        final String summaryText;

        try {
            summaryText = summaryProvider.generateSummary(article);
        } catch (Exception e) {
            log.error("Error generating summary using provider={} for articleId={}",
                    summaryProvider.getProviderName(), articleId, e);
            throw new IllegalStateException("Failed to generate summary", e);
        }

        if (summaryText == null || StringUtils.isBlank(summaryText)) {
            log.error("Generated summary is null/blank for articleId={}", articleId);
            throw new IllegalStateException("Generated summary is empty");
        }

        var summaryDTO = SummaryDTO.builder()
                .articleId(articleId)
                .summary(summaryText)
                .cached(false)
                .build();

        cacheSummary(cacheKey, summaryDTO);

        return summaryDTO;
    }

    /**
     * Caches the summary with configured TTL.
     * Fails silently if caching fails - cache is optional and shouldn't break business logic.
     * 
     * <p>TTL is enforced to be at least 1 hour (safety constraint).
     */
    private void cacheSummary(String cacheKey, SummaryDTO summaryDTO) {

        long ttlSeconds = Math.max(cacheTtl.getSeconds(), ONE_HOUR.getSeconds());

        try {
            cacheService.put(cacheKey, summaryDTO, ttlSeconds);
            log.debug("Successfully cached summary. cacheKey={}, articleId={}, ttlSeconds={}",
                    cacheKey, summaryDTO.getArticleId(), ttlSeconds);
        } catch (Exception e) {

            log.warn("Failed to cache summary. cacheKey={}, articleId={}. Returning result without cache.",
                    cacheKey, summaryDTO.getArticleId(), e);

        }
    }

    /**
     * Validates input parameters.
     */
    private void validateInput(UUID articleId, ArticleDTO article) {

        if (articleId == null) {
            throw new IllegalArgumentException("Article ID is required");
        }
        if (article == null) {
            throw new IllegalArgumentException("Article cannot be null");
        }

        // Validate that article ID in path matches article ID in body (if present)
        if (article.getId() != null && !article.getId().equals(articleId)) {
            log.warn("Article ID mismatch: path={}, body={}", articleId, article.getId());
            throw new BusinessException(String.format("Article ID in path (%d) does not match ID in body (%d)",
                    articleId, article.getId()));
        }

    }

    private SummaryDTO copyAsCached(SummaryDTO cached) {
        return SummaryDTO.builder()
                .articleId(cached.getArticleId())
                .summary(cached.getSummary())
                .cached(true)
                .build();
    }
}