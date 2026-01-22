package com.tispace.dataingestion.service;

import com.tispace.dataingestion.domain.entity.Article;
import com.tispace.dataingestion.infrastructure.repository.ArticleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.test.context.TestPropertySource;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Integration tests for ArticleQueryService retry functionality with Resilience4j.
 * Tests actual retry behavior when transient database errors occur.
 */
@SpringBootTest(classes = {
	com.tispace.dataingestion.DataIngestionServiceApplication.class
}, webEnvironment = org.springframework.boot.test.context.SpringBootTest.WebEnvironment.NONE, properties = {
	"spring.main.allow-bean-definition-overriding=true"
})
@TestPropertySource(properties = {
	"resilience4j.retry.instances.database.maxAttempts=3",
	"resilience4j.retry.instances.database.waitDuration=100ms",
	"resilience4j.retry.instances.database.enableExponentialBackoff=true",
	"resilience4j.retry.instances.database.exponentialBackoffMultiplier=2",
	"spring.datasource.url=jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
	"spring.datasource.driver-class-name=org.h2.Driver",
	"spring.jpa.hibernate.ddl-auto=create-drop",
	"spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
	"spring.liquibase.enabled=false",
	"scheduler.enabled=false",
	"query-service.internal-token=test-token",
	"external-api.news-api.api-key=test-key"
})
class ArticleQueryServiceRetryIntegrationTest {
	
	@MockitoBean
	private ArticleRepository articleRepository;
	
	@Autowired
	private ArticleQueryService articleQueryService;
	
	private Article mockArticle;
	private List<Article> mockArticles;
	private static final UUID ARTICLE_ID = UUID.fromString("01234567-89ab-7def-0123-456789abcdef");
	
	@BeforeEach
	void setUp() {
		mockArticle = new Article();
		mockArticle.setId(ARTICLE_ID);
		mockArticle.setTitle("Test Article");
		mockArticle.setDescription("Test Description");
		mockArticle.setAuthor("Test Author");
		mockArticle.setPublishedAt(LocalDateTime.of(2025, 1, 15, 12, 0, 0));
		mockArticle.setCategory("technology");
		
		mockArticles = new ArrayList<>();
		mockArticles.add(mockArticle);
	}
	
	@Test
	void testGetArticles_TransientException_RetriesAndSucceeds() {
		Pageable pageable = PageRequest.of(0, 20);
		Page<Article> page = new PageImpl<>(mockArticles, pageable, 1);
		
		// First two calls throw transient exception, third succeeds
		when(articleRepository.findAll(any(Pageable.class)))
			.thenThrow(new TransientDataAccessException("Connection timeout") {})
			.thenThrow(new TransientDataAccessException("Connection timeout") {})
			.thenReturn(page);
		
		Page<Article> result = articleQueryService.getArticles(pageable, null);
		
		assertNotNull(result);
		assertEquals(1, result.getContent().size());
		assertEquals("Test Article", result.getContent().get(0).getTitle());
		
		// Verify retry happened - repository called 3 times (2 failures + 1 success)
		verify(articleRepository, times(3)).findAll(pageable);
	}
	
	@Test
	void testGetArticles_TransientException_AllRetriesFail_ThrowsException() {
		Pageable pageable = PageRequest.of(0, 20);
		
		// All retry attempts fail
		when(articleRepository.findAll(any(Pageable.class)))
			.thenThrow(new TransientDataAccessException("Connection timeout") {})
			.thenThrow(new TransientDataAccessException("Connection timeout") {})
			.thenThrow(new TransientDataAccessException("Connection timeout") {});
		
		assertThrows(TransientDataAccessException.class, 
			() -> articleQueryService.getArticles(pageable, null));
		
		// Verify all retry attempts (3 max attempts)
		verify(articleRepository, times(3)).findAll(pageable);
	}
	
	@Test
	void testGetArticleById_TransientException_RetriesAndSucceeds() {
		// First call throws transient exception, second succeeds
		when(articleRepository.findById(ARTICLE_ID))
			.thenThrow(new TransientDataAccessException("Connection timeout") {})
			.thenReturn(java.util.Optional.of(mockArticle));
		
		Article result = articleQueryService.getArticleById(ARTICLE_ID);
		
		assertNotNull(result);
		assertEquals(ARTICLE_ID, result.getId());
		assertEquals("Test Article", result.getTitle());
		
		// Verify retry happened - repository called 2 times (1 failure + 1 success)
		verify(articleRepository, times(2)).findById(ARTICLE_ID);
	}
	
	@Test
	void testGetArticles_NonTransientException_NoRetry() {
		Pageable pageable = PageRequest.of(0, 20);
		
		// Non-transient exception should not trigger retry
		when(articleRepository.findAll(any(Pageable.class)))
			.thenThrow(new RuntimeException("Permanent error"));
		
		assertThrows(RuntimeException.class, 
			() -> articleQueryService.getArticles(pageable, null));
		
		// Should only be called once (no retry for non-transient exceptions)
		verify(articleRepository, times(1)).findAll(pageable);
	}
	
	@Test
	void testGetArticles_WithCategory_TransientException_RetriesAndSucceeds() {
		Pageable pageable = PageRequest.of(0, 20);
		Page<Article> page = new PageImpl<>(mockArticles, pageable, 1);
		
		when(articleRepository.findByCategory(anyString(), any(Pageable.class)))
			.thenThrow(new TransientDataAccessException("Connection timeout") {})
			.thenReturn(page);
		
		Page<Article> result = articleQueryService.getArticles(pageable, "technology");
		
		assertNotNull(result);
		assertEquals(1, result.getContent().size());
		
		// Verify retry happened
		verify(articleRepository, times(2)).findByCategory("technology", pageable);
	}
	
	@Test
	void testGetArticles_Success_NoRetryNeeded() {
		Pageable pageable = PageRequest.of(0, 20);
		Page<Article> page = new PageImpl<>(mockArticles, pageable, 1);
		
		when(articleRepository.findAll(any(Pageable.class))).thenReturn(page);
		
		Page<Article> result = articleQueryService.getArticles(pageable, null);
		
		assertNotNull(result);
		assertEquals(1, result.getContent().size());
		
		// Should only be called once (no retry needed for success)
		verify(articleRepository, times(1)).findAll(pageable);
	}
}

