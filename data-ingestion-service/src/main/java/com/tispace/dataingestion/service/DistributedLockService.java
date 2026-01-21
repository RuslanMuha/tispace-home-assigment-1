package com.tispace.dataingestion.service;

import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.function.Supplier;

import static org.springframework.transaction.annotation.Propagation.REQUIRES_NEW;

/**
 * Distributed locking via PostgreSQL advisory locks (pg_try_advisory_xact_lock).
 * Lock auto-releases on transaction end. Returns false if lock not acquired.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DistributedLockService {

    private final EntityManager entityManager;

    private static final long SCHEDULER_LOCK_ID = 123456789L;

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

    @Transactional(propagation = REQUIRES_NEW)
    public boolean executeScheduledTaskWithLock(Runnable task) {
        return executeWithLock(SCHEDULER_LOCK_ID, () -> {
            task.run();
            return true;
        });
    }
}

