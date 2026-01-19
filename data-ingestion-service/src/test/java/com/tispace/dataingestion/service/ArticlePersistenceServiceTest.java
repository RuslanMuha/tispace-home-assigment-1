package com.tispace.dataingestion.service;

import com.tispace.common.entity.Article;
import com.tispace.common.repository.ArticleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ArticlePersistenceServiceTest {
	
	@Mock
	private ArticleRepository articleRepository;
	
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
		when(articleRepository.saveAll(anyList())).thenReturn(articles);
		
		int savedCount = articlePersistenceService.saveArticles(articles);
		
		assertEquals(3, savedCount);
		verify(articleRepository, times(1)).saveAll(articles);
		verify(articleRepository, never()).save(any(Article.class));
	}
	
	@Test
	void testSaveArticles_EmptyList_ReturnsZero() {
		List<Article> articles = new ArrayList<>();
		
		int savedCount = articlePersistenceService.saveArticles(articles);
		
		assertEquals(0, savedCount);
		verify(articleRepository, never()).saveAll(anyList());
		verify(articleRepository, never()).save(any(Article.class));
	}
	
	@Test
	void testSaveArticles_BatchSizeExceeded_SavesInBatches() {
		// Create 120 articles (more than batch size of 50)
		List<Article> articles = new ArrayList<>();
		for (int i = 0; i < 120; i++) {
			articles.add(createArticle("Article " + i));
		}
		
		when(articleRepository.saveAll(anyList())).thenAnswer(invocation -> {
			List<Article> batch = invocation.getArgument(0);
			return batch;
		});
		
		int savedCount = articlePersistenceService.saveArticles(articles);
		
		assertEquals(120, savedCount);
		// Should be called 3 times: 50 + 50 + 20
		verify(articleRepository, times(3)).saveAll(anyList());
	}
	
	@Test
	void testSaveArticles_BatchFailsWithDuplicate_SavesIndividually() {
		List<Article> articles = Arrays.asList(mockArticle, createArticle("Article 2"));
		
		// First batch save fails with duplicate
		when(articleRepository.saveAll(anyList())).thenThrow(new DataIntegrityViolationException("Duplicate key"));
		when(articleRepository.save(any(Article.class))).thenAnswer(invocation -> invocation.getArgument(0));
		
		int savedCount = articlePersistenceService.saveArticles(articles);
		
		assertEquals(2, savedCount);
		verify(articleRepository, times(1)).saveAll(anyList());
		verify(articleRepository, times(2)).save(any(Article.class));
	}
	
	@Test
	void testSaveArticles_IndividualSaveFailsWithDuplicate_SkipsDuplicate() {
		List<Article> articles = Arrays.asList(mockArticle, createArticle("Article 2"));
		
		// Batch save fails
		when(articleRepository.saveAll(anyList())).thenThrow(new DataIntegrityViolationException("Duplicate key"));
		
		// First individual save succeeds, second fails with duplicate
		when(articleRepository.save(mockArticle)).thenReturn(mockArticle);
		when(articleRepository.save(articles.get(1))).thenThrow(new DataIntegrityViolationException("Duplicate key"));
		
		int savedCount = articlePersistenceService.saveArticles(articles);
		
		assertEquals(1, savedCount);
		verify(articleRepository, times(1)).saveAll(anyList());
		verify(articleRepository, times(2)).save(any(Article.class));
	}
	
	@Test
	void testSaveArticles_AllIndividualSavesFailWithDuplicate_ReturnsZero() {
		List<Article> articles = Arrays.asList(mockArticle, createArticle("Article 2"));
		
		// Batch save fails
		when(articleRepository.saveAll(anyList())).thenThrow(new DataIntegrityViolationException("Duplicate key"));
		
		// All individual saves fail with duplicate
		when(articleRepository.save(any(Article.class))).thenThrow(new DataIntegrityViolationException("Duplicate key"));
		
		int savedCount = articlePersistenceService.saveArticles(articles);
		
		assertEquals(0, savedCount);
		verify(articleRepository, times(1)).saveAll(anyList());
		verify(articleRepository, times(2)).save(any(Article.class));
	}
	
	@Test
	void testSaveArticles_IndividualSaveFailsWithNonDuplicateException_PropagatesException() {
		List<Article> articles = Arrays.asList(mockArticle);
		
		// Batch save fails
		when(articleRepository.saveAll(anyList())).thenThrow(new DataIntegrityViolationException("Duplicate key"));
		
		// Individual save fails with non-duplicate exception
		when(articleRepository.save(any(Article.class))).thenThrow(new RuntimeException("Database error"));
		
		assertThrows(RuntimeException.class, () -> articlePersistenceService.saveArticles(articles));
		verify(articleRepository, times(1)).saveAll(anyList());
		verify(articleRepository, times(1)).save(any(Article.class));
	}
	
	@Test
	void testSaveArticles_ExactBatchSize_SavesInOneBatch() {
		// Create exactly 50 articles (batch size)
		List<Article> articles = new ArrayList<>();
		for (int i = 0; i < 50; i++) {
			articles.add(createArticle("Article " + i));
		}
		
		when(articleRepository.saveAll(anyList())).thenAnswer(invocation -> {
			List<Article> batch = invocation.getArgument(0);
			return batch;
		});
		
		int savedCount = articlePersistenceService.saveArticles(articles);
		
		assertEquals(50, savedCount);
		verify(articleRepository, times(1)).saveAll(anyList());
	}
	
	@Test
	void testSaveArticles_MultipleBatchesWithSomeFailures_HandlesCorrectly() {
		// Create 75 articles (2 batches: 50 + 25)
		List<Article> articles = new ArrayList<>();
		for (int i = 0; i < 75; i++) {
			articles.add(createArticle("Article " + i));
		}
		
		// First batch succeeds, second batch fails with duplicate
		when(articleRepository.saveAll(anyList())).thenAnswer(invocation -> {
			List<Article> batch = invocation.getArgument(0);
			if (batch.size() == 50) {
				return batch; // First batch succeeds
			} else {
				throw new DataIntegrityViolationException("Duplicate key"); // Second batch fails
			}
		});
		
		// Individual saves for second batch succeed
		when(articleRepository.save(any(Article.class))).thenAnswer(invocation -> invocation.getArgument(0));
		
		int savedCount = articlePersistenceService.saveArticles(articles);
		
		assertEquals(75, savedCount);
		verify(articleRepository, times(2)).saveAll(anyList());
		verify(articleRepository, times(25)).save(any(Article.class));
	}
	
	@Test
	void testSaveArticles_SingleArticle_SavesSuccessfully() {
		List<Article> articles = Arrays.asList(mockArticle);
		when(articleRepository.saveAll(anyList())).thenReturn(articles);
		
		int savedCount = articlePersistenceService.saveArticles(articles);
		
		assertEquals(1, savedCount);
		verify(articleRepository, times(1)).saveAll(articles);
	}
	
	@Test
	void testSaveArticles_NullList_ThrowsException() {
		assertThrows(NullPointerException.class, () -> articlePersistenceService.saveArticles(null));
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

