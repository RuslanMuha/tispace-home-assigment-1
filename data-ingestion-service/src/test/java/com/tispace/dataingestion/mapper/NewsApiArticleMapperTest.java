package com.tispace.dataingestion.mapper;

import com.tispace.common.entity.Article;
import com.tispace.dataingestion.adapter.NewsApiAdapter;
import com.tispace.dataingestion.constants.NewsApiConstants;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;


import static org.junit.jupiter.api.Assertions.*;

class NewsApiArticleMapperTest {
	
	private NewsApiArticleMapper mapper;
	
	@BeforeEach
	void setUp() {
		mapper = Mappers.getMapper(NewsApiArticleMapper.class);
	}
	
	@Test
	void testToArticle_Success_MapsCorrectly() {
		NewsApiAdapter.ArticleResponse articleResponse = new NewsApiAdapter.ArticleResponse();
		articleResponse.setTitle("Test Article");
		articleResponse.setDescription("Test Description");
		articleResponse.setAuthor("Test Author");
		articleResponse.setPublishedAt("2025-01-18T10:00:00Z");
		
		Article article = mapper.toArticle(articleResponse);
		
		assertNotNull(article);
		assertEquals("Test Article", article.getTitle());
		assertEquals("Test Description", article.getDescription());
		assertEquals("Test Author", article.getAuthor());
		assertNotNull(article.getPublishedAt());
		assertNull(article.getId()); // Should be ignored
		assertNull(article.getCategory()); // Should be ignored initially
	}
	
	@Test
	void testToArticle_NullResponse_ReturnsNull() {
		Article article = mapper.toArticle(null);
		assertNull(article);
	}
	
	@Test
	void testToArticle_NullFields_HandlesGracefully() {
		NewsApiAdapter.ArticleResponse articleResponse = new NewsApiAdapter.ArticleResponse();
		articleResponse.setTitle(null);
		articleResponse.setDescription(null);
		articleResponse.setAuthor(null);
		articleResponse.setPublishedAt(null);
		
		Article article = mapper.toArticle(articleResponse);
		
		assertNotNull(article);
		assertNull(article.getTitle());
		assertNull(article.getDescription());
		assertNull(article.getAuthor());
		assertNull(article.getPublishedAt());
	}
	
	@Test
	void testUpdateCategory_WithCategory_SetsCategory() {
		Article article = new Article();
		article.setTitle("Test Article");
		
		mapper.updateCategory(article, "technology");
		
		assertEquals("technology", article.getCategory());
	}
	
	@Test
	void testUpdateCategory_WithEmptyCategory_UsesDefault() {
		Article article = new Article();
		article.setTitle("Test Article");
		
		mapper.updateCategory(article, "");
		
		assertEquals(NewsApiConstants.DEFAULT_CATEGORY, article.getCategory());
	}
	
	@Test
	void testUpdateCategory_WithNullCategory_UsesDefault() {
		Article article = new Article();
		article.setTitle("Test Article");
		
		mapper.updateCategory(article, null);
		
		assertEquals(NewsApiConstants.DEFAULT_CATEGORY, article.getCategory());
	}
	
	@Test
	void testUpdateCategory_WithWhitespaceCategory_UsesProvidedValue() {
		// Note: StringUtils.isNotEmpty("   ") returns true, so whitespace is treated as valid category
		// This is the actual behavior of the implementation
		Article article = new Article();
		article.setTitle("Test Article");
		
		mapper.updateCategory(article, "   ");
		
		// The implementation uses isNotEmpty, not isNotBlank, so whitespace is kept
		assertEquals("   ", article.getCategory());
	}
	
	@Test
	void testUpdateCategory_WithValidCategory_OverwritesExisting() {
		Article article = new Article();
		article.setTitle("Test Article");
		article.setCategory("old-category");
		
		mapper.updateCategory(article, "new-category");
		
		assertEquals("new-category", article.getCategory());
	}
	
	@Test
	void testToArticle_WithDifferentDateFormats_HandlesCorrectly() {
		NewsApiAdapter.ArticleResponse articleResponse = new NewsApiAdapter.ArticleResponse();
		articleResponse.setTitle("Test Article");
		articleResponse.setPublishedAt("2025-01-18T10:00:00+00:00");
		
		Article article = mapper.toArticle(articleResponse);
		
		assertNotNull(article);
		assertNotNull(article.getPublishedAt());
	}
	
	@Test
	void testToArticle_WithInvalidDate_ReturnsNullPublishedAt() {
		NewsApiAdapter.ArticleResponse articleResponse = new NewsApiAdapter.ArticleResponse();
		articleResponse.setTitle("Test Article");
		articleResponse.setPublishedAt("invalid-date");
		
		Article article = mapper.toArticle(articleResponse);
		
		assertNotNull(article);
		assertNull(article.getPublishedAt());
	}
	
	@Test
	void testToArticle_WithEmptyDate_ReturnsNullPublishedAt() {
		NewsApiAdapter.ArticleResponse articleResponse = new NewsApiAdapter.ArticleResponse();
		articleResponse.setTitle("Test Article");
		articleResponse.setPublishedAt("");
		
		Article article = mapper.toArticle(articleResponse);
		
		assertNotNull(article);
		assertNull(article.getPublishedAt());
	}
	
	@Test
	void testUpdateCategory_MultipleCalls_UpdatesCorrectly() {
		Article article = new Article();
		article.setTitle("Test Article");
		
		mapper.updateCategory(article, "category1");
		assertEquals("category1", article.getCategory());
		
		mapper.updateCategory(article, "category2");
		assertEquals("category2", article.getCategory());
		
		mapper.updateCategory(article, null);
		assertEquals(NewsApiConstants.DEFAULT_CATEGORY, article.getCategory());
	}
}

