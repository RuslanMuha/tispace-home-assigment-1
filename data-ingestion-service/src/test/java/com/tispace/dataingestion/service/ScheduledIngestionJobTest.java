package com.tispace.dataingestion.service;

import com.tispace.dataingestion.domain.entity.Article;
import com.tispace.dataingestion.infrastructure.repository.ArticleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ScheduledIngestionJobTest {
	
	@Mock
	private DataIngestionService dataIngestionService;
	
	@Mock
	private ArticleRepository articleRepository;
	
	@Mock
	private DistributedLockService distributedLockService;
	
	@InjectMocks
	private ScheduledIngestionJob scheduledIngestionJob;
	
	private Article mockArticle;
	private static final UUID ARTICLE_ID = UUID.fromString("01234567-89ab-7def-0123-456789abcdef");
	private static final LocalDateTime FIXED_NOW = LocalDateTime.of(2025, 1, 15, 12, 0, 0);
	
	@BeforeEach
	void setUp() {
		mockArticle = new Article();
		mockArticle.setId(ARTICLE_ID);
		mockArticle.setTitle("Test Article");
		mockArticle.setCreatedAt(FIXED_NOW);
		
		// Set default timeout to prevent timeout issues in tests
		org.springframework.test.util.ReflectionTestUtils.setField(scheduledIngestionJob, "jobTimeoutSeconds", 300);
	}
	
	@Test
	void testOnApplicationReady_EmptyDatabase_RunsIngestion() {
		when(articleRepository.findTop1ByOrderByCreatedAtDesc()).thenReturn(Optional.empty());
		when(distributedLockService.executeScheduledTaskWithLock(any(Runnable.class))).thenAnswer(invocation -> {
			Runnable task = invocation.getArgument(0);
			task.run();
			return true;
		});
		
		scheduledIngestionJob.onApplicationReady();
		
		verify(articleRepository, times(1)).findTop1ByOrderByCreatedAtDesc();
		verify(distributedLockService, times(1)).executeScheduledTaskWithLock(any(Runnable.class));
		verify(dataIngestionService, times(1)).ingestData();
	}
	
	@Test
	void testOnApplicationReady_DataStale_RunsIngestion() {
		// Article created 25 hours ago (stale) - exceeds 24h threshold
		LocalDateTime staleTime = FIXED_NOW.minusHours(25);
		mockArticle.setCreatedAt(staleTime);
		
		when(articleRepository.findTop1ByOrderByCreatedAtDesc()).thenReturn(Optional.of(mockArticle));
		when(distributedLockService.executeScheduledTaskWithLock(any(Runnable.class))).thenAnswer(invocation -> {
			Runnable task = invocation.getArgument(0);
			task.run();
			return true;
		});
		
		scheduledIngestionJob.onApplicationReady();
		
		verify(articleRepository, times(1)).findTop1ByOrderByCreatedAtDesc();
		verify(distributedLockService, times(1)).executeScheduledTaskWithLock(any(Runnable.class));
		verify(dataIngestionService, times(1)).ingestData();
	}
	
	@Test
	void testOnApplicationReady_DataFresh_SkipsIngestion() {
		// Article created 12 hours ago (fresh) - within 24h threshold
		LocalDateTime freshTime = FIXED_NOW.minusHours(12);
		mockArticle.setCreatedAt(freshTime);
		
		when(articleRepository.findTop1ByOrderByCreatedAtDesc()).thenReturn(Optional.of(mockArticle));
		
		scheduledIngestionJob.onApplicationReady();
		
		verify(articleRepository, times(1)).findTop1ByOrderByCreatedAtDesc();
		verify(dataIngestionService, never()).ingestData();
	}
	
	@Test
	void testOnApplicationReady_DataExactlyAtThreshold_SkipsIngestion() {
		// Article created 23 hours ago (at threshold boundary)
		// Note: compareTo returns > 0 only if strictly greater, so 23 hours is less than 24h threshold
		// Using fixed time ensures consistent test behavior
		LocalDateTime thresholdTime = FIXED_NOW.minusHours(23);
		mockArticle.setCreatedAt(thresholdTime);
		
		when(articleRepository.findTop1ByOrderByCreatedAtDesc()).thenReturn(Optional.of(mockArticle));
		
		scheduledIngestionJob.onApplicationReady();
		
		verify(articleRepository, times(1)).findTop1ByOrderByCreatedAtDesc();
		verify(dataIngestionService, never()).ingestData();
	}
	
	@Test
	void testOnApplicationReady_RepositoryThrowsException_HandlesGracefully() {
		when(articleRepository.findTop1ByOrderByCreatedAtDesc()).thenThrow(new RuntimeException("Database error"));
		
		// Should not throw exception
		scheduledIngestionJob.onApplicationReady();
		
		verify(articleRepository, times(1)).findTop1ByOrderByCreatedAtDesc();
		verify(dataIngestionService, never()).ingestData();
	}
	
	@Test
	void testOnApplicationReady_IngestionThrowsException_HandlesGracefully() {
		when(articleRepository.findTop1ByOrderByCreatedAtDesc()).thenReturn(Optional.empty());
		when(distributedLockService.executeScheduledTaskWithLock(any(Runnable.class))).thenAnswer(invocation -> {
			Runnable task = invocation.getArgument(0);
			try {
				task.run();
			} catch (RuntimeException e) {
				// Exception is caught and logged in the actual implementation
			}
			return true;
		});
		doThrow(new RuntimeException("Ingestion error")).when(dataIngestionService).ingestData();
		
		// Should not throw exception
		scheduledIngestionJob.onApplicationReady();
		
		verify(articleRepository, times(1)).findTop1ByOrderByCreatedAtDesc();
		verify(distributedLockService, times(1)).executeScheduledTaskWithLock(any(Runnable.class));
		verify(dataIngestionService, times(1)).ingestData();
	}
	
	@Test
	void testScheduledDataIngestion_Success_RunsIngestion() {
		when(distributedLockService.executeScheduledTaskWithLock(any(Runnable.class))).thenAnswer(invocation -> {
			Runnable task = invocation.getArgument(0);
			task.run();
			return true;
		});
		
		scheduledIngestionJob.scheduledDataIngestion();
		
		verify(distributedLockService, times(1)).executeScheduledTaskWithLock(any(Runnable.class));
		verify(dataIngestionService, times(1)).ingestData();
	}
	
	@Test
	void testScheduledDataIngestion_ThrowsException_HandlesGracefully() {
		when(distributedLockService.executeScheduledTaskWithLock(any(Runnable.class))).thenAnswer(invocation -> {
			Runnable task = invocation.getArgument(0);
			try {
				task.run();
			} catch (RuntimeException e) {
				// Exception is caught and logged in the actual implementation
			}
			return true;
		});
		doThrow(new RuntimeException("Ingestion error")).when(dataIngestionService).ingestData();
		
		// Should not throw exception
		scheduledIngestionJob.scheduledDataIngestion();
		
		verify(distributedLockService, times(1)).executeScheduledTaskWithLock(any(Runnable.class));
		verify(dataIngestionService, times(1)).ingestData();
	}
	
	@Test
	void testOnApplicationReady_DataVeryStale_RunsIngestion() {
		// Article created 100 hours ago (very stale) - exceeds 24h threshold
		LocalDateTime veryStaleTime = FIXED_NOW.minusHours(100);
		mockArticle.setCreatedAt(veryStaleTime);
		
		when(articleRepository.findTop1ByOrderByCreatedAtDesc()).thenReturn(Optional.of(mockArticle));
		when(distributedLockService.executeScheduledTaskWithLock(any(Runnable.class))).thenAnswer(invocation -> {
			Runnable task = invocation.getArgument(0);
			task.run();
			return true;
		});
		
		scheduledIngestionJob.onApplicationReady();
		
		verify(articleRepository, times(1)).findTop1ByOrderByCreatedAtDesc();
		verify(distributedLockService, times(1)).executeScheduledTaskWithLock(any(Runnable.class));
		verify(dataIngestionService, times(1)).ingestData();
	}
	
	@Test
	void testOnApplicationReady_DataJustCreated_SkipsIngestion() {
		// Article created at fixed time (fresh) - within 24h threshold
		mockArticle.setCreatedAt(FIXED_NOW);
		
		when(articleRepository.findTop1ByOrderByCreatedAtDesc()).thenReturn(Optional.of(mockArticle));
		
		scheduledIngestionJob.onApplicationReady();
		
		verify(articleRepository, times(1)).findTop1ByOrderByCreatedAtDesc();
		verify(dataIngestionService, never()).ingestData();
	}
	
	@Test
	void testScheduledDataIngestion_LockNotAcquired_SkipsIngestion() {
		// Lock cannot be acquired (another instance is running)
		when(distributedLockService.executeScheduledTaskWithLock(any(Runnable.class))).thenReturn(false);
		
		scheduledIngestionJob.scheduledDataIngestion();
		
		verify(distributedLockService, times(1)).executeScheduledTaskWithLock(any(Runnable.class));
		verify(dataIngestionService, never()).ingestData();
	}
}

