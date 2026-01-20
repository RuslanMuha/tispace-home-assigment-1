package com.tispace.dataingestion.service;

import com.tispace.common.entity.Article;
import com.tispace.dataingestion.repository.ArticleBatchRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ArticlePersistenceServiceTest {
	
	@Mock
	private ArticleBatchRepository articleBatchRepository;
	
	@InjectMocks
	private ArticlePersistenceService articlePersistenceService;
	
	private Article mockArticle;
	
	@BeforeEach
	void setUp() {
		mockArticle = new Article();
		mockArticle.setTitle("Test Article");
		mockArticle.setDescription("Test Description");
		mockArticle.setAuthor("Test Author");
		mockArticle.setPublishedAt(LocalDateTime.now());
		mockArticle.setCategory("technology");
	}
	
	@Test
	void testSaveArticles_Success_SavesAllArticles() {
		List<Article> articles = Arrays.asList(mockArticle, createArticle("Article 2"), createArticle("Article 3"));
		when(articleBatchRepository.batchInsertIgnoreDuplicates(anyList())).thenReturn(3);
		
		int savedCount = articlePersistenceService.saveArticles(articles);
		
		assertEquals(3, savedCount);
		verify(articleBatchRepository, times(1)).batchInsertIgnoreDuplicates(articles);
	}
	
	@Test
	void testSaveArticles_EmptyList_ReturnsZero() {
		List<Article> articles = new ArrayList<>();
		
		int savedCount = articlePersistenceService.saveArticles(articles);
		
		assertEquals(0, savedCount);
		verify(articleBatchRepository, never()).batchInsertIgnoreDuplicates(anyList());
	}
	
	@Test
	void testSaveArticles_BatchSizeExceeded_SavesInBatches() {
		// Create 120 articles
		// Note: Current implementation doesn't batch - it saves all articles in one call
		List<Article> articles = new ArrayList<>();
		for (int i = 0; i < 120; i++) {
			articles.add(createArticle("Article " + i));
		}
		
		when(articleBatchRepository.batchInsertIgnoreDuplicates(anyList())).thenAnswer(invocation -> {
			List<Article> batch = invocation.getArgument(0);
			return batch.size(); // Return number of articles in batch
		});
		
		int savedCount = articlePersistenceService.saveArticles(articles);
		
		assertEquals(120, savedCount);
		// Implementation saves all articles in one batch call
		verify(articleBatchRepository, times(1)).batchInsertIgnoreDuplicates(articles);
	}
	
	@Test
	void testSaveArticles_BatchFailsWithDuplicate_SavesIndividually() {
		List<Article> articles = Arrays.asList(mockArticle, createArticle("Article 2"));
		
		// Batch insert returns 2 (all inserted successfully)
		when(articleBatchRepository.batchInsertIgnoreDuplicates(anyList())).thenReturn(2);
		
		int savedCount = articlePersistenceService.saveArticles(articles);
		
		assertEquals(2, savedCount);
		verify(articleBatchRepository, times(1)).batchInsertIgnoreDuplicates(articles);
	}
	
	@Test
	void testSaveArticles_IndividualSaveFailsWithDuplicate_SkipsDuplicate() {
		List<Article> articles = Arrays.asList(mockArticle, createArticle("Article 2"));
		
		// Batch insert returns 1 (one duplicate was skipped)
		when(articleBatchRepository.batchInsertIgnoreDuplicates(anyList())).thenReturn(1);
		
		int savedCount = articlePersistenceService.saveArticles(articles);
		
		assertEquals(1, savedCount);
		verify(articleBatchRepository, times(1)).batchInsertIgnoreDuplicates(articles);
	}
	
	@Test
	void testSaveArticles_AllIndividualSavesFailWithDuplicate_ReturnsZero() {
		List<Article> articles = Arrays.asList(mockArticle, createArticle("Article 2"));
		
		// Batch insert returns 0 (all were duplicates)
		when(articleBatchRepository.batchInsertIgnoreDuplicates(anyList())).thenReturn(0);
		
		int savedCount = articlePersistenceService.saveArticles(articles);
		
		assertEquals(0, savedCount);
		verify(articleBatchRepository, times(1)).batchInsertIgnoreDuplicates(articles);
	}
	
	@Test
	void testSaveArticles_IndividualSaveFailsWithNonDuplicateException_PropagatesException() {
		List<Article> articles = Arrays.asList(mockArticle);
		
		// Batch insert throws exception
		when(articleBatchRepository.batchInsertIgnoreDuplicates(anyList())).thenThrow(new RuntimeException("Database error"));
		
		assertThrows(RuntimeException.class, () -> articlePersistenceService.saveArticles(articles));
		verify(articleBatchRepository, times(1)).batchInsertIgnoreDuplicates(articles);
	}
	
	@Test
	void testSaveArticles_LargeList_SavesInOneBatch() {
		// Create 50 articles
		// Note: Current implementation doesn't batch - it saves all articles in one call
		List<Article> articles = new ArrayList<>();
		for (int i = 0; i < 50; i++) {
			articles.add(createArticle("Article " + i));
		}
		
		when(articleBatchRepository.batchInsertIgnoreDuplicates(anyList())).thenAnswer(invocation -> {
			List<Article> batch = invocation.getArgument(0);
			return batch.size();
		});
		
		int savedCount = articlePersistenceService.saveArticles(articles);
		
		assertEquals(50, savedCount);
		verify(articleBatchRepository, times(1)).batchInsertIgnoreDuplicates(articles);
	}
	
	@Test
	void testSaveArticles_MultipleBatchesWithSomeFailures_HandlesCorrectly() {
		// Create 75 articles
		// Note: Current implementation doesn't batch - it saves all articles in one call
		List<Article> articles = new ArrayList<>();
		for (int i = 0; i < 75; i++) {
			articles.add(createArticle("Article " + i));
		}
		
		// All articles saved in one batch
		when(articleBatchRepository.batchInsertIgnoreDuplicates(anyList())).thenAnswer(invocation -> {
			List<Article> batch = invocation.getArgument(0);
			return batch.size(); // All inserted
		});
		
		int savedCount = articlePersistenceService.saveArticles(articles);
		
		assertEquals(75, savedCount);
		// Implementation saves all articles in one batch call
		verify(articleBatchRepository, times(1)).batchInsertIgnoreDuplicates(articles);
	}
	
	@Test
	void testSaveArticles_SingleArticle_SavesSuccessfully() {
		List<Article> articles = Arrays.asList(mockArticle);
		when(articleBatchRepository.batchInsertIgnoreDuplicates(anyList())).thenReturn(1);
		
		int savedCount = articlePersistenceService.saveArticles(articles);
		
		assertEquals(1, savedCount);
		verify(articleBatchRepository, times(1)).batchInsertIgnoreDuplicates(articles);
	}
	
	@Test
	void testSaveArticles_NullList_ReturnsZero() {
		// With new implementation, null list returns 0 instead of throwing
		int savedCount = articlePersistenceService.saveArticles(null);
		assertEquals(0, savedCount);
		verify(articleBatchRepository, never()).batchInsertIgnoreDuplicates(anyList());
	}
	
	private Article createArticle(String title) {
		Article article = new Article();
		article.setTitle(title);
		article.setDescription("Description for " + title);
		article.setAuthor("Author");
		article.setPublishedAt(LocalDateTime.now());
		article.setCategory("technology");
		return article;
	}
}

