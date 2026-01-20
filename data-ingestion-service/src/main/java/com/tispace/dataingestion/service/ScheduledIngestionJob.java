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

@Component
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(name = "scheduler.enabled", havingValue = "true", matchIfMissing = true)
public class ScheduledIngestionJob {
	
	private final DataIngestionService dataIngestionService;
	private final ArticleRepository articleRepository;
	private final DistributedLockService distributedLockService;
	
	private static final Duration DATA_STALENESS_THRESHOLD = Duration.ofHours(24);
	
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


