package com.tispace.queryservice.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.resilience4j.bulkhead.annotation.Bulkhead;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Random;
import java.util.concurrent.TimeUnit;

@Service
@Slf4j
public class CacheService {
	
	private static final double TTL_JITTER_PERCENT = 0.1; // ±10%
	
	private final RedisTemplate<String, String> redisTemplate;
	private final ObjectMapper objectMapper;
	@SuppressWarnings("unused")
	private final MeterRegistry meterRegistry;
	private final Random random = new Random();
	
	// Metrics
	private final Counter cacheHits;
	private final Counter cacheMisses;
	private final Counter cacheErrors;
	private final Timer cacheGetTimer;
	private final Timer cachePutTimer;
	
	public CacheService(RedisTemplate<String, String> redisTemplate, 
	                    ObjectMapper objectMapper, 
	                    MeterRegistry meterRegistry) {
		this.redisTemplate = redisTemplate;
		this.objectMapper = objectMapper;
		this.meterRegistry = meterRegistry;
		
		// Initialize metrics
		this.cacheHits = Counter.builder("cache.hits")
			.description("Number of cache hits")
			.tag("cache", "summary")
			.register(meterRegistry);
		
		this.cacheMisses = Counter.builder("cache.misses")
			.description("Number of cache misses")
			.tag("cache", "summary")
			.register(meterRegistry);
		
		this.cacheErrors = Counter.builder("cache.errors")
			.description("Number of cache errors")
			.tag("cache", "summary")
			.register(meterRegistry);
		
		this.cacheGetTimer = Timer.builder("cache.get.duration")
			.description("Cache get operation duration")
			.tag("cache", "summary")
			.register(meterRegistry);
		
		this.cachePutTimer = Timer.builder("cache.put.duration")
			.description("Cache put operation duration")
			.tag("cache", "summary")
			.register(meterRegistry);
	}
	
	@CircuitBreaker(name = "redis", fallbackMethod = "getFallback")
	@Bulkhead(name = "redis", fallbackMethod = "getFallback")
	public <T> T get(String key, Class<T> type) {
		if (key == null || key.isEmpty()) {
			log.warn("Attempted to get cache with null or empty key");
			return null;
		}
		
		try {
			return cacheGetTimer.recordCallable(() -> {
				try {
					String value = redisTemplate.opsForValue().get(key);
					if (value == null) {
						cacheMisses.increment();
						return null;
					}
					
					T result = objectMapper.readValue(value, type);
					cacheHits.increment();
					return result;
				} catch (JsonProcessingException e) {
					cacheErrors.increment();
					log.warn("Error deserializing cached value for key: {}. Returning null (cache miss)", key);
					return null;
				} catch (Exception e) {
					cacheErrors.increment();
					log.warn("Unexpected error getting cached value for key: {}. Returning null (cache miss)", key, e);
					return null;
				}
			});
		} catch (Exception e) {
			cacheErrors.increment();
			log.warn("Error recording cache get metric for key: {}. Returning null (cache miss)", key, e);
			return null;
		}
	}
	
	/**
	 * Fallback for get - returns null (cache miss).
	 * Called when circuit breaker is open or bulkhead is full.
	 */
	@SuppressWarnings("unused")
	public <T> T getFallback(String key, Class<T> type, Exception e) {
		cacheErrors.increment();
		String exceptionType = e != null ? e.getClass().getSimpleName() : "Unknown";
		if (exceptionType.contains("Bulkhead")) {
			log.warn("Redis bulkhead is full for key: {}. Returning null (cache miss)", key);
		} else {
			log.warn("Redis circuit breaker open for key: {}. Returning null (cache miss)", key);
		}
		return null;
	}
	
	@CircuitBreaker(name = "redis", fallbackMethod = "putFallback")
	@Bulkhead(name = "redis", fallbackMethod = "putFallback")
	public void put(String key, Object value, long ttlSeconds) {
		if (key == null || key.isEmpty()) {
			log.warn("Attempted to put cache with null or empty key");
			return;
		}
		
		if (value == null) {
			log.warn("Attempted to cache null value for key: {}", key);
			return;
		}
		
		if (ttlSeconds <= 0) {
			log.warn("Invalid TTL {} seconds for key: {}. Skipping cache put.", ttlSeconds, key);
			return;
		}
		
		cachePutTimer.record(() -> {
			try {
				String jsonValue = objectMapper.writeValueAsString(value);
				// Add jitter to TTL (±10%) to prevent stampede
				long jitteredTtl = addJitter(ttlSeconds);
				redisTemplate.opsForValue().set(key, jsonValue, jitteredTtl, TimeUnit.SECONDS);
				log.debug("Cached value for key: {} with TTL: {} seconds (original: {})", key, jitteredTtl, ttlSeconds);
			} catch (JsonProcessingException e) {
				cacheErrors.increment();
				log.warn("Error serializing value for cache key: {}. Cache operation failed silently.", key);
			} catch (Exception e) {
				cacheErrors.increment();
				log.warn("Unexpected error putting cached value for key: {}. Cache operation failed silently.", key, e);
			}
		});
	}
	
	/**
	 * Fallback for put - fails silently.
	 * Called when circuit breaker is open or bulkhead is full.
	 */
	@SuppressWarnings("unused")
	public void putFallback(String key, Object value, long ttlSeconds, Exception e) {
		cacheErrors.increment();
		String exceptionType = e != null ? e.getClass().getSimpleName() : "Unknown";
		if (exceptionType.contains("Bulkhead")) {
			log.warn("Redis bulkhead is full for key: {}. Cache operation failed silently.", key);
		} else {
			log.warn("Redis circuit breaker open for key: {}. Cache operation failed silently.", key);
		}
		// Fail silently - cache is optional
	}
	
	@CircuitBreaker(name = "redis", fallbackMethod = "deleteFallback")
	public void delete(String key) {
		if (key == null || key.isEmpty()) {
			log.warn("Attempted to delete cache with null or empty key");
			return;
		}
		
		try {
			redisTemplate.delete(key);
		} catch (Exception e) {
			cacheErrors.increment();
			log.warn("Unexpected error deleting cached value for key: {}. Operation failed silently.", key, e);
		}
	}
	
	/**
	 * Fallback for delete - fails silently.
	 */
	@SuppressWarnings("unused")
	public void deleteFallback(String key, Exception e) {
		cacheErrors.increment();
		log.warn("Redis circuit breaker open for key: {}. Delete operation failed silently.", key);
	}
	
	/**
	 * Adds random jitter to TTL to prevent stampede.
	 * Jitter: ±10% of the original value.
	 */
	private long addJitter(long ttlSeconds) {
		double jitter = (random.nextDouble() * 2 - 1) * TTL_JITTER_PERCENT; // -0.1 to +0.1
		long jittered = (long) (ttlSeconds * (1 + jitter));
		return Math.max(1, jittered); // Minimum 1 second
	}
}
