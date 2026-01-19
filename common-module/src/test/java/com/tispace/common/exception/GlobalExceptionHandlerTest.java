package com.tispace.common.exception;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.tispace.common.dto.ErrorResponseDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class GlobalExceptionHandlerTest {
	
	private GlobalExceptionHandler exceptionHandler;
	private WebRequest webRequest;
	
	@BeforeEach
	void setUp() {
		exceptionHandler = new GlobalExceptionHandler();
		webRequest = mock(WebRequest.class);
		when(webRequest.getDescription(false)).thenReturn("uri=/api/test");
	}
	
	@Test
	void testHandleNotFoundException_ReturnsNotFound() {
		NotFoundException ex = new NotFoundException("Article", 1L);
		
		ResponseEntity<ErrorResponseDTO> response = exceptionHandler.handleNotFoundException(ex, webRequest);
		
		assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
		assertNotNull(response.getBody());
		assertEquals("NOT_FOUND", response.getBody().getErrorCode());
		assertTrue(response.getBody().getMessage().contains("Article"));
		assertTrue(response.getBody().getMessage().contains("1"));
	}
	
	@Test
	void testHandleExternalApiException_ReturnsServiceUnavailable() {
		ExternalApiException ex = new ExternalApiException("API error");
		
		ResponseEntity<ErrorResponseDTO> response = exceptionHandler.handleExternalApiException(ex, webRequest);
		
		assertEquals(HttpStatus.SERVICE_UNAVAILABLE, response.getStatusCode());
		assertNotNull(response.getBody());
		assertEquals("EXTERNAL_API_ERROR", response.getBody().getErrorCode());
		assertEquals("API error", response.getBody().getMessage());
	}
	
	@Test
	void testHandleHttpClientErrorException_Unauthorized_ReturnsUnauthorized() {
		HttpClientErrorException ex = new HttpClientErrorException(HttpStatus.UNAUTHORIZED);
		
		ResponseEntity<ErrorResponseDTO> response = exceptionHandler.handleHttpClientErrorException(ex, webRequest);
		
		assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
		assertNotNull(response.getBody());
		assertTrue(response.getBody().getMessage().contains("Authentication failed"));
	}
	
	@Test
	void testHandleHttpClientErrorException_Forbidden_ReturnsForbidden() {
		HttpClientErrorException ex = new HttpClientErrorException(HttpStatus.FORBIDDEN);
		
		ResponseEntity<ErrorResponseDTO> response = exceptionHandler.handleHttpClientErrorException(ex, webRequest);
		
		assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
		assertNotNull(response.getBody());
		assertTrue(response.getBody().getMessage().contains("Access forbidden"));
	}
	
	@Test
	void testHandleHttpClientErrorException_NotFound_ReturnsNotFound() {
		HttpClientErrorException ex = new HttpClientErrorException(HttpStatus.NOT_FOUND);
		
		ResponseEntity<ErrorResponseDTO> response = exceptionHandler.handleHttpClientErrorException(ex, webRequest);
		
		assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
		assertNotNull(response.getBody());
		assertTrue(response.getBody().getMessage().contains("Resource not found"));
	}
	
	@Test
	void testHandleHttpClientErrorException_TooManyRequests_ReturnsTooManyRequests() {
		HttpClientErrorException ex = new HttpClientErrorException(HttpStatus.TOO_MANY_REQUESTS);
		
		ResponseEntity<ErrorResponseDTO> response = exceptionHandler.handleHttpClientErrorException(ex, webRequest);
		
		assertEquals(HttpStatus.TOO_MANY_REQUESTS, response.getStatusCode());
		assertNotNull(response.getBody());
		assertTrue(response.getBody().getMessage().contains("Rate limit exceeded"));
	}
	
	@Test
	void testHandleHttpClientErrorException_Conflict_ReturnsConflict() {
		HttpClientErrorException ex = new HttpClientErrorException(HttpStatus.CONFLICT);
		
		ResponseEntity<ErrorResponseDTO> response = exceptionHandler.handleHttpClientErrorException(ex, webRequest);
		
		assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
		assertNotNull(response.getBody());
		assertTrue(response.getBody().getMessage().contains("Resource conflict"));
	}
	
	@Test
	void testHandleResourceAccessException_Timeout_ReturnsTimeoutMessage() {
		ResourceAccessException ex = new ResourceAccessException("Connection timeout");
		
		ResponseEntity<ErrorResponseDTO> response = exceptionHandler.handleResourceAccessException(ex, webRequest);
		
		assertEquals(HttpStatus.SERVICE_UNAVAILABLE, response.getStatusCode());
		assertNotNull(response.getBody());
		assertEquals("CONNECTION_ERROR", response.getBody().getErrorCode());
		assertTrue(response.getBody().getMessage().contains("timeout"));
	}
	
	@Test
	void testHandleResourceAccessException_ConnectionRefused_ReturnsConnectionRefusedMessage() {
		ResourceAccessException ex = new ResourceAccessException("Connection refused");
		
		ResponseEntity<ErrorResponseDTO> response = exceptionHandler.handleResourceAccessException(ex, webRequest);
		
		assertEquals(HttpStatus.SERVICE_UNAVAILABLE, response.getStatusCode());
		assertNotNull(response.getBody());
		assertTrue(response.getBody().getMessage().contains("unavailable"));
	}
	
	@Test
	void testHandleDataIntegrityViolationException_Duplicate_ReturnsDuplicateError() {
		DataIntegrityViolationException ex = new DataIntegrityViolationException("duplicate key value");
		
		ResponseEntity<ErrorResponseDTO> response = exceptionHandler.handleDataIntegrityViolationException(ex, webRequest);
		
		assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
		assertNotNull(response.getBody());
		assertEquals("DUPLICATE_ENTRY", response.getBody().getErrorCode());
		assertTrue(response.getBody().getMessage().contains("Duplicate entry"));
	}
	
	@Test
	void testHandleDataIntegrityViolationException_UniqueConstraint_ReturnsDuplicateError() {
		DataIntegrityViolationException ex = new DataIntegrityViolationException("unique constraint violation");
		
		ResponseEntity<ErrorResponseDTO> response = exceptionHandler.handleDataIntegrityViolationException(ex, webRequest);
		
		assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
		assertNotNull(response.getBody());
		assertEquals("DUPLICATE_ENTRY", response.getBody().getErrorCode());
	}
	
	@Test
	void testHandleDataIntegrityViolationException_ForeignKey_ReturnsReferentialIntegrityError() {
		DataIntegrityViolationException ex = new DataIntegrityViolationException("foreign key constraint");
		
		ResponseEntity<ErrorResponseDTO> response = exceptionHandler.handleDataIntegrityViolationException(ex, webRequest);
		
		assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
		assertNotNull(response.getBody());
		assertEquals("REFERENTIAL_INTEGRITY_ERROR", response.getBody().getErrorCode());
		assertTrue(response.getBody().getMessage().contains("Referential integrity"));
	}
	
	@Test
	void testHandleDataIntegrityViolationException_ReferentialIntegrity_ReturnsReferentialIntegrityError() {
		DataIntegrityViolationException ex = new DataIntegrityViolationException("referential integrity violation");
		
		ResponseEntity<ErrorResponseDTO> response = exceptionHandler.handleDataIntegrityViolationException(ex, webRequest);
		
		assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
		assertNotNull(response.getBody());
		assertEquals("REFERENTIAL_INTEGRITY_ERROR", response.getBody().getErrorCode());
	}
	
	@Test
	void testHandleDataIntegrityViolationException_Generic_ReturnsDataIntegrityError() {
		DataIntegrityViolationException ex = new DataIntegrityViolationException("generic integrity violation");
		
		ResponseEntity<ErrorResponseDTO> response = exceptionHandler.handleDataIntegrityViolationException(ex, webRequest);
		
		assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
		assertNotNull(response.getBody());
		assertEquals("DATA_INTEGRITY_ERROR", response.getBody().getErrorCode());
	}
	
	@Test
	void testHandleJsonProcessingException_ReturnsSerializationError() {
		JsonProcessingException ex = mock(JsonProcessingException.class);
		when(ex.getMessage()).thenReturn("JSON parse error");
		
		ResponseEntity<ErrorResponseDTO> response = exceptionHandler.handleJsonProcessingException(ex, webRequest);
		
		assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
		assertNotNull(response.getBody());
		assertEquals("SERIALIZATION_ERROR", response.getBody().getErrorCode());
		assertTrue(response.getBody().getMessage().contains("JSON"));
	}
	
	@Test
	void testHandleCacheException_ReturnsCacheUnavailable() {
		CacheException ex = new CacheException("Cache error");
		
		ResponseEntity<ErrorResponseDTO> response = exceptionHandler.handleCacheException(ex, webRequest);
		
		assertEquals(HttpStatus.SERVICE_UNAVAILABLE, response.getStatusCode());
		assertNotNull(response.getBody());
		assertEquals("CACHE_UNAVAILABLE", response.getBody().getErrorCode());
		assertTrue(response.getBody().getMessage().contains("Cache service"));
	}
	
	@Test
	void testHandleRateLimitExceededException_ReturnsTooManyRequests() {
		RateLimitExceededException ex = new RateLimitExceededException("Rate limit exceeded");
		
		ResponseEntity<ErrorResponseDTO> response = exceptionHandler.handleRateLimitExceededException(ex, webRequest);
		
		assertEquals(HttpStatus.TOO_MANY_REQUESTS, response.getStatusCode());
		assertNotNull(response.getBody());
		assertEquals("RATE_LIMIT_EXCEEDED", response.getBody().getErrorCode());
		assertEquals("Rate limit exceeded", response.getBody().getMessage());
	}
	
	@Test
	void testHandleSerializationException_ReturnsSerializationError() {
		SerializationException ex = new SerializationException("Serialization error");
		
		ResponseEntity<ErrorResponseDTO> response = exceptionHandler.handleSerializationException(ex, webRequest);
		
		assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
		assertNotNull(response.getBody());
		assertEquals("SERIALIZATION_ERROR", response.getBody().getErrorCode());
		assertEquals("Serialization error", response.getBody().getMessage());
	}
	
	@Test
	void testHandleBusinessException_ReturnsBadRequest() {
		BusinessException ex = new BusinessException("Business error");
		
		ResponseEntity<ErrorResponseDTO> response = exceptionHandler.handleBusinessException(ex, webRequest);
		
		assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
		assertNotNull(response.getBody());
		assertEquals("BUSINESS_ERROR", response.getBody().getErrorCode());
		assertEquals("Business error", response.getBody().getMessage());
	}
	
	@Test
	void testHandleIllegalArgumentException_ReturnsBadRequest() {
		IllegalArgumentException ex = new IllegalArgumentException("Invalid argument");
		
		ResponseEntity<ErrorResponseDTO> response = exceptionHandler.handleIllegalArgumentException(ex, webRequest);
		
		assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
		assertNotNull(response.getBody());
		assertEquals("INVALID_ARGUMENT", response.getBody().getErrorCode());
		assertEquals("Invalid argument", response.getBody().getMessage());
	}
	
	@Test
	void testHandleIllegalArgumentException_NullMessage_ReturnsDefaultMessage() {
		IllegalArgumentException ex = new IllegalArgumentException();
		
		ResponseEntity<ErrorResponseDTO> response = exceptionHandler.handleIllegalArgumentException(ex, webRequest);
		
		assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
		assertNotNull(response.getBody());
		assertEquals("INVALID_ARGUMENT", response.getBody().getErrorCode());
		assertNotNull(response.getBody().getMessage());
	}
	
	@Test
	void testHandleNoResourceFoundException_Favicon_ReturnsNotFoundWithoutBody() {
		when(webRequest.getDescription(false)).thenReturn("uri=/favicon.ico");
		// NoResourceFoundException constructor: HttpMethod, String resourcePath
		NoResourceFoundException ex = new NoResourceFoundException(
			org.springframework.http.HttpMethod.GET, "/favicon.ico");
		
		ResponseEntity<?> response = exceptionHandler.handleNoResourceFoundException(ex, webRequest);
		
		assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
		assertNull(response.getBody());
	}
	
	@Test
	void testHandleNoResourceFoundException_SpringDocPath_ReturnsNotFoundWithoutBody() {
		when(webRequest.getDescription(false)).thenReturn("uri=/v3/api-docs");
		NoResourceFoundException ex = new NoResourceFoundException(
			org.springframework.http.HttpMethod.GET, "/v3/api-docs");
		
		ResponseEntity<?> response = exceptionHandler.handleNoResourceFoundException(ex, webRequest);
		
		assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
		assertNull(response.getBody());
	}
	
	@Test
	void testHandleNoResourceFoundException_RegularPath_ReturnsErrorResponse() {
		NoResourceFoundException ex = new NoResourceFoundException(
			org.springframework.http.HttpMethod.GET, "/api/articles/999");
		
		ResponseEntity<?> response = exceptionHandler.handleNoResourceFoundException(ex, webRequest);
		
		assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
		assertNotNull(response.getBody());
		assertTrue(response.getBody() instanceof ErrorResponseDTO);
	}
	
	@Test
	void testHandleGenericException_SpringDocPath_ReturnsSimpleResponse() {
		when(webRequest.getDescription(false)).thenReturn("uri=/swagger-ui/index.html");
		Exception ex = new Exception("Test error");
		
		ResponseEntity<ErrorResponseDTO> response = exceptionHandler.handleGenericException(ex, webRequest);
		
		assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
		assertNotNull(response.getBody());
		assertEquals("INTERNAL_ERROR", response.getBody().getErrorCode());
	}
	
	@Test
	void testHandleGenericException_RegularPath_ReturnsGenericError() {
		Exception ex = new Exception("Unexpected error");
		
		ResponseEntity<ErrorResponseDTO> response = exceptionHandler.handleGenericException(ex, webRequest);
		
		assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
		assertNotNull(response.getBody());
		assertEquals("INTERNAL_ERROR", response.getBody().getErrorCode());
		assertTrue(response.getBody().getMessage().contains("unexpected error"));
	}
	
	@Test
	void testHandleGenericException_NullMessage_ReturnsDefaultMessage() {
		Exception ex = new Exception();
		
		ResponseEntity<ErrorResponseDTO> response = exceptionHandler.handleGenericException(ex, webRequest);
		
		assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
		assertNotNull(response.getBody());
		assertEquals("INTERNAL_ERROR", response.getBody().getErrorCode());
	}
	
	@Test
	void testHandleGenericException_LongMessage_TruncatesMessage() {
		String longMessage = "A".repeat(300);
		Exception ex = new Exception(longMessage);
		
		ResponseEntity<ErrorResponseDTO> response = exceptionHandler.handleGenericException(ex, webRequest);
		
		assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
		assertNotNull(response.getBody());
		assertTrue(response.getBody().getMessage().length() <= 203); // 200 + "..."
	}
	
	@Test
	void testExtractPath_EmptyDescription_ReturnsUnknown() {
		when(webRequest.getDescription(false)).thenReturn("");
		
		ResponseEntity<ErrorResponseDTO> response = exceptionHandler.handleNotFoundException(
			new NotFoundException("Test"), webRequest);
		
		assertNotNull(response.getBody());
		assertEquals("unknown", response.getBody().getPath());
	}
	
	@Test
	void testExtractPath_NullDescription_ReturnsUnknown() {
		when(webRequest.getDescription(false)).thenReturn(null);
		
		ResponseEntity<ErrorResponseDTO> response = exceptionHandler.handleNotFoundException(
			new NotFoundException("Test"), webRequest);
		
		assertNotNull(response.getBody());
		assertEquals("unknown", response.getBody().getPath());
	}
	
	@Test
	void testExtractPath_Exception_ReturnsUnknown() {
		when(webRequest.getDescription(false)).thenThrow(new RuntimeException("Error"));
		
		ResponseEntity<ErrorResponseDTO> response = exceptionHandler.handleNotFoundException(
			new NotFoundException("Test"), webRequest);
		
		assertNotNull(response.getBody());
		assertEquals("unknown", response.getBody().getPath());
	}
}

