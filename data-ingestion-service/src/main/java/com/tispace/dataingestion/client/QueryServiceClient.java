package com.tispace.dataingestion.client;

import com.tispace.common.dto.ArticleDTO;
import com.tispace.common.dto.SummaryDTO;
import com.tispace.common.exception.ExternalApiException;
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
import org.springframework.web.client.RestTemplate;

import java.util.UUID;

/**
 * Client for internal query-service communication with resilience patterns.
 * Protects against service unavailability and overload.
 * 
 * <p>Resilience patterns:
 * <ul>
 *   <li>Circuit breaker: Opens after threshold failures</li>
 *   <li>Retry: Retries transient failures</li>
 *   <li>Bulkhead: Limits concurrent requests (thread pool isolation)</li>
 * </ul>
 * 
 * <p>Fallback behavior: Returns degraded response (stub summary) instead of throwing exception
 * for better user experience. Rate limit and bulkhead failures still throw exceptions.
 * 
 * <p>Side effects: Internal HTTP calls, metrics recording.
 * 
 * <p>Authentication: Requires INTERNAL_API_TOKEN header (configured in RestTemplate).
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class QueryServiceClient {
	
	@Value("${services.query-service.url:http://query-service:8082}")
	private String queryServiceUrl;

    private final RestTemplate queryServiceRestTemplate;
	
	private static final String SUMMARY_ENDPOINT = "/internal/summary";
	
	/**
	 * Gets article summary from query-service with resilience patterns.
	 * 
	 * @param articleId article UUID
	 * @param article article data (sent in request body)
	 * @return summary DTO (may be degraded response on fallback)
	 * 
	 * @throws ExternalApiException if rate limit exceeded, bulkhead full, or null/empty response
	 *         (circuit breaker failures return degraded response, not exception)
	 */
	@CircuitBreaker(name = "queryService", fallbackMethod = "getArticleSummaryFallback")
	@Retry(name = "queryService")
	@Bulkhead(name = "queryService", type = Bulkhead.Type.THREADPOOL, fallbackMethod = "getArticleSummaryFallback")
	public SummaryDTO getArticleSummary(UUID articleId, ArticleDTO article) {
		String url = String.format("%s%s/%s", queryServiceUrl, SUMMARY_ENDPOINT, articleId);
		
		HttpEntity<ArticleDTO> request = new HttpEntity<>(article);
		ResponseEntity<SummaryDTO> response = queryServiceRestTemplate.exchange(
			url,
			HttpMethod.POST,
			request,
			SummaryDTO.class
		);
		
		if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
			return response.getBody();
		}
		
		if (response.getBody() == null) {
			log.warn("Query-service returned null response body with status: {}", response.getStatusCode());
			throw new ExternalApiException("Query-service returned empty response");
		}
		
		log.warn("Unexpected response from query-service: {}", response.getStatusCode());
		throw new ExternalApiException(String.format("Failed to get summary from query-service: status %s", response.getStatusCode()));
	}
	
	/**
	 * Fallback method invoked by Resilience4j when circuit breaker is open,
	 * bulkhead is full, or all retries are exhausted.
	 * 
	 * <p>Behavior:
	 * <ul>
	 *   <li>Rate limit exceeded → throws ExternalApiException</li>
	 *   <li>Bulkhead full → throws ExternalApiException</li>
	 *   <li>Circuit breaker open / connection errors → returns degraded response (stub summary)</li>
	 * </ul>
	 * 
	 * <p>This method is called by Resilience4j framework, not directly.
	 * 
	 * @param articleId article UUID
	 * @param article article data (unused in fallback)
	 * @param e exception that triggered fallback
	 * @return degraded summary DTO (for circuit breaker) or throws (for rate limit/bulkhead)
	 * @throws ExternalApiException for rate limit or bulkhead failures
	 */
	@SuppressWarnings("unused")
	private SummaryDTO getArticleSummaryFallback(UUID articleId, ArticleDTO article, Exception e) {
		if (e instanceof RequestNotPermitted) {
			log.warn("Rate limit exceeded for query-service. Article id: {}", articleId);
			throw new ExternalApiException("Rate limit exceeded. Please try again later.", e);
		}
		
		String exceptionType = e != null ? e.getClass().getSimpleName() : "Unknown";
		if (exceptionType.contains("Bulkhead")) {
			log.warn("Query-service bulkhead is full (max concurrent calls reached). Article id: {}", articleId);
			throw new ExternalApiException("Service is currently overloaded. Please try again later.", e);
		}
		
		// For circuit breaker or connection errors, return a degraded response
		log.error("Query-service circuit breaker is open or service unavailable. Article id: {}. Error: {}", 
			articleId, e != null ? e.getMessage() : "Unknown error", e);
		
		// Return a stub summary instead of throwing exception for better user experience
		return SummaryDTO.builder()
			.articleId(articleId)
			.summary("Summary generation is temporarily unavailable. Please try again later.")
			.cached(false)
			.build();
	}
}

