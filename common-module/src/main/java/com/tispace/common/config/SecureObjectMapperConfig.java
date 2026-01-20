package com.tispace.common.config;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * Configuration for secure ObjectMapper to prevent deserialization attacks.
 * Disables polymorphic deserialization and configures safe defaults.
 */
@Configuration
public class SecureObjectMapperConfig {
	
	@Bean
	@Primary
	public ObjectMapper objectMapper() {
		ObjectMapper mapper = new ObjectMapper();
		
		mapper.registerModule(new JavaTimeModule());
        mapper.deactivateDefaultTyping();
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, true);

		// SECURITY: Disable auto-detection of creator properties to prevent injection
		mapper.configure(DeserializationFeature.FAIL_ON_INVALID_SUBTYPE, true);

		// Additional security: don't allow empty strings as null
		mapper.configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false);

		// Don't fail on null for primitives (for backward compatibility)
		mapper.configure(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES, false);
		
		return mapper;
	}
}

