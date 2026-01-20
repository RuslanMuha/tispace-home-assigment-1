package com.tispace.dataingestion.service;

import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DistributedLockServiceTest {

    @Mock
    private EntityManager entityManager;

    @Mock
    private Query query;

    @InjectMocks
    private DistributedLockService distributedLockService;

    @BeforeEach
    void setUp() {
        // Mock the chain: createNativeQuery().setParameter().getSingleResult()
        lenient().when(entityManager.createNativeQuery(any(String.class))).thenReturn(query);
        lenient().when(query.setParameter(anyInt(), any())).thenReturn(query);
    }

    @Test
    void executeWithLock_whenLockAcquired_thenExecutesTaskAndReturnsTrue() {
        long lockId = 42L;
        when(query.getSingleResult()).thenReturn(Boolean.TRUE);

        @SuppressWarnings("unchecked")
        Supplier<Boolean> task = mock(Supplier.class);
        when(task.get()).thenReturn(true);

        boolean result = distributedLockService.executeWithLock(lockId, task);

        assertTrue(result);
        verify(entityManager).createNativeQuery("SELECT pg_try_advisory_xact_lock(?1)");
        verify(query).setParameter(1, lockId);
        verify(query).getSingleResult();
        verify(task, times(1)).get();
    }

    @Test
    void executeWithLock_whenLockNotAcquired_thenReturnsFalseAndDoesNotExecuteTask() {
        long lockId = 99L;
        when(query.getSingleResult()).thenReturn(Boolean.FALSE);

        @SuppressWarnings("unchecked")
        Supplier<Boolean> task = mock(Supplier.class);

        boolean result = distributedLockService.executeWithLock(lockId, task);

        assertFalse(result);
        verify(query).getSingleResult();
        verify(task, never()).get();
    }

    @Test
    void executeWithLock_whenTaskThrowsException_thenPropagatesException() {
        long lockId = 7L;
        when(query.getSingleResult()).thenReturn(Boolean.TRUE);

        @SuppressWarnings("unchecked")
        Supplier<Boolean> task = mock(Supplier.class);
        when(task.get()).thenThrow(new IllegalStateException("task failed"));

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> distributedLockService.executeWithLock(lockId, task));

        assertEquals("task failed", ex.getMessage());
        verify(query).getSingleResult();
        verify(task, times(1)).get();
    }

    @Test
    void executeWithLock_whenDatabaseError_thenReturnsFalse() {
        long lockId = 13L;
        when(entityManager.createNativeQuery(any(String.class))).thenThrow(new RuntimeException("db error"));

        @SuppressWarnings("unchecked")
        Supplier<Boolean> task = mock(Supplier.class);

        boolean result = distributedLockService.executeWithLock(lockId, task);

        assertFalse(result);
        verify(task, never()).get();
    }

    @Test
    void executeScheduledTaskWithLock_whenLockAcquired_thenExecutesRunnableAndReturnsTrue() {
        when(query.getSingleResult()).thenReturn(Boolean.TRUE);

        Runnable runnable = mock(Runnable.class);

        boolean result = distributedLockService.executeScheduledTaskWithLock(runnable);

        assertTrue(result);
        verify(query).getSingleResult();
        verify(runnable, times(1)).run();
    }

    @Test
    void executeScheduledTaskWithLock_whenLockNotAcquired_thenReturnsFalseAndDoesNotRun() {
        when(query.getSingleResult()).thenReturn(Boolean.FALSE);

        Runnable runnable = mock(Runnable.class);

        boolean result = distributedLockService.executeScheduledTaskWithLock(runnable);

        assertFalse(result);
        verify(query).getSingleResult();
        verify(runnable, never()).run();
    }

    @Test
    void executeWithLock_whenTaskReturnsFalse_thenReturnsFalse() {
        long lockId = 55L;
        when(query.getSingleResult()).thenReturn(Boolean.TRUE);

        @SuppressWarnings("unchecked")
        Supplier<Boolean> task = mock(Supplier.class);
        when(task.get()).thenReturn(false);

        boolean result = distributedLockService.executeWithLock(lockId, task);

        assertFalse(result);
        verify(query).getSingleResult();
        verify(task, times(1)).get();
    }
}



