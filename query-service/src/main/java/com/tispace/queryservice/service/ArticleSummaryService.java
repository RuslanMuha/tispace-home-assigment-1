package com.tispace.queryservice.service;

import com.tispace.common.contract.ArticleDTO;
import com.tispace.common.contract.SummaryDTO;
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
 * Generates and caches article summaries.
 * Uses cache-aside with single-flight to prevent concurrent generation of the same summary.
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

    public SummaryDTO getSummary(UUID articleId, ArticleDTO article) {
        validateInput(articleId, article);

        log.debug("Fetching summary for articleId={}", articleId);

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
     * Double-checks cache after acquiring single-flight lock to prevent concurrent generation.
     */
    private SummaryDTO getSummaryWithSingleFlight(UUID articleId, ArticleDTO article, String cacheKey) {
        String singleFlightKey = ArticleConstants.buildSingleFlightKey(cacheKey);

        try {
            return singleFlightExecutor.execute(singleFlightKey, SummaryDTO.class, () -> {
                CacheResult<SummaryDTO> cachedAfterLock = cacheService.get(cacheKey, SummaryDTO.class);

                if (cachedAfterLock instanceof CacheResult.Hit<SummaryDTO>(SummaryDTO value)) {
                    return copyAsCached(value);
                }

                if (cachedAfterLock instanceof CacheResult.Error<SummaryDTO>(Throwable cause)) {
                    throw new IllegalStateException("Cache unavailable, refusing to generate summary to protect provider", cause);
                }

                return generateAndCacheSummary(articleId, article, cacheKey);
            });
        } catch (Exception e) {
            log.error("Single-flight execution failed for articleId={}, cacheKey={}", articleId, cacheKey, e);
            throw new IllegalStateException("Summary generation temporarily unavailable", e);
        }
    }

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
     * Caches summary with TTL (min 1 hour). Fails silently - cache is optional.
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

    private void validateInput(UUID articleId, ArticleDTO article) {

        if (articleId == null) {
            throw new IllegalArgumentException("Article ID is required");
        }
        if (article == null) {
            throw new IllegalArgumentException("Article cannot be null");
        }

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