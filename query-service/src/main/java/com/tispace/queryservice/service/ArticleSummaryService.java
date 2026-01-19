package com.tispace.queryservice.service;

import com.tispace.common.dto.ArticleDTO;
import com.tispace.common.dto.SummaryDTO;
import com.tispace.queryservice.constants.ArticleConstants;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Service for generating and caching article summaries.
 * Implements cache-aside pattern with single-flight protection against stampede.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ArticleSummaryService {
	
	private static final int SECONDS_PER_HOUR = 60 * 60;
	
	private final CacheService cacheService;
	private final ChatGptService chatGptService;
	private final SingleFlightExecutor singleFlightExecutor;
	
	@Value("${cache.summary.ttl-hours:24}")
	private int cacheTtlHours;
	
	/**
	 * Gets summary for an article. The article data should be provided by the caller.
	 * This method handles caching and ChatGPT generation with single-flight protection.
	 *
	 * @param articleId the article ID
	 * @param article the article data
	 * @return the summary DTO
	 * @throws IllegalArgumentException if articleId or article is null
	 * @throws IllegalStateException if generated summary is empty
	 */
	public SummaryDTO getSummary(Long articleId, ArticleDTO article) {
		validateInput(articleId, article);
		
		log.debug("Fetching summary for article with id: {}", articleId);
		
		// Check cache first
		String cacheKey = ArticleConstants.buildCacheKey(articleId);
		SummaryDTO cachedSummary = cacheService.get(cacheKey, SummaryDTO.class);
		
		if (cachedSummary != null) {
			log.debug("Summary found in cache for article id: {}", articleId);
			cachedSummary.setCached(true);
			return cachedSummary;
		}

		return getSummaryWithSingleFlight(articleId, article, cacheKey);
	}
	
	/**
	 * Gets summary with single-flight protection to prevent concurrent generation.
	 */
	private SummaryDTO getSummaryWithSingleFlight(Long articleId, ArticleDTO article, String cacheKey) {
		String singleFlightKey = "summary:" + articleId;
		
		try {
            return singleFlightExecutor.execute(singleFlightKey, SummaryDTO.class, () -> {
                // Double-check cache after acquiring lock / becoming leader
                SummaryDTO cachedSummary = cacheService.get(cacheKey, SummaryDTO.class);
                if (cachedSummary != null) {
                    log.debug("Summary found in cache after lock acquisition for article id: {}", articleId);
                    cachedSummary.setCached(true);
                    return cachedSummary;
                }

                // Generate and cache summary
                return generateAndCacheSummary(articleId, article, cacheKey);
            });
		} catch (Exception e) {
			log.error("Error executing single-flight operation for article id: {}", articleId, e);
			// Fallback: generate directly without single-flight protection
			return generateAndCacheSummary(articleId, article, cacheKey);
		}
	}
	
	/**
	 * Generates summary using ChatGPT and caches it.
	 */
	private SummaryDTO generateAndCacheSummary(Long articleId, ArticleDTO article, String cacheKey) {
		String summary = chatGptService.generateSummary(article);
		
		if (summary == null || summary.trim().isEmpty()) {
			log.error("Generated summary is null or empty for article id: {}", articleId);
			throw new IllegalStateException("Generated summary is empty");
		}
		
		SummaryDTO summaryDTO = SummaryDTO.builder()
			.articleId(articleId)
			.summary(summary)
			.cached(false)
			.build();
		
		// Cache the summary (fail silently if cache fails)
		cacheSummary(cacheKey, summaryDTO);
		
		return summaryDTO;
	}
	
	/**
	 * Caches the summary with configured TTL.
	 * Fails silently if caching fails - cache is optional.
	 */
	private void cacheSummary(String cacheKey, SummaryDTO summaryDTO) {
		long ttlSeconds = (long) cacheTtlHours * SECONDS_PER_HOUR;
		if (ttlSeconds > 0) {
			try {
				cacheService.put(cacheKey, summaryDTO, ttlSeconds);
			} catch (Exception e) {
				log.warn("Failed to cache summary for key: {}. Summary will still be returned.", cacheKey, e);
				// Fail silently - cache is optional
			}
		} else {
			log.warn("Invalid TTL calculation (ttlHours: {}). Skipping cache.", cacheTtlHours);
		}
	}
	
	/**
	 * Validates input parameters.
	 */
	private void validateInput(Long articleId, ArticleDTO article) {
		if (articleId == null) {
			throw new IllegalArgumentException("Article ID cannot be null");
		}
		
		if (article == null) {
			throw new IllegalArgumentException("Article cannot be null");
		}
	}
}
