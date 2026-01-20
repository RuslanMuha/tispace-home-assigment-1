package com.tispace.common.config;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SecureObjectMapperConfigTest {
	
	private ObjectMapper objectMapper;
	
	@BeforeEach
	void setUp() {
		SecureObjectMapperConfig config = new SecureObjectMapperConfig();
		objectMapper = config.objectMapper();
	}
	
	@Test
	void testObjectMapper_ValidJson_ShouldDeserialize() throws JsonProcessingException {
		String json = "{\"id\":1,\"name\":\"test\"}";
		TestObject result = objectMapper.readValue(json, TestObject.class);
		
		assertNotNull(result);
		assertEquals(1L, result.getId());
		assertEquals("test", result.getName());
	}
	
	@Test
	void testObjectMapper_UnknownProperties_ShouldThrow() {
		String json = "{\"id\":1,\"name\":\"test\",\"unknownField\":\"value\"}";
		
		assertThrows(Exception.class, () -> {
			objectMapper.readValue(json, TestObject.class);
		});
	}
	
	@Test
	void testObjectMapper_ValidNestedJson_ShouldDeserialize() throws JsonProcessingException {
		// Test that valid nested JSON still works
		String json = "{\"nested\":{\"id\":1,\"name\":\"test\"}}";
		assertDoesNotThrow(() -> {
			objectMapper.readTree(json);
		});
	}
	
	@Test
	void testObjectMapper_PolymorphicDeserialization_ShouldBeDisabled() throws JsonProcessingException {
		// Attempt to use polymorphic type information (should be disabled)
		String json = "{\"@class\":\"java.util.HashMap\",\"key\":\"value\"}";
		
		// When polymorphic deserialization is disabled, @class is ignored
		// The JSON should deserialize as a Map, not as the specified class
		Object result = objectMapper.readValue(json, Object.class);
		
		// Verify that it's deserialized as a Map (not as HashMap with @class)
		assertNotNull(result);
		// The @class field should be ignored, so the result should be a Map with the actual data
		assertTrue(result instanceof java.util.Map);
		java.util.Map<?, ?> map = (java.util.Map<?, ?>) result;
		// Verify that @class is treated as a regular property, not as type information
		assertTrue(map.containsKey("@class"));
		assertEquals("java.util.HashMap", map.get("@class"));
	}
	
	// Helper class for testing
	private static class TestObject {
		private Long id;
		private String name;
		
		public TestObject() {
		}
		
		public TestObject(Long id, String name) {
			this.id = id;
			this.name = name;
		}
		
		public Long getId() {
			return id;
		}
		
		public void setId(Long id) {
			this.id = id;
		}
		
		public String getName() {
			return name;
		}
		
		public void setName(String name) {
			this.name = name;
		}
	}
}

