package com.tispace.queryservice.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CacheServiceTest {
	
	@Mock
	private RedisTemplate<String, String> redisTemplate;
	
	@Mock
	private ObjectMapper objectMapper;
	
	@Mock
	private ValueOperations<String, String> valueOperations;
	
	@InjectMocks
	private CacheService cacheService;
	
	@BeforeEach
	void setUp() {
		// Set up mock that's used in most tests, but make it lenient for tests that don't need it
		lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);
	}
	
	@Test
	void testGet_KeyExists_ReturnsValue() throws Exception {
		String key = "test:key";
		String jsonValue = "{\"id\":1,\"name\":\"test\"}";
		TestObject expectedObject = new TestObject(1L, "test");
		
		when(valueOperations.get(key)).thenReturn(jsonValue);
		when(objectMapper.readValue(jsonValue, TestObject.class)).thenReturn(expectedObject);
		
		TestObject result = cacheService.get(key, TestObject.class);
		
		assertNotNull(result);
		assertEquals(1L, result.getId());
		assertEquals("test", result.getName());
		verify(valueOperations, times(1)).get(key);
	}
	
	@Test
	void testGet_KeyNotExists_ReturnsNull() {
		String key = "test:key";
		
		when(valueOperations.get(key)).thenReturn(null);
		
		TestObject result = cacheService.get(key, TestObject.class);
		
		assertNull(result);
		verify(valueOperations, times(1)).get(key);
	}
	
	@Test
	void testPut_Success_CachesValue() throws Exception {
		String key = "test:key";
		TestObject value = new TestObject(1L, "test");
		String jsonValue = "{\"id\":1,\"name\":\"test\"}";
		
		when(objectMapper.writeValueAsString(value)).thenReturn(jsonValue);
		
		cacheService.put(key, value, 3600);
		
		verify(objectMapper, times(1)).writeValueAsString(value);
		verify(valueOperations, times(1)).set(eq(key), eq(jsonValue), eq(3600L), eq(TimeUnit.SECONDS));
	}
	
	@Test
	void testDelete_Success_DeletesKey() {
		String key = "test:key";
		
		cacheService.delete(key);
		
		verify(redisTemplate, times(1)).delete(key);
	}
	
	@Test
	void testGet_DeserializationException_ReturnsNull() throws Exception {
		// CacheService handles exceptions gracefully and returns null (cache miss)
		String key = "test:key";
		String jsonValue = "invalid json";
		
		when(valueOperations.get(key)).thenReturn(jsonValue);
		when(objectMapper.readValue(jsonValue, TestObject.class))
			.thenThrow(new com.fasterxml.jackson.databind.JsonMappingException(null, "Parse error"));
		
		TestObject result = cacheService.get(key, TestObject.class);
		
		assertNull(result); // Exceptions are handled and return null
		verify(valueOperations, times(1)).get(key);
	}
	
	@Test
	void testPut_SerializationException_FailsSilently() throws Exception {
		// CacheService handles exceptions gracefully and fails silently
		String key = "test:key";
		TestObject value = new TestObject(1L, "test");
		
		when(objectMapper.writeValueAsString(value))
			.thenThrow(new com.fasterxml.jackson.databind.JsonMappingException(null, "Serialize error"));
		
		// Should not throw exception - fails silently
		cacheService.put(key, value, 3600);
		
		verify(objectMapper, times(1)).writeValueAsString(value);
		verify(valueOperations, never()).set(anyString(), anyString(), anyLong(), any(TimeUnit.class));
	}
	
	@Test
	void testGet_RedisThrowsException_ReturnsNull() {
		// CacheService handles exceptions gracefully and returns null (cache miss)
		String key = "test:key";
		
		when(valueOperations.get(key)).thenThrow(new RuntimeException("Redis connection error"));
		
		TestObject result = cacheService.get(key, TestObject.class);
		
		assertNull(result); // Exceptions are handled and return null
		verify(valueOperations, times(1)).get(key);
	}
	
	@Test
	void testPut_RedisThrowsException_FailsSilently() throws Exception {
		// CacheService handles exceptions gracefully and fails silently
		String key = "test:key";
		TestObject value = new TestObject(1L, "test");
		String jsonValue = "{\"id\":1,\"name\":\"test\"}";
		
		when(objectMapper.writeValueAsString(value)).thenReturn(jsonValue);
		doThrow(new RuntimeException("Redis connection error"))
			.when(valueOperations).set(eq(key), eq(jsonValue), eq(3600L), eq(TimeUnit.SECONDS));
		
		// Should not throw exception - fails silently
		cacheService.put(key, value, 3600);
		
		verify(objectMapper, times(1)).writeValueAsString(value);
		verify(valueOperations, times(1)).set(eq(key), eq(jsonValue), eq(3600L), eq(TimeUnit.SECONDS));
	}
	
	@Test
	void testDelete_RedisThrowsException_FailsSilently() {
		// CacheService handles exceptions gracefully and fails silently
		String key = "test:key";
		
		doThrow(new RuntimeException("Redis connection error")).when(redisTemplate).delete(key);
		
		// Should not throw exception - fails silently
		cacheService.delete(key);
		
		verify(redisTemplate, times(1)).delete(key);
	}
	
	@Test
	void testGet_EmptyJson_HandlesGracefully() throws Exception {
		String key = "test:key";
		String jsonValue = "{}";
		TestObject expectedObject = new TestObject(null, null);
		
		when(valueOperations.get(key)).thenReturn(jsonValue);
		when(objectMapper.readValue(jsonValue, TestObject.class)).thenReturn(expectedObject);
		
		TestObject result = cacheService.get(key, TestObject.class);
		
		assertNotNull(result);
		verify(valueOperations, times(1)).get(key);
	}
	
	@Test
	void testPut_ZeroTtl_SkipsCaching() throws Exception {
		// CacheService skips caching when TTL <= 0
		String key = "test:key";
		TestObject value = new TestObject(1L, "test");
		
		cacheService.put(key, value, 0);
		
		// Should not call objectMapper or valueOperations when TTL is invalid
		verify(objectMapper, never()).writeValueAsString(any());
		verify(valueOperations, never()).set(anyString(), anyString(), anyLong(), any(TimeUnit.class));
	}
	
	@Test
	void testPut_NegativeTtl_SkipsCaching() throws Exception {
		// CacheService skips caching when TTL <= 0
		String key = "test:key";
		TestObject value = new TestObject(1L, "test");
		
		cacheService.put(key, value, -1);
		
		// Should not call objectMapper or valueOperations when TTL is invalid
		verify(objectMapper, never()).writeValueAsString(any());
		verify(valueOperations, never()).set(anyString(), anyString(), anyLong(), any(TimeUnit.class));
	}
	
	@Test
	void testGet_NullKey_ReturnsNull() throws Exception {
		// CacheService handles null key gracefully and returns null
		TestObject result = cacheService.get(null, TestObject.class);
		
		assertNull(result);
		verify(valueOperations, never()).get(anyString());
	}
	
	@Test
	void testPut_NullKey_FailsSilently() throws Exception {
		// CacheService handles null key gracefully and fails silently
		TestObject value = new TestObject(1L, "test");
		
		cacheService.put(null, value, 3600);
		
		verify(objectMapper, never()).writeValueAsString(any());
		verify(valueOperations, never()).set(anyString(), anyString(), anyLong(), any(TimeUnit.class));
	}
	
	@Test
	void testPut_NullValue_SkipsCaching() throws Exception {
		// CacheService skips caching when value is null
		String key = "test:key";
		
		cacheService.put(key, null, 3600);
		
		// Should not call objectMapper or valueOperations when value is null
		verify(objectMapper, never()).writeValueAsString(any());
		verify(valueOperations, never()).set(anyString(), anyString(), anyLong(), any(TimeUnit.class));
	}
	
	// Helper class for testing
	private static class TestObject {
		private Long id;
		private String name;
		
		public TestObject(Long id, String name) {
			this.id = id;
			this.name = name;
		}
		
		public Long getId() {
			return id;
		}
		
		public String getName() {
			return name;
		}
	}
}

