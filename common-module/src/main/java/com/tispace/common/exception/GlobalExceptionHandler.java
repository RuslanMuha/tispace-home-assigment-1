package com.tispace.common.exception;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.tispace.common.dto.ErrorResponseDTO;
import com.tispace.common.util.SensitiveDataFilter;
import org.apache.commons.lang3.StringUtils;
import io.swagger.v3.oas.annotations.Hidden;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.time.LocalDateTime;

@RestControllerAdvice
@Order(1) // Lower priority to let SpringDoc handle its own exceptions first
@Hidden // Exclude from SpringDoc/OpenAPI scanning to avoid compatibility issues
@Slf4j
public class GlobalExceptionHandler {
	
	// URI and path constants
	private static final String URI_PREFIX = "uri=";
	private static final String UNKNOWN_PATH = "unknown";
	private static final String FAVICON_PATH = "favicon.ico";
	private static final String SPRINGDOC_API_DOCS_PATH = "/v3/api-docs";
	private static final String SPRINGDOC_SWAGGER_UI_PATH = "/swagger-ui";
	private static final String SPRINGDOC_SWAGGER_UI_HTML_PATH = "/swagger-ui.html";
	
	// Error messages
	private static final String VALIDATION_FAILED_MESSAGE = "Validation failed";
	private static final String UNEXPECTED_ERROR_MESSAGE = "An unexpected error occurred";
	private static final String INVALID_ARGUMENT_MESSAGE = "Invalid argument provided";
	private static final String HTTP_ERROR_MESSAGE = "HTTP error occurred";
	private static final String UNKNOWN_ERROR_MESSAGE = "Unknown error";
	
	// Error codes
	private static final String ERROR_CODE_NOT_FOUND = "NOT_FOUND";
	private static final String ERROR_CODE_EXTERNAL_API_ERROR = "EXTERNAL_API_ERROR";
	private static final String ERROR_CODE_CONNECTION_ERROR = "CONNECTION_ERROR";
	private static final String ERROR_CODE_DATA_INTEGRITY_ERROR = "DATA_INTEGRITY_ERROR";
	private static final String ERROR_CODE_DUPLICATE_ENTRY = "DUPLICATE_ENTRY";
	private static final String ERROR_CODE_REFERENTIAL_INTEGRITY_ERROR = "REFERENTIAL_INTEGRITY_ERROR";
	private static final String ERROR_CODE_SERIALIZATION_ERROR = "SERIALIZATION_ERROR";
	private static final String ERROR_CODE_CACHE_UNAVAILABLE = "CACHE_UNAVAILABLE";
	private static final String ERROR_CODE_RATE_LIMIT_EXCEEDED = "RATE_LIMIT_EXCEEDED";
	private static final String ERROR_CODE_BUSINESS_ERROR = "BUSINESS_ERROR";
	private static final String ERROR_CODE_VALIDATION_ERROR = "VALIDATION_ERROR";
	private static final String ERROR_CODE_INVALID_ARGUMENT = "INVALID_ARGUMENT";
	private static final String ERROR_CODE_INTERNAL_ERROR = "INTERNAL_ERROR";
	
	// Message patterns for data integrity violations
	private static final String PATTERN_DUPLICATE = "duplicate";
	private static final String PATTERN_UNIQUE_CONSTRAINT = "unique constraint";
	private static final String PATTERN_FOREIGN_KEY = "foreign key";
	private static final String PATTERN_REFERENTIAL_INTEGRITY = "referential integrity";
	
	// Connection error patterns
	private static final String PATTERN_TIMEOUT = "timeout";
	private static final String PATTERN_CONNECTION_REFUSED = "Connection refused";
	
	// Message limits
	private static final int MAX_MESSAGE_LENGTH = 200;
	private static final String MESSAGE_TRUNCATION_SUFFIX = "...";
	
	@ExceptionHandler(NotFoundException.class)
	public ResponseEntity<ErrorResponseDTO> handleNotFoundException(NotFoundException ex, WebRequest request) {
		log.error("Resource not found: {}", ex.getMessage());
		return buildErrorResponse(ERROR_CODE_NOT_FOUND, ex.getMessage(), HttpStatus.NOT_FOUND, request);
	}
	
	@ExceptionHandler(ExternalApiException.class)
	public ResponseEntity<ErrorResponseDTO> handleExternalApiException(ExternalApiException ex, WebRequest request) {
		String sanitizedMessage = sanitizeMessage(ex.getMessage());
		log.error("External API error: {}", sanitizedMessage);
		return buildErrorResponse(ERROR_CODE_EXTERNAL_API_ERROR, ex.getMessage(), HttpStatus.SERVICE_UNAVAILABLE, request);
	}
	
	@ExceptionHandler(HttpClientErrorException.class)
	public ResponseEntity<ErrorResponseDTO> handleHttpClientErrorException(HttpClientErrorException ex, WebRequest request) {
		HttpStatusCode statusCode = ex.getStatusCode();
		HttpStatus status = HttpStatus.resolve(statusCode.value());
		if (status == null) {
			status = HttpStatus.INTERNAL_SERVER_ERROR;
		}
		String errorCode = determineErrorCode(status);
		String message = determineHttpErrorMessage(ex, status);
		
		String sanitizedMessage = sanitizeMessage(message);
		log.error("HTTP client error [{}]: {}", status.value(), sanitizedMessage);
		return buildErrorResponse(errorCode, message, status, request);
	}
	
	@ExceptionHandler(ResourceAccessException.class)
	public ResponseEntity<ErrorResponseDTO> handleResourceAccessException(ResourceAccessException ex, WebRequest request) {
		String exceptionMessage = ex.getMessage();
		String message = "Connection timeout or network error occurred";
		
		if (exceptionMessage != null) {
			String lowerMessage = exceptionMessage.toLowerCase();
			if (lowerMessage.contains(PATTERN_TIMEOUT)) {
				message = "Request timeout. Please try again later.";
			} else if (exceptionMessage.contains(PATTERN_CONNECTION_REFUSED)) {
				message = "Service is currently unavailable. Please try again later.";
			}
		}
		
		String sanitizedMessage = sanitizeMessage(exceptionMessage);
		log.error("Resource access error: {}", sanitizedMessage);
		return buildErrorResponse(ERROR_CODE_CONNECTION_ERROR, message, HttpStatus.SERVICE_UNAVAILABLE, request);
	}
	
	@ExceptionHandler(DataIntegrityViolationException.class)
	public ResponseEntity<ErrorResponseDTO> handleDataIntegrityViolationException(DataIntegrityViolationException ex, WebRequest request) {
		String message = "Data integrity violation occurred";
		String errorCode = ERROR_CODE_DATA_INTEGRITY_ERROR;
		
		// Check if it's a duplicate key violation
		String exceptionMessage = ex.getMessage();
		if (exceptionMessage != null) {
			String lowerMessage = exceptionMessage.toLowerCase();
			if (lowerMessage.contains(PATTERN_DUPLICATE) || lowerMessage.contains(PATTERN_UNIQUE_CONSTRAINT)) {
				message = "Duplicate entry detected. The resource already exists.";
				errorCode = ERROR_CODE_DUPLICATE_ENTRY;
			} else if (lowerMessage.contains(PATTERN_FOREIGN_KEY) || lowerMessage.contains(PATTERN_REFERENTIAL_INTEGRITY)) {
				message = "Referential integrity violation. Related resource does not exist.";
				errorCode = ERROR_CODE_REFERENTIAL_INTEGRITY_ERROR;
			}
		}
		
		log.error("Data integrity violation: {}", exceptionMessage, ex);
		return buildErrorResponse(errorCode, message, HttpStatus.CONFLICT, request);
	}
	
	
	@ExceptionHandler(JsonProcessingException.class)
	public ResponseEntity<ErrorResponseDTO> handleJsonProcessingException(JsonProcessingException ex, WebRequest request) {
		log.error("JSON processing error: {}", ex.getMessage(), ex);
		return buildErrorResponse(ERROR_CODE_SERIALIZATION_ERROR, "Failed to process JSON data", HttpStatus.INTERNAL_SERVER_ERROR, request);
	}
	
	@ExceptionHandler(CacheException.class)
	public ResponseEntity<ErrorResponseDTO> handleCacheException(CacheException ex, WebRequest request) {
		log.warn("Cache error: {}", ex.getMessage());
		// Cache failures should not break the application - return a degraded response
		return buildErrorResponse(ERROR_CODE_CACHE_UNAVAILABLE, "Cache service is temporarily unavailable", HttpStatus.SERVICE_UNAVAILABLE, request);
	}
	
	@ExceptionHandler(RateLimitExceededException.class)
	public ResponseEntity<ErrorResponseDTO> handleRateLimitExceededException(RateLimitExceededException ex, WebRequest request) {
		log.warn("Rate limit exceeded: {}", ex.getMessage());
		return buildErrorResponse(ERROR_CODE_RATE_LIMIT_EXCEEDED, ex.getMessage(), HttpStatus.TOO_MANY_REQUESTS, request);
	}
	
	@ExceptionHandler(SerializationException.class)
	public ResponseEntity<ErrorResponseDTO> handleSerializationException(SerializationException ex, WebRequest request) {
		log.error("Serialization error: {}", ex.getMessage(), ex);
		return buildErrorResponse(ERROR_CODE_SERIALIZATION_ERROR, ex.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR, request);
	}
	
	@ExceptionHandler(BusinessException.class)
	public ResponseEntity<ErrorResponseDTO> handleBusinessException(BusinessException ex, WebRequest request) {
		log.error("Business error: {}", ex.getMessage());
		return buildErrorResponse(ERROR_CODE_BUSINESS_ERROR, ex.getMessage(), HttpStatus.BAD_REQUEST, request);
	}
	
	@ExceptionHandler(MethodArgumentNotValidException.class)
	public ResponseEntity<ErrorResponseDTO> handleValidationException(MethodArgumentNotValidException ex, WebRequest request) {
		log.error("Validation error: {}", ex.getMessage());
		String message = ex.getBindingResult().getFieldErrors().stream()
			.map(error -> String.format("%s: %s", error.getField(), error.getDefaultMessage()))
			.reduce((a, b) -> String.format("%s, %s", a, b))
			.orElse(VALIDATION_FAILED_MESSAGE);
		
		// If no field errors, use the exception message if available
		if (VALIDATION_FAILED_MESSAGE.equals(message)) {
            message = ex.getMessage();
        }
		
		return buildErrorResponse(ERROR_CODE_VALIDATION_ERROR, message, HttpStatus.BAD_REQUEST, request);
	}
	
	@ExceptionHandler(IllegalArgumentException.class)
	public ResponseEntity<ErrorResponseDTO> handleIllegalArgumentException(IllegalArgumentException ex, WebRequest request) {
		log.error("Illegal argument: {}", ex.getMessage());
		String message = ex.getMessage() != null ? ex.getMessage() : INVALID_ARGUMENT_MESSAGE;
		return buildErrorResponse(ERROR_CODE_INVALID_ARGUMENT, message, HttpStatus.BAD_REQUEST, request);
	}
	
	@ExceptionHandler(NoResourceFoundException.class)
	public ResponseEntity<?> handleNoResourceFoundException(NoResourceFoundException ex, WebRequest request) {
		String path = extractPath(request);
		
		// Ignore favicon.ico requests - they are automatically requested by browsers
		if (path.contains(FAVICON_PATH)) {
			log.debug("Favicon not found: {}", path);
			return ResponseEntity.notFound().build();
		}
		
		// Ignore SpringDoc/OpenAPI paths - let SpringDoc handle them
		if (isSpringDocPath(path)) {
			log.debug("SpringDoc path not found: {}", path);
			return ResponseEntity.notFound().build();
		}
		
		log.warn("Resource not found: {}", path);
		return buildErrorResponse(ERROR_CODE_NOT_FOUND, ex.getMessage(), HttpStatus.NOT_FOUND, request);
	}
	
	@ExceptionHandler(Exception.class)
	public ResponseEntity<ErrorResponseDTO> handleGenericException(Exception ex, WebRequest request) {
		String path = extractPath(request);
		
		// Ignore SpringDoc/OpenAPI paths - let SpringDoc handle its own errors
		// With @Hidden annotation, SpringDoc shouldn't scan this handler, but we add this check as a safety measure
		if (isSpringDocPath(path)) {
			log.debug("SpringDoc path detected: {}, not handling exception", path);
			// Return a simple response without logging to avoid interfering with SpringDoc
			return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
				.body(ErrorResponseDTO.builder()
					.errorCode(ERROR_CODE_INTERNAL_ERROR)
					.message(UNEXPECTED_ERROR_MESSAGE)
					.timestamp(LocalDateTime.now())
					.path(path)
					.build());
		}
		
		// Don't expose full exception details to client for security
		String errorMessage = truncateMessage(ex.getMessage());
		
		log.error("Unexpected error: {}", errorMessage != null ? errorMessage : UNKNOWN_ERROR_MESSAGE, ex);
		return buildErrorResponse(ERROR_CODE_INTERNAL_ERROR, UNEXPECTED_ERROR_MESSAGE, HttpStatus.INTERNAL_SERVER_ERROR, request);
	}
	
	private ResponseEntity<ErrorResponseDTO> buildErrorResponse(String errorCode, String message, 
	                                                             HttpStatus status, WebRequest request) {
		ErrorResponseDTO error = ErrorResponseDTO.builder()
			.errorCode(errorCode)
			.message(message)
			.timestamp(LocalDateTime.now())
			.path(extractPath(request))
			.build();
		return new ResponseEntity<>(error, status);
	}
	
	private String extractPath(WebRequest request) {
		try {
			String description = request.getDescription(false);
			if (StringUtils.isEmpty(description)) {
				return UNKNOWN_PATH;
			}
			return description.replace(URI_PREFIX, "");
		} catch (Exception e) {
			try {
				log.warn("Failed to extract path from request", e);
			} catch (Exception logException) {
				// Ignore logging exceptions to ensure we always return UNKNOWN_PATH
			}
			return UNKNOWN_PATH;
		}
	}
	
	/**
	 * Checks if the given path is a SpringDoc/OpenAPI path that should be ignored.
	 */
	private boolean isSpringDocPath(String path) {
		return path.contains(SPRINGDOC_API_DOCS_PATH) 
			|| path.contains(SPRINGDOC_SWAGGER_UI_PATH) 
			|| path.contains(SPRINGDOC_SWAGGER_UI_HTML_PATH);
	}
	
	/**
	 * Truncates a message to the maximum allowed length for security purposes.
	 */
	private String truncateMessage(String message) {
		if (message == null) {
			return null;
		}
		if (message.length() > MAX_MESSAGE_LENGTH) {
			return message.substring(0, MAX_MESSAGE_LENGTH) + MESSAGE_TRUNCATION_SUFFIX;
		}
		return message;
	}
	
	private String determineErrorCode(HttpStatus status) {
		if (status == null) {
			return "HTTP_ERROR";
		}
		return status.name();
	}
	
	private String determineHttpErrorMessage(HttpClientErrorException ex, HttpStatus status) {
		if (status == HttpStatus.UNAUTHORIZED) {
			return "Authentication failed. Please check your credentials.";
		} else if (status == HttpStatus.FORBIDDEN) {
			return "Access forbidden. You don't have permission to access this resource.";
		} else if (status == HttpStatus.NOT_FOUND) {
			return "Resource not found.";
		} else if (status == HttpStatus.TOO_MANY_REQUESTS) {
			return "Rate limit exceeded. Please try again later.";
		} else if (status == HttpStatus.CONFLICT) {
			return "Resource conflict. The resource already exists or is in use.";
		}
		
		String exceptionMessage = ex.getMessage();
		if (exceptionMessage != null && !exceptionMessage.isEmpty()) {
			// Extract only the status code part, not the full response body
			return HTTP_ERROR_MESSAGE;
		}
		
		return HTTP_ERROR_MESSAGE;
	}
	
	/**
	 * Sanitizes a message by masking sensitive data and truncating if necessary.
	 * 
	 * @param message the message to sanitize
	 * @return the sanitized message
	 */
	private String sanitizeMessage(String message) {
		if (message == null) {
			return null;
		}
		
		// First mask sensitive data
		String masked = SensitiveDataFilter.maskSensitiveData(message);
		
		// Then truncate if too long
		return truncateMessage(masked);
	}
}

