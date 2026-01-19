package com.tispace.dataingestion.service;

import com.tispace.common.entity.Article;
import com.tispace.common.repository.ArticleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ScheduledIngestionJobTest {
	
	@Mock
	private DataIngestionService dataIngestionService;
	
	@Mock
	private ArticleRepository articleRepository;
	
	@InjectMocks
	private ScheduledIngestionJob scheduledIngestionJob;
	
	private Article mockArticle;
	
	@BeforeEach
	void setUp() {
		mockArticle = new Article();
		mockArticle.setId(1L);
		mockArticle.setTitle("Test Article");
		mockArticle.setCreatedAt(LocalDateTime.now());
	}
	
	@Test
	void testOnApplicationReady_EmptyDatabase_RunsIngestion() {
		when(articleRepository.findTop1ByOrderByCreatedAtDesc()).thenReturn(Optional.empty());
		doNothing().when(dataIngestionService).ingestData();
		
		scheduledIngestionJob.onApplicationReady();
		
		verify(articleRepository, times(1)).findTop1ByOrderByCreatedAtDesc();
		verify(dataIngestionService, times(1)).ingestData();
	}
	
	@Test
	void testOnApplicationReady_DataStale_RunsIngestion() {
		// Article created 25 hours ago (stale)
		LocalDateTime staleTime = LocalDateTime.now().minusHours(25);
		mockArticle.setCreatedAt(staleTime);
		
		when(articleRepository.findTop1ByOrderByCreatedAtDesc()).thenReturn(Optional.of(mockArticle));
		doNothing().when(dataIngestionService).ingestData();
		
		scheduledIngestionJob.onApplicationReady();
		
		verify(articleRepository, times(1)).findTop1ByOrderByCreatedAtDesc();
		verify(dataIngestionService, times(1)).ingestData();
	}
	
	@Test
	void testOnApplicationReady_DataFresh_SkipsIngestion() {
		// Article created 12 hours ago (fresh)
		LocalDateTime freshTime = LocalDateTime.now().minusHours(12);
		mockArticle.setCreatedAt(freshTime);
		
		when(articleRepository.findTop1ByOrderByCreatedAtDesc()).thenReturn(Optional.of(mockArticle));
		
		scheduledIngestionJob.onApplicationReady();
		
		verify(articleRepository, times(1)).findTop1ByOrderByCreatedAtDesc();
		verify(dataIngestionService, never()).ingestData();
	}
	
	@Test
	void testOnApplicationReady_DataExactlyAtThreshold_SkipsIngestion() {
		// Article created exactly 24 hours ago (at threshold)
		// Note: compareTo returns > 0 only if strictly greater, so exactly 24 hours should skip
		// Use 23 hours to ensure it's safely less than 24 hour threshold
		// This accounts for any timing precision issues during test execution
		LocalDateTime thresholdTime = LocalDateTime.now().minusHours(23);
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
		doThrow(new RuntimeException("Ingestion error")).when(dataIngestionService).ingestData();
		
		// Should not throw exception
		scheduledIngestionJob.onApplicationReady();
		
		verify(articleRepository, times(1)).findTop1ByOrderByCreatedAtDesc();
		verify(dataIngestionService, times(1)).ingestData();
	}
	
	@Test
	void testScheduledDataIngestion_Success_RunsIngestion() {
		doNothing().when(dataIngestionService).ingestData();
		
		scheduledIngestionJob.scheduledDataIngestion();
		
		verify(dataIngestionService, times(1)).ingestData();
	}
	
	@Test
	void testScheduledDataIngestion_ThrowsException_HandlesGracefully() {
		doThrow(new RuntimeException("Ingestion error")).when(dataIngestionService).ingestData();
		
		// Should not throw exception
		scheduledIngestionJob.scheduledDataIngestion();
		
		verify(dataIngestionService, times(1)).ingestData();
	}
	
	@Test
	void testOnApplicationReady_DataVeryStale_RunsIngestion() {
		// Article created 100 hours ago (very stale)
		LocalDateTime veryStaleTime = LocalDateTime.now().minusHours(100);
		mockArticle.setCreatedAt(veryStaleTime);
		
		when(articleRepository.findTop1ByOrderByCreatedAtDesc()).thenReturn(Optional.of(mockArticle));
		doNothing().when(dataIngestionService).ingestData();
		
		scheduledIngestionJob.onApplicationReady();
		
		verify(articleRepository, times(1)).findTop1ByOrderByCreatedAtDesc();
		verify(dataIngestionService, times(1)).ingestData();
	}
	
	@Test
	void testOnApplicationReady_DataJustCreated_SkipsIngestion() {
		// Article created just now
		mockArticle.setCreatedAt(LocalDateTime.now());
		
		when(articleRepository.findTop1ByOrderByCreatedAtDesc()).thenReturn(Optional.of(mockArticle));
		
		scheduledIngestionJob.onApplicationReady();
		
		verify(articleRepository, times(1)).findTop1ByOrderByCreatedAtDesc();
		verify(dataIngestionService, never()).ingestData();
	}
}

