package com.tispace.dataingestion.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tispace.common.entity.Article;
import com.tispace.common.exception.ExternalApiException;
import org.apache.commons.lang3.StringUtils;
import com.tispace.dataingestion.adapter.NewsApiAdapter;
import com.tispace.dataingestion.constants.NewsApiConstants;
import com.tispace.dataingestion.mapper.NewsApiArticleMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class NewsApiClient implements ExternalApiClient {
	
	private final RestTemplate restTemplate;
	private final ObjectMapper objectMapper;
	private final NewsApiArticleMapper newsApiArticleMapper;
	
	@Value("${external-api.news-api.url:https://newsapi.org/v2/everything}")
	private String newsApiUrl;
	
	@Value("${external-api.news-api.api-key:}")
	private String apiKey;
	
	@Override
	public List<Article> fetchArticles(String keyword, String category) {
		try {
			log.info("Fetching articles from NewsAPI with keyword: {}, category: {}", keyword, category);
			
			String url = buildUrl(keyword, category);
			log.debug("NewsAPI URL: {}", url);
			
			ResponseEntity<String> response = restTemplate.getForEntity(url, String.class);
			
			if (!response.getStatusCode().is2xxSuccessful()) {
				throw new ExternalApiException(String.format("NewsAPI returned status: %s", response.getStatusCode()));
			}
			
			NewsApiAdapter adapter = objectMapper.readValue(response.getBody(), NewsApiAdapter.class);
			
			if (!NewsApiConstants.STATUS_OK.equalsIgnoreCase(adapter.getStatus())) {
				throw new ExternalApiException(String.format("NewsAPI returned status: %s", adapter.getStatus()));
			}
			
			return mapToArticles(adapter, category);
			
		} catch (ExternalApiException e) {
			throw e;
		} catch (Exception e) {
			log.error("Error fetching articles from NewsAPI", e);
			throw new ExternalApiException(String.format("Failed to fetch articles from NewsAPI: %s", e.getMessage()), e);
		}
	}
	
	private String buildUrl(String keyword, String category) {
		UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(newsApiUrl)
			.queryParam(NewsApiConstants.PARAM_API_KEY, apiKey)
			.queryParam(NewsApiConstants.PARAM_PAGE_SIZE, NewsApiConstants.DEFAULT_PAGE_SIZE)
			.queryParam(NewsApiConstants.PARAM_SORT_BY, NewsApiConstants.DEFAULT_SORT_BY);
		
		if (StringUtils.isNotEmpty(keyword)) {
			builder.queryParam(NewsApiConstants.PARAM_QUERY, keyword);
		}
		
		// Note: category parameter is not supported by /everything endpoint
		// Category is only used for setting the category field in Article entities
		// If using /top-headlines endpoint, category parameter would be supported
		
		return builder.toUriString();
	}
	
	private List<Article> mapToArticles(NewsApiAdapter adapter, String category) {
		List<Article> articles = new ArrayList<>();
		
		if (adapter.getArticles() == null) {
			return articles;
		}
		
		for (NewsApiAdapter.ArticleResponse articleResponse : adapter.getArticles()) {
			Article article = newsApiArticleMapper.toArticle(articleResponse);
			newsApiArticleMapper.updateCategory(article, category);
			
			if (StringUtils.isNotEmpty(article.getTitle())) {
				articles.add(article);
			}
		}
		
		return articles;
	}
	
	@Override
	public String getApiName() {
		return "NewsAPI";
	}
}

