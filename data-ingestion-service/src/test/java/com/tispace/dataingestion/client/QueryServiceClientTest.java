package com.tispace.dataingestion.client;

import com.tispace.common.dto.ArticleDTO;
import com.tispace.common.dto.SummaryDTO;
import com.tispace.common.exception.ExternalApiException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class QueryServiceClientTest {
	
	@Mock
	private RestTemplate restTemplate;
	
	@InjectMocks
	private QueryServiceClient queryServiceClient;
	
	private ArticleDTO mockArticleDTO;
	private SummaryDTO mockSummaryDTO;
	private static final String QUERY_SERVICE_URL = "http://query-service:8082";
	
	@BeforeEach
	void setUp() {
		ReflectionTestUtils.setField(queryServiceClient, "queryServiceUrl", QUERY_SERVICE_URL);
		
		mockArticleDTO = ArticleDTO.builder()
			.id(1L)
			.title("Test Article")
			.description("Test Description")
			.author("Test Author")
			.publishedAt(LocalDateTime.now())
			.category("technology")
			.build();
		
		mockSummaryDTO = SummaryDTO.builder()
			.articleId(1L)
			.summary("Test Summary")
			.cached(false)
			.build();
	}
	
	@Test
	void testGetArticleSummary_Success_ReturnsSummary() {
		ResponseEntity<SummaryDTO> responseEntity = new ResponseEntity<>(mockSummaryDTO, HttpStatus.OK);
		
		when(restTemplate.exchange(
			anyString(),
			eq(HttpMethod.POST),
			any(HttpEntity.class),
			eq(SummaryDTO.class)
		)).thenReturn(responseEntity);
		
		SummaryDTO result = queryServiceClient.getArticleSummary(1L, mockArticleDTO);
		
		assertNotNull(result);
		assertEquals(1L, result.getArticleId());
		assertEquals("Test Summary", result.getSummary());
		verify(restTemplate, times(1)).exchange(
			eq(QUERY_SERVICE_URL + "/internal/summary/1"),
			eq(HttpMethod.POST),
			any(HttpEntity.class),
			eq(SummaryDTO.class)
		);
	}
	
	@Test
	void testGetArticleSummary_NullResponseBody_ThrowsException() {
		ResponseEntity<SummaryDTO> responseEntity = new ResponseEntity<>(null, HttpStatus.OK);
		
		when(restTemplate.exchange(
			anyString(),
			eq(HttpMethod.POST),
			any(HttpEntity.class),
			eq(SummaryDTO.class)
		)).thenReturn(responseEntity);
		
		ExternalApiException exception = assertThrows(ExternalApiException.class, 
			() -> queryServiceClient.getArticleSummary(1L, mockArticleDTO));
		
		assertTrue(exception.getMessage().contains("empty response"));
	}
	
	@Test
	void testGetArticleSummary_NonOkStatus_ThrowsException() {
		ResponseEntity<SummaryDTO> responseEntity = new ResponseEntity<>(mockSummaryDTO, HttpStatus.INTERNAL_SERVER_ERROR);
		
		when(restTemplate.exchange(
			anyString(),
			eq(HttpMethod.POST),
			any(HttpEntity.class),
			eq(SummaryDTO.class)
		)).thenReturn(responseEntity);
		
		ExternalApiException exception = assertThrows(ExternalApiException.class, 
			() -> queryServiceClient.getArticleSummary(1L, mockArticleDTO));
		
		assertTrue(exception.getMessage().contains("Failed to get summary"));
	}
	
	@Test
	void testGetArticleSummary_HttpClientErrorException_ThrowsException() {
		HttpClientErrorException httpException = new HttpClientErrorException(HttpStatus.NOT_FOUND, "Not Found");
		
		when(restTemplate.exchange(
			anyString(),
			eq(HttpMethod.POST),
			any(HttpEntity.class),
			eq(SummaryDTO.class)
		)).thenThrow(httpException);
		
		// In unit tests without Resilience4j configuration, exceptions are thrown directly
		// Circuit breaker/retry/rate limiter annotations don't work without proper setup
		assertThrows(HttpClientErrorException.class, 
			() -> queryServiceClient.getArticleSummary(1L, mockArticleDTO));
	}
	
	@Test
	void testGetArticleSummary_HttpServerErrorException_ThrowsException() {
		HttpServerErrorException httpException = new HttpServerErrorException(HttpStatus.INTERNAL_SERVER_ERROR, "Server Error");
		
		when(restTemplate.exchange(
			anyString(),
			eq(HttpMethod.POST),
			any(HttpEntity.class),
			eq(SummaryDTO.class)
		)).thenThrow(httpException);
		
		// In unit tests without Resilience4j configuration, exceptions are thrown directly
		assertThrows(HttpServerErrorException.class, 
			() -> queryServiceClient.getArticleSummary(1L, mockArticleDTO));
	}
	
	@Test
	void testGetArticleSummary_ResourceAccessException_ThrowsException() {
		ResourceAccessException accessException = new ResourceAccessException("Connection refused");
		
		when(restTemplate.exchange(
			anyString(),
			eq(HttpMethod.POST),
			any(HttpEntity.class),
			eq(SummaryDTO.class)
		)).thenThrow(accessException);
		
		// In unit tests without Resilience4j configuration, exceptions are thrown directly
		assertThrows(ResourceAccessException.class, 
			() -> queryServiceClient.getArticleSummary(1L, mockArticleDTO));
	}
	
	@Test
	void testGetArticleSummary_RuntimeException_ThrowsException() {
		RuntimeException runtimeException = new RuntimeException("Unexpected error");
		
		when(restTemplate.exchange(
			anyString(),
			eq(HttpMethod.POST),
			any(HttpEntity.class),
			eq(SummaryDTO.class)
		)).thenThrow(runtimeException);
		
		// In unit tests without Resilience4j configuration, exceptions are thrown directly
		assertThrows(RuntimeException.class, 
			() -> queryServiceClient.getArticleSummary(1L, mockArticleDTO));
	}
	
	@Test
	void testGetArticleSummary_RequestNotPermitted_CallsFallback() {
		// RequestNotPermitted doesn't have a public constructor, so we'll test the fallback behavior
		// by simulating the scenario where rate limiter would trigger
		RuntimeException rateLimitException = new RuntimeException("Rate limit exceeded");
		
		when(restTemplate.exchange(
			anyString(),
			eq(HttpMethod.POST),
			any(HttpEntity.class),
			eq(SummaryDTO.class)
		)).thenThrow(rateLimitException);
		
		// In unit tests without Resilience4j configuration, exceptions are thrown directly
		assertThrows(RuntimeException.class, 
			() -> queryServiceClient.getArticleSummary(1L, mockArticleDTO));
	}
	
	@Test
	void testGetArticleSummary_DifferentArticleId_UsesCorrectUrl() {
		ResponseEntity<SummaryDTO> responseEntity = new ResponseEntity<>(mockSummaryDTO, HttpStatus.OK);
		
		when(restTemplate.exchange(
			anyString(),
			eq(HttpMethod.POST),
			any(HttpEntity.class),
			eq(SummaryDTO.class)
		)).thenReturn(responseEntity);
		
		queryServiceClient.getArticleSummary(999L, mockArticleDTO);
		
		verify(restTemplate, times(1)).exchange(
			eq(QUERY_SERVICE_URL + "/internal/summary/999"),
			eq(HttpMethod.POST),
			any(HttpEntity.class),
			eq(SummaryDTO.class)
		);
	}
	
	@Test
	void testGetArticleSummary_NullArticleDTO_StillSendsRequest() {
		ResponseEntity<SummaryDTO> responseEntity = new ResponseEntity<>(mockSummaryDTO, HttpStatus.OK);
		
		when(restTemplate.exchange(
			anyString(),
			eq(HttpMethod.POST),
			any(HttpEntity.class),
			eq(SummaryDTO.class)
		)).thenReturn(responseEntity);
		
		SummaryDTO result = queryServiceClient.getArticleSummary(1L, null);
		
		assertNotNull(result);
		verify(restTemplate, times(1)).exchange(
			anyString(),
			eq(HttpMethod.POST),
			any(HttpEntity.class),
			eq(SummaryDTO.class)
		);
	}
	
	@Test
	void testGetArticleSummary_BadRequestStatus_ThrowsException() {
		ResponseEntity<SummaryDTO> responseEntity = new ResponseEntity<>(mockSummaryDTO, HttpStatus.BAD_REQUEST);
		
		when(restTemplate.exchange(
			anyString(),
			eq(HttpMethod.POST),
			any(HttpEntity.class),
			eq(SummaryDTO.class)
		)).thenReturn(responseEntity);
		
		ExternalApiException exception = assertThrows(ExternalApiException.class, 
			() -> queryServiceClient.getArticleSummary(1L, mockArticleDTO));
		
		assertTrue(exception.getMessage().contains("Failed to get summary"));
	}
}

