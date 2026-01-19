package com.tispace.dataingestion.service;

import com.tispace.common.entity.Article;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DataIngestionServiceTest {
	
	@Mock
	private ExternalApiClient externalApiClient;
	
	@Mock
	private ArticlePersistenceService articlePersistenceService;
	
	@InjectMocks
	private DataIngestionService dataIngestionService;
	
	private List<Article> mockArticles;
	
	@BeforeEach
	void setUp() {
		// Set default values using reflection since @Value doesn't work in unit tests
		ReflectionTestUtils.setField(dataIngestionService, "defaultKeyword", "technology");
		ReflectionTestUtils.setField(dataIngestionService, "defaultCategory", "technology");
		
		mockArticles = new ArrayList<>();
		Article article = new Article();
		article.setTitle("Test Article");
		article.setDescription("Test Description");
		article.setAuthor("Test Author");
		article.setPublishedAt(LocalDateTime.now());
		article.setCategory("technology");
		mockArticles.add(article);
	}
	
	@Test
	void testIngestData_Success() {
		when(externalApiClient.fetchArticles(anyString(), anyString())).thenReturn(mockArticles);
		when(articlePersistenceService.saveArticles(anyList())).thenReturn(1);
		
		assertDoesNotThrow(() -> dataIngestionService.ingestData("technology", "technology"));
		
		verify(externalApiClient, times(1)).fetchArticles("technology", "technology");
		verify(articlePersistenceService, times(1)).saveArticles(anyList());
	}
	
	@Test
	void testIngestData_WithNullParams_UsesDefaults() {
		when(externalApiClient.fetchArticles(anyString(), anyString())).thenReturn(mockArticles);
		when(articlePersistenceService.saveArticles(anyList())).thenReturn(1);
		
		assertDoesNotThrow(() -> dataIngestionService.ingestData(null, null));
		
		// Should use default values when null is passed
		verify(externalApiClient, times(1)).fetchArticles("technology", "technology");
		verify(articlePersistenceService, times(1)).saveArticles(anyList());
	}
	
	@Test
	void testIngestData_EmptyArticles_DoesNotSave() {
		when(externalApiClient.fetchArticles(anyString(), anyString())).thenReturn(new ArrayList<>());
		
		assertDoesNotThrow(() -> dataIngestionService.ingestData("technology", "technology"));
		
		verify(externalApiClient, times(1)).fetchArticles("technology", "technology");
		verify(articlePersistenceService, never()).saveArticles(anyList());
	}
	
	@Test
	void testIngestData_WithEmptyKeyword_UsesDefault() {
		when(externalApiClient.fetchArticles(anyString(), anyString())).thenReturn(mockArticles);
		when(articlePersistenceService.saveArticles(anyList())).thenReturn(1);
		
		assertDoesNotThrow(() -> dataIngestionService.ingestData("", "technology"));
		
		verify(externalApiClient, times(1)).fetchArticles("technology", "technology");
		verify(articlePersistenceService, times(1)).saveArticles(anyList());
	}
	
	@Test
	void testIngestData_WithEmptyCategory_UsesDefault() {
		when(externalApiClient.fetchArticles(anyString(), anyString())).thenReturn(mockArticles);
		when(articlePersistenceService.saveArticles(anyList())).thenReturn(1);
		
		assertDoesNotThrow(() -> dataIngestionService.ingestData("technology", ""));
		
		verify(externalApiClient, times(1)).fetchArticles("technology", "technology");
		verify(articlePersistenceService, times(1)).saveArticles(anyList());
	}
	
	@Test
	void testIngestData_WithWhitespaceKeyword_UsesWhitespaceAsKeyword() {
		// Note: StringUtils.isNotEmpty("   ") returns true, so whitespace is used as-is
		when(externalApiClient.fetchArticles(anyString(), anyString())).thenReturn(mockArticles);
		when(articlePersistenceService.saveArticles(anyList())).thenReturn(1);
		
		assertDoesNotThrow(() -> dataIngestionService.ingestData("   ", "technology"));
		
		// The implementation uses isNotEmpty, not isNotBlank, so whitespace is kept
		verify(externalApiClient, times(1)).fetchArticles("   ", "technology");
		verify(articlePersistenceService, times(1)).saveArticles(anyList());
	}
	
	@Test
	void testIngestData_WithNullArticles_FiltersOutNulls() {
		List<Article> articlesWithNulls = new ArrayList<>();
		articlesWithNulls.add(mockArticles.get(0));
		articlesWithNulls.add(null);
		articlesWithNulls.add(createArticle("Valid Article"));
		
		when(externalApiClient.fetchArticles(anyString(), anyString())).thenReturn(articlesWithNulls);
		when(articlePersistenceService.saveArticles(anyList())).thenReturn(2);
		
		assertDoesNotThrow(() -> dataIngestionService.ingestData("technology", "technology"));
		
		verify(externalApiClient, times(1)).fetchArticles("technology", "technology");
		verify(articlePersistenceService, times(1)).saveArticles(argThat(list -> list.size() == 2));
	}
	
	@Test
	void testIngestData_WithArticlesWithoutTitle_FiltersOutInvalid() {
		List<Article> articlesWithInvalid = new ArrayList<>();
		articlesWithInvalid.add(mockArticles.get(0));
		
		Article articleWithoutTitle = new Article();
		articleWithoutTitle.setTitle(null);
		articleWithoutTitle.setDescription("Description");
		articlesWithInvalid.add(articleWithoutTitle);
		
		Article articleWithEmptyTitle = new Article();
		articleWithEmptyTitle.setTitle("");
		articleWithEmptyTitle.setDescription("Description");
		articlesWithInvalid.add(articleWithEmptyTitle);
		
		when(externalApiClient.fetchArticles(anyString(), anyString())).thenReturn(articlesWithInvalid);
		when(articlePersistenceService.saveArticles(anyList())).thenReturn(1);
		
		assertDoesNotThrow(() -> dataIngestionService.ingestData("technology", "technology"));
		
		verify(externalApiClient, times(1)).fetchArticles("technology", "technology");
		verify(articlePersistenceService, times(1)).saveArticles(argThat(list -> list.size() == 1));
	}
	
	@Test
	void testIngestData_AllArticlesInvalid_DoesNotSave() {
		List<Article> invalidArticles = new ArrayList<>();
		Article articleWithoutTitle = new Article();
		articleWithoutTitle.setTitle(null);
		invalidArticles.add(articleWithoutTitle);
		
		when(externalApiClient.fetchArticles(anyString(), anyString())).thenReturn(invalidArticles);
		
		assertDoesNotThrow(() -> dataIngestionService.ingestData("technology", "technology"));
		
		verify(externalApiClient, times(1)).fetchArticles("technology", "technology");
		verify(articlePersistenceService, never()).saveArticles(anyList());
	}
	
	@Test
	void testIngestData_NoParams_UsesDefaults() {
		when(externalApiClient.fetchArticles(anyString(), anyString())).thenReturn(mockArticles);
		when(articlePersistenceService.saveArticles(anyList())).thenReturn(1);
		
		assertDoesNotThrow(() -> dataIngestionService.ingestData());
		
		verify(externalApiClient, times(1)).fetchArticles("technology", "technology");
		verify(articlePersistenceService, times(1)).saveArticles(anyList());
	}
	
	@Test
	void testIngestData_ExternalApiThrowsException_PropagatesException() {
		when(externalApiClient.fetchArticles(anyString(), anyString()))
			.thenThrow(new RuntimeException("API error"));
		
		assertThrows(RuntimeException.class, () -> dataIngestionService.ingestData("technology", "technology"));
		
		verify(externalApiClient, times(1)).fetchArticles("technology", "technology");
		verify(articlePersistenceService, never()).saveArticles(anyList());
	}
	
	@Test
	void testIngestData_PersistenceServiceThrowsException_PropagatesException() {
		when(externalApiClient.fetchArticles(anyString(), anyString())).thenReturn(mockArticles);
		when(articlePersistenceService.saveArticles(anyList()))
			.thenThrow(new RuntimeException("Database error"));
		
		assertThrows(RuntimeException.class, () -> dataIngestionService.ingestData("technology", "technology"));
		
		verify(externalApiClient, times(1)).fetchArticles("technology", "technology");
		verify(articlePersistenceService, times(1)).saveArticles(anyList());
	}
	
	private Article createArticle(String title) {
		Article article = new Article();
		article.setTitle(title);
		article.setDescription("Description");
		article.setAuthor("Author");
		article.setPublishedAt(LocalDateTime.now());
		article.setCategory("technology");
		return article;
	}
}

