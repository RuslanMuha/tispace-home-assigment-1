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
	void testGet_DeserializationException_ThrowsException() throws Exception {
		String key = "test:key";
		String jsonValue = "invalid json";
		
		when(valueOperations.get(key)).thenReturn(jsonValue);
		when(objectMapper.readValue(jsonValue, TestObject.class))
			.thenThrow(new com.fasterxml.jackson.core.JsonProcessingException("Parse error") {});
		
		assertThrows(com.fasterxml.jackson.core.JsonProcessingException.class, 
			() -> cacheService.get(key, TestObject.class));
		
		verify(valueOperations, times(1)).get(key);
		verify(objectMapper, times(1)).readValue(jsonValue, TestObject.class);
	}
	
	@Test
	void testPut_SerializationException_ThrowsException() throws Exception {
		String key = "test:key";
		TestObject value = new TestObject(1L, "test");
		
		when(objectMapper.writeValueAsString(value))
			.thenThrow(new com.fasterxml.jackson.core.JsonProcessingException("Serialize error") {});
		
		assertThrows(com.fasterxml.jackson.core.JsonProcessingException.class, 
			() -> cacheService.put(key, value, 3600));
		
		verify(objectMapper, times(1)).writeValueAsString(value);
		verify(valueOperations, never()).set(anyString(), anyString(), anyLong(), any(TimeUnit.class));
	}
	
	@Test
	void testGet_RedisThrowsException_PropagatesException() {
		String key = "test:key";
		
		when(valueOperations.get(key)).thenThrow(new RuntimeException("Redis connection error"));
		
		assertThrows(RuntimeException.class, () -> cacheService.get(key, TestObject.class));
		
		verify(valueOperations, times(1)).get(key);
	}
	
	@Test
	void testPut_RedisThrowsException_PropagatesException() throws Exception {
		String key = "test:key";
		TestObject value = new TestObject(1L, "test");
		String jsonValue = "{\"id\":1,\"name\":\"test\"}";
		
		when(objectMapper.writeValueAsString(value)).thenReturn(jsonValue);
		doThrow(new RuntimeException("Redis connection error"))
			.when(valueOperations).set(eq(key), eq(jsonValue), eq(3600L), eq(TimeUnit.SECONDS));
		
		assertThrows(RuntimeException.class, () -> cacheService.put(key, value, 3600));
		
		verify(objectMapper, times(1)).writeValueAsString(value);
		verify(valueOperations, times(1)).set(eq(key), eq(jsonValue), eq(3600L), eq(TimeUnit.SECONDS));
	}
	
	@Test
	void testDelete_RedisThrowsException_PropagatesException() {
		String key = "test:key";
		
		doThrow(new RuntimeException("Redis connection error")).when(redisTemplate).delete(key);
		
		assertThrows(RuntimeException.class, () -> cacheService.delete(key));
		
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
	void testPut_ZeroTtl_StillCaches() throws Exception {
		String key = "test:key";
		TestObject value = new TestObject(1L, "test");
		String jsonValue = "{\"id\":1,\"name\":\"test\"}";
		
		when(objectMapper.writeValueAsString(value)).thenReturn(jsonValue);
		
		cacheService.put(key, value, 0);
		
		verify(objectMapper, times(1)).writeValueAsString(value);
		verify(valueOperations, times(1)).set(eq(key), eq(jsonValue), eq(0L), eq(TimeUnit.SECONDS));
	}
	
	@Test
	void testPut_NegativeTtl_StillCaches() throws Exception {
		String key = "test:key";
		TestObject value = new TestObject(1L, "test");
		String jsonValue = "{\"id\":1,\"name\":\"test\"}";
		
		when(objectMapper.writeValueAsString(value)).thenReturn(jsonValue);
		
		cacheService.put(key, value, -1);
		
		verify(objectMapper, times(1)).writeValueAsString(value);
		verify(valueOperations, times(1)).set(eq(key), eq(jsonValue), eq(-1L), eq(TimeUnit.SECONDS));
	}
	
	@Test
	void testGet_NullKey_ThrowsException() {
		assertThrows(Exception.class, () -> cacheService.get(null, TestObject.class));
	}
	
	@Test
	void testPut_NullKey_ThrowsException() {
		TestObject value = new TestObject(1L, "test");
		assertThrows(Exception.class, () -> cacheService.put(null, value, 3600));
	}
	
	@Test
	void testPut_NullValue_HandlesGracefully() throws Exception {
		String key = "test:key";
		String jsonValue = "null";
		
		when(objectMapper.writeValueAsString(null)).thenReturn(jsonValue);
		
		cacheService.put(key, null, 3600);
		
		verify(objectMapper, times(1)).writeValueAsString(null);
		verify(valueOperations, times(1)).set(eq(key), eq(jsonValue), eq(3600L), eq(TimeUnit.SECONDS));
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

