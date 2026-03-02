package com.tispace.dataingestion.client;

import com.tispace.common.contract.ArticleDTO;
import com.tispace.common.contract.SummaryDTO;
import com.tispace.common.exception.ExternalApiException;
import com.tispace.common.exception.RateLimitExceededException;
import com.tispace.common.util.LogRateLimiter;
import io.github.resilience4j.bulkhead.annotation.Bulkhead;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.ratelimiter.RequestNotPermitted;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.util.UUID;

/**
 * Internal query-service client with circuit breaker, retry, and bulkhead.
 * Returns degraded summary on circuit breaker failures; throws on rate limit/bulkhead.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class QueryServiceClient {
	
	@Value("${services.query-service.url:http://query-service:8082}")
	private String queryServiceUrl;

    private final RestTemplate queryServiceRestTemplate;
	
	private static final String SUMMARY_ENDPOINT = "/internal/summary";
	private static final LogRateLimiter LOG_LIMITER = LogRateLimiter.getInstance();
	private static final Duration CB_LOG_WINDOW = Duration.ofSeconds(10);
	
	@CircuitBreaker(name = "queryService", fallbackMethod = "getArticleSummaryFallback")
	@Retry(name = "queryService")
	@Bulkhead(name = "queryService", fallbackMethod = "getArticleSummaryFallback")
	public SummaryDTO getArticleSummary(UUID articleId, ArticleDTO article) {
		String url = String.format("%s%s/%s", queryServiceUrl, SUMMARY_ENDPOINT, articleId);
		
		HttpEntity<ArticleDTO> request = new HttpEntity<>(article);
		try {
			ResponseEntity<SummaryDTO> response = queryServiceRestTemplate.exchange(
				url,
				HttpMethod.POST,
				request,
				SummaryDTO.class
			);
			
			if (response.getBody() == null) {
				log.warn("Query-service returned null response body with status: {}", response.getStatusCode());
				throw new ExternalApiException("Query-service returned empty response");
			}
			
			// Keep a defensive branch for mocked/non-default RestTemplate error handlers.
			if (response.getStatusCode() != HttpStatus.OK) {
				log.warn("Unexpected response from query-service: {}", response.getStatusCode());
				throw new ExternalApiException(String.format("Failed to get summary from query-service: status %s", response.getStatusCode()));
			}
			
			return response.getBody();
		} catch (HttpStatusCodeException e) {
			if (e.getStatusCode().is4xxClientError()) {
				log.warn("Query-service client error. status={}, articleId={}", e.getStatusCode(), articleId);
				throw new ExternalApiException(
					String.format("Query-service request failed with client error: status %s", e.getStatusCode()),
					e
				);
			}
			throw e;
		}
	}
	
	/**
	 * Fallback: returns degraded summary on circuit breaker failures.
	 * Throws on rate limit/bulkhead failures. Called by Resilience4j, not directly.
	 */
	@SuppressWarnings("unused")
	private SummaryDTO getArticleSummaryFallback(UUID articleId, ArticleDTO article, Exception e) {
		if (e instanceof RequestNotPermitted) {
			log.warn("Rate limit exceeded for query-service. Article id: {}", articleId);
			throw new RateLimitExceededException("Client-side rate limit exceeded. Please try again later.", e);
		}
		
		String exceptionType = e != null ? e.getClass().getSimpleName() : "Unknown";
		if (exceptionType.contains("Bulkhead")) {
			log.warn("Query-service bulkhead is full (max concurrent calls reached). Article id: {}", articleId);
			throw new ExternalApiException("Service is currently overloaded. Please try again later.", e);
		}
		
		// For circuit breaker or connection errors, return a degraded response
		if (LOG_LIMITER.shouldLog("query_service:cb_open", CB_LOG_WINDOW)) {
			log.error("Query-service circuit breaker is open or service unavailable. Article id: {}. Error: [{}] {}",
				articleId, e != null ? e.getClass().getSimpleName() : "Unknown", e != null ? e.getMessage() : "Unknown error");
		}
		log.debug("Query-service CB open/unavailable. Article id: {}", articleId, e);
		
		return SummaryDTO.builder()
			.articleId(articleId)
			.summary("Summary generation is temporarily unavailable. Please try again later.")
			.cached(false)
			.build();
	}
}

