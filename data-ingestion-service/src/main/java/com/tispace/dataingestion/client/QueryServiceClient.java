package com.tispace.dataingestion.client;

import com.tispace.common.dto.ArticleDTO;
import com.tispace.common.dto.SummaryDTO;
import com.tispace.common.exception.ExternalApiException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

@Component
@RequiredArgsConstructor
@Slf4j
public class QueryServiceClient {
	
	@Value("${services.query-service.url:http://query-service:8082}")
	private String queryServiceUrl;
	
	private final RestTemplate restTemplate;
	
	private static final String SUMMARY_ENDPOINT = "/internal/summary";
	
	public SummaryDTO getArticleSummary(Long articleId, ArticleDTO article) {
		try {
			String url = String.format("%s%s/%d", queryServiceUrl, SUMMARY_ENDPOINT, articleId);
			
			HttpEntity<ArticleDTO> request = new HttpEntity<>(article);
			ResponseEntity<SummaryDTO> response = restTemplate.exchange(
				url,
				HttpMethod.POST,
				request,
				SummaryDTO.class
			);
			
			if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
				return response.getBody();
			}
			
			log.warn("Unexpected response from query-service: {}", response.getStatusCode());
			throw new ExternalApiException("Failed to get summary from query-service");
			
		} catch (HttpClientErrorException e) {
			log.error("Error calling query-service for summary: {}", e.getMessage());
			throw new ExternalApiException(String.format("Query service error: %s", e.getMessage()), e);
		} catch (Exception e) {
			log.error("Unexpected error calling query-service", e);
			throw new ExternalApiException(String.format("Failed to get summary: %s", e.getMessage()), e);
		}
	}
}

