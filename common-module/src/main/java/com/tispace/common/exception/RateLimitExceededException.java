package com.tispace.common.exception;

public class RateLimitExceededException extends ExternalApiException {
	
	public RateLimitExceededException(String message) {
		super(message);
	}
	
	public RateLimitExceededException(String message, Throwable cause) {
		super(message, cause);
	}
}


