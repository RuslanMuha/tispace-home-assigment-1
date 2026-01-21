package com.tispace.queryservice.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tispace.common.exception.ExternalApiException;
import com.tispace.queryservice.config.SingleFlightProperties;
import com.tispace.queryservice.dto.SingleFlightEnvelope;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.util.Collections;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SingleFlightServiceTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    private SingleFlightProperties properties;
    private ObjectMapper objectMapper;

    private SingleFlightService singleFlightService;

    @BeforeEach
    void setUp() {
        properties = new SingleFlightProperties();
        properties.setLockTimeoutSeconds(5);
        properties.setInFlightTimeoutSeconds(2);
        properties.setResultTtlSeconds(5);
        properties.setPollInitialMs(5);
        properties.setPollMaxMs(20);
        properties.setPollMultiplier(2);

        objectMapper = new ObjectMapper();

        singleFlightService = new SingleFlightService(redisTemplate, objectMapper, properties);


    }

    @Test
    void execute_whenLeaderAcquiresLock_thenExecutesOperationAndStoresResult() throws Exception {
        String key = "article:1";
        String lockKey = "lock:singleflight:" + key;
        String resultKey = "result:singleflight:" + key;

        when(valueOperations.get(resultKey)).thenReturn(null);
        when(valueOperations.setIfAbsent(eq(lockKey), anyString(), any(Duration.class))).thenReturn(true);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        TestResult expected = new TestResult("ok");

        TestResult result = singleFlightService.execute(key, TestResult.class, () -> expected);

        assertEquals(expected, result);

        ArgumentCaptor<String> jsonCaptor = ArgumentCaptor.forClass(String.class);
        verify(valueOperations).set(eq(resultKey), jsonCaptor.capture(), any(Duration.class));

        SingleFlightEnvelope envelope = objectMapper.readValue(jsonCaptor.getValue(), SingleFlightEnvelope.class);
        assertTrue(envelope.isSuccess());
        assertNotNull(envelope.getPayload());

        TestResult storedPayload = objectMapper.readValue(envelope.getPayload(), TestResult.class);
        assertEquals("ok", storedPayload.value());

        verify(valueOperations, times(1)).setIfAbsent(eq(lockKey), anyString(), any(Duration.class));
        verify(redisTemplate, atLeastOnce()).execute(any(), eq(Collections.singletonList(lockKey)), anyString());
    }

    @Test
    void execute_whenResultAlreadyCached_thenReturnsFastPathWithoutExecutingOperation() throws Exception {
        String key = "article:fast";
        String resultKey = "result:singleflight:" + key;

        SingleFlightEnvelope cachedEnvelope =
                new SingleFlightEnvelope(true, objectMapper.writeValueAsString(new TestResult("cached")), null, null);

        when(valueOperations.get(resultKey)).thenReturn(objectMapper.writeValueAsString(cachedEnvelope));
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        AtomicInteger executions = new AtomicInteger(0);

        TestResult result = singleFlightService.execute(key, TestResult.class, () -> {
            executions.incrementAndGet();
            return new TestResult("should-not-execute");
        });

        assertEquals("cached", result.value());
        assertEquals(0, executions.get());

        verify(valueOperations, times(1)).get(resultKey);
        verify(valueOperations, never()).setIfAbsent(anyString(), anyString(), any(Duration.class));
    }

    @Test
    void execute_whenRedisReadFails_thenFallsBackToInMemorySingleFlight() throws Exception {

        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        String key = "article:fallback";
        when(valueOperations.get(anyString())).thenThrow(new RuntimeException("redis down"));

        AtomicInteger executions = new AtomicInteger(0);

        TestResult result = singleFlightService.execute(key, TestResult.class, () -> {
            executions.incrementAndGet();
            return new TestResult("in-memory");
        });

        assertEquals("in-memory", result.value());
        assertEquals(1, executions.get());
    }

    @Test
    void execute_whenEnvelopeIndicatesError_thenThrowsExternalApiException() throws Exception {
        String key = "article:error";
        String resultKey = "result:singleflight:" + key;

        SingleFlightEnvelope errorEnvelope =
                new SingleFlightEnvelope(false, null, "EXTERNAL_API", "failure");

        when(valueOperations.get(resultKey)).thenReturn(objectMapper.writeValueAsString(errorEnvelope));
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        ExternalApiException ex = assertThrows(
                ExternalApiException.class,
                () -> singleFlightService.execute(key, TestResult.class, () -> new TestResult("unused"))
        );

        assertTrue(ex.getMessage().contains("EXTERNAL_API"));
        assertTrue(ex.getMessage().contains("failure"));
    }

    @Test
    void execute_whenSuccessEnvelopeMissingPayload_thenThrowsExternalApiException() throws Exception {
        String key = "article:missing-payload";
        String resultKey = "result:singleflight:" + key;

        SingleFlightEnvelope badEnvelope =
                new SingleFlightEnvelope(true, null, null, null);

        when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        when(valueOperations.get(resultKey)).thenReturn(objectMapper.writeValueAsString(badEnvelope));

        ExternalApiException ex = assertThrows(
                ExternalApiException.class,
                () -> singleFlightService.execute(key, TestResult.class, () -> new TestResult("unused"))
        );

        assertTrue(ex.getMessage().contains("missing payload"));
    }

    @Test
    void execute_whenKeyIsNull_thenThrowsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class,
                () -> singleFlightService.execute(null, TestResult.class, () -> new TestResult("x")));
    }

    @Test
    void execute_whenResultTypeIsNull_thenThrowsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class,
                () -> singleFlightService.execute("k", null, () -> new TestResult("x")));
    }

    @Test
    void execute_withConcurrentInMemoryFallback_onlyExecutesOperationOnce() throws Exception {

        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        // Force Redis failure so in-memory single-flight is used
        when(valueOperations.get(anyString())).thenThrow(new RuntimeException("redis down"));

        int threadCount = 6;
        ExecutorService pool = Executors.newFixedThreadPool(threadCount);

        CountDownLatch startLatch = new CountDownLatch(1);
        AtomicInteger executions = new AtomicInteger(0);

        CompletableFuture<TestResult>[] futures = new CompletableFuture[threadCount];

        for (int i = 0; i < threadCount; i++) {
            futures[i] = CompletableFuture.supplyAsync(() -> {
                try {
                    startLatch.await();
                    return singleFlightService.execute("concurrent-key", TestResult.class, () -> {
                        executions.incrementAndGet();
                        Thread.sleep(50);
                        return new TestResult("shared");
                    });
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }, pool);
        }

        startLatch.countDown();

        for (CompletableFuture<TestResult> future : futures) {
            assertEquals("shared", future.join().value());
        }

        assertEquals(1, executions.get(), "Operation should run exactly once in in-memory single-flight");

        pool.shutdownNow();
    }

    private record TestResult(String value) {}
}



