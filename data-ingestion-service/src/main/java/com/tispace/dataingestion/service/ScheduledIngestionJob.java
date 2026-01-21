package com.tispace.dataingestion.service;

import com.tispace.common.entity.Article;
import com.tispace.common.repository.ArticleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Optional;

/**
 * Scheduled job for periodic data ingestion with distributed locking.
 * Prevents concurrent execution across multiple service instances.
 * 
 * <p>Startup behavior: Checks if database is empty or data is stale (>24h).
 * Runs initial ingestion if needed to avoid cold start delays.
 * 
 * <p>Distributed locking: Uses PostgreSQL advisory locks to ensure only one instance
 * executes the job in multi-instance deployments. Other instances skip execution silently.
 * 
 * <p>Schedule: Configurable via scheduler.cron (default: every 6 hours).
 * Can be disabled via scheduler.enabled=false.
 * 
 * <p>Side effects: External API calls, database writes, distributed lock acquisition.
 * 
 * <p>Error handling: Job failures are logged but don't crash the application.
 * Lock acquisition failures are handled gracefully (job skipped).
 */
@Component
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(name = "scheduler.enabled", havingValue = "true", matchIfMissing = true)
public class ScheduledIngestionJob {
	
	private final DataIngestionService dataIngestionService;
	private final ArticleRepository articleRepository;
	private final DistributedLockService distributedLockService;
	
	private static final Duration DATA_STALENESS_THRESHOLD = Duration.ofHours(24);
	
	/**
	 * Runs on application startup to check if initial data ingestion is needed.
	 * Triggers ingestion if database is empty or last article is older than threshold.
	 * 
	 * <p>This ensures fresh data on startup without waiting for first scheduled run.
	 * Errors are logged but don't prevent application startup.
	 */
	@EventListener(ApplicationReadyEvent.class)
	public void onApplicationReady() {
		log.info("Checking if initial data ingestion is needed on startup");
		try {
			Optional<Article> lastArticle = articleRepository.findTop1ByOrderByCreatedAtDesc();
			
			if (lastArticle.isEmpty()) {
				log.info("Database is empty, running initial data ingestion");
				scheduledDataIngestion();
				return;
			}
			
			LocalDateTime lastArticleCreatedAt = lastArticle.get().getCreatedAt();
			LocalDateTime now = LocalDateTime.now();
			Duration timeSinceLastArticle = Duration.between(lastArticleCreatedAt, now);
			
			if (timeSinceLastArticle.compareTo(DATA_STALENESS_THRESHOLD) > 0) {
				log.info("Last article was created {} hours ago (threshold: {} hours), running data ingestion",
					timeSinceLastArticle.toHours(), DATA_STALENESS_THRESHOLD.toHours());
				scheduledDataIngestion();
			} else {
				log.info("Last article was created {} hours ago (threshold: {} hours), skipping initial data ingestion",
					timeSinceLastArticle.toHours(), DATA_STALENESS_THRESHOLD.toHours());
			}
		} catch (Exception e) {
			log.error("Error during startup data ingestion check", e);
		}
	}
	
	/**
	 * Scheduled data ingestion job executed periodically.
	 * Uses distributed lock to prevent concurrent execution across instances.
	 * 
	 * <p>If lock is acquired: executes ingestion and releases lock on completion/failure.
	 * If lock is not acquired: skips execution (another instance is running).
	 * 
	 * <p>Schedule: Configured via scheduler.cron (default: every 6 hours at minute 0).
	 * 
	 * <p>Side effects: External API calls, database writes, distributed lock operations.
	 * 
	 * <p>Error handling: Ingestion failures are logged and rethrown (causes lock release).
	 * Lock acquisition failures are handled gracefully (returns false, job skipped).
	 */
	@Scheduled(cron = "${scheduler.cron:0 0 */6 * * *}", zone = "UTC")
	public void scheduledDataIngestion() {
		log.info("Attempting to acquire distributed lock for scheduled data ingestion job");
		
		boolean executed = distributedLockService.executeScheduledTaskWithLock(() -> {
			log.info("Distributed lock acquired, starting scheduled data ingestion job");
			try {
				dataIngestionService.ingestData();
				log.info("Scheduled data ingestion job completed successfully");
			} catch (Exception e) {
				log.error("Scheduled data ingestion job failed", e);
				throw new RuntimeException("Data ingestion failed", e);
			}
		});
		
		if (!executed) {
			log.info("Scheduled data ingestion job skipped - another instance is already running");
		}
	}
}


