package com.tispace.dataingestion.service;

import com.tispace.common.entity.Article;
import org.apache.commons.lang3.StringUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * Orchestrates data ingestion from external APIs and persistence to database.
 * Handles validation, filtering, and deduplication of articles before saving.
 * 
 * <p>Side effects: Makes external API calls, writes to database via ArticlePersistenceService.
 * 
 * <p>Guarantees: Only valid articles (non-null, non-empty title) are persisted.
 * Duplicates are handled by ArticlePersistenceService using ON CONFLICT.
 * 
 * <p>Edge cases: Empty API responses are handled gracefully (returns early).
 * Invalid articles are skipped with warning logs.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DataIngestionService {
	
	private final ExternalApiClient externalApiClient;
	private final ArticlePersistenceService articlePersistenceService;
	
	@Value("${scheduler.keyword:technology}")
	private String defaultKeyword;
	
	@Value("${scheduler.category:technology}")
	private String defaultCategory;

	/**
	 * Ingests articles from external API and saves them to database.
	 * Uses default keyword/category if parameters are null or empty.
	 * 
	 * @param keyword search keyword (falls back to default if empty)
	 * @param category article category (falls back to default if empty)
	 * 
	 * @throws ExternalApiException if external API call fails (propagated from ExternalApiClient)
	 */
	public void ingestData(String keyword, String category) {
		log.info("Starting data ingestion with keyword: {}, category: {}", keyword, category);
		
		String searchKeyword = StringUtils.isNotEmpty(keyword) ? keyword : defaultKeyword;
		String searchCategory = StringUtils.isNotEmpty(category) ? category : defaultCategory;

		List<Article> articles = externalApiClient.fetchArticles(searchKeyword, searchCategory);
		
		log.info("Fetched {} articles from {}", articles.size(), externalApiClient.getApiName());
		
		if (articles.isEmpty()) {
			log.warn("No articles fetched from {}", externalApiClient.getApiName());
			return;
		}

		List<Article> validArticles = new ArrayList<>(articles.size());
		int skippedCount = 0;
		
		for (Article article : articles) {
			if (article != null && StringUtils.isNotEmpty(article.getTitle())) {
				validArticles.add(article);
			} else {
				skippedCount++;
			}
		}
		
		if (skippedCount > 0) {
			log.warn("Skipped {} articles due to missing or invalid title", skippedCount);
		}
		
		if (validArticles.isEmpty()) {
			log.warn("No valid articles to save");
			return;
		}

		int savedCount = articlePersistenceService.saveArticles(validArticles);
		
		log.info("Successfully saved {} new articles to database ({} skipped due to duplicates)", 
				savedCount, validArticles.size() - savedCount);
	}

	public void ingestData() {
		ingestData(defaultKeyword, defaultCategory);
	}
}

