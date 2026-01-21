package com.tispace.dataingestion.service;

import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.function.Supplier;

import static org.springframework.transaction.annotation.Propagation.REQUIRES_NEW;

/**
 * Service for distributed locking using PostgreSQL advisory locks.
 * Ensures only one instance executes scheduled jobs in a multi-instance environment.
 * 
 * <p>Lock mechanism: Uses pg_try_advisory_xact_lock() which automatically releases
 * on transaction commit/rollback. Lock is scoped to transaction (REQUIRES_NEW).
 * 
 * <p>Guarantees: Only one instance can acquire lock per lockId at a time.
 * Lock is automatically released when transaction ends (commit or rollback).
 * 
 * <p>Edge cases: Lock acquisition failures return false (don't throw).
 * Task exceptions propagate and cause transaction rollback (lock released).
 * 
 * <p>Side effects: Database transaction, advisory lock acquisition/release.
 * 
 * <p>Thread safety: Safe for concurrent calls (PostgreSQL handles lock contention).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DistributedLockService {

    private final EntityManager entityManager;

    private static final long SCHEDULER_LOCK_ID = 123456789L;

    /**
     * Executes task under distributed lock. Lock is automatically released on transaction end.
     * 
     * @param lockId unique lock identifier (must be consistent across instances)
     * @param task task to execute if lock is acquired
     * @return true if lock acquired and task executed, false if lock not acquired
     * @throws RuntimeException if task fails (propagates, causes transaction rollback and lock release)
     */
    @Transactional(propagation = REQUIRES_NEW)
    public boolean executeWithLock(long lockId, Supplier<Boolean> task) {
        final boolean acquired;
        try {
            Object raw = entityManager
                    .createNativeQuery("SELECT pg_try_advisory_xact_lock(?1)")
                    .setParameter(1, lockId)
                    .getSingleResult();

            acquired = Boolean.TRUE.equals(raw);
        } catch (Exception e) {
            log.error("Error acquiring advisory lock lockId={}", lockId, e);
            return false;
        }

        if (!acquired) {
            log.debug("Lock not acquired lockId={} (another instance running)", lockId);
            return false;
        }

        log.debug("Lock acquired lockId={}", lockId);

        boolean result = task.get();

        log.debug("Task completed under lockId={}, result={}", lockId, result);
        return result;
    }

    /**
     * Convenience method for scheduled tasks using predefined scheduler lock ID.
     * 
     * @param task task to execute if lock is acquired
     * @return true if lock acquired and task executed, false if lock not acquired
     * @throws RuntimeException if task fails (propagates)
     */
    @Transactional(propagation = REQUIRES_NEW)
    public boolean executeScheduledTaskWithLock(Runnable task) {
        return executeWithLock(SCHEDULER_LOCK_ID, () -> {
            task.run();
            return true;
        });
    }
}

