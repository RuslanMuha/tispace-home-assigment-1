package com.tispace.dataingestion.adapter;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

class NewsApiAdapterTest {
	
	@Test
	void testGetPublishedAtLocalDateTime_ValidZonedDateTime_ReturnsLocalDateTime() {
		NewsApiAdapter.ArticleResponse response = new NewsApiAdapter.ArticleResponse();
		response.setPublishedAt("2025-01-18T10:00:00Z");
		
		LocalDateTime result = response.getPublishedAtLocalDateTime();
		
		assertNotNull(result);
		assertEquals(2025, result.getYear());
		assertEquals(1, result.getMonthValue());
		assertEquals(18, result.getDayOfMonth());
	}
	
	@Test
	void testGetPublishedAtLocalDateTime_ValidWithTimezone_ReturnsLocalDateTime() {
		NewsApiAdapter.ArticleResponse response = new NewsApiAdapter.ArticleResponse();
		response.setPublishedAt("2025-01-18T10:00:00+05:00");
		
		LocalDateTime result = response.getPublishedAtLocalDateTime();
		
		assertNotNull(result);
	}
	
	@Test
	void testGetPublishedAtLocalDateTime_Null_ReturnsNull() {
		NewsApiAdapter.ArticleResponse response = new NewsApiAdapter.ArticleResponse();
		response.setPublishedAt(null);
		
		LocalDateTime result = response.getPublishedAtLocalDateTime();
		
		assertNull(result);
	}
	
	@Test
	void testGetPublishedAtLocalDateTime_Empty_ReturnsNull() {
		NewsApiAdapter.ArticleResponse response = new NewsApiAdapter.ArticleResponse();
		response.setPublishedAt("");
		
		LocalDateTime result = response.getPublishedAtLocalDateTime();
		
		assertNull(result);
	}
	
	@Test
	void testGetPublishedAtLocalDateTime_InvalidFormat_ReturnsNull() {
		NewsApiAdapter.ArticleResponse response = new NewsApiAdapter.ArticleResponse();
		response.setPublishedAt("invalid-date-format");
		
		LocalDateTime result = response.getPublishedAtLocalDateTime();
		
		assertNull(result);
	}
	
	@Test
	void testGetPublishedAtLocalDateTime_InvalidDate_ReturnsNull() {
		NewsApiAdapter.ArticleResponse response = new NewsApiAdapter.ArticleResponse();
		response.setPublishedAt("2025-13-45T25:99:99Z");
		
		LocalDateTime result = response.getPublishedAtLocalDateTime();
		
		assertNull(result);
	}
	
	@Test
	void testGetPublishedAtLocalDateTime_Whitespace_ReturnsNull() {
		NewsApiAdapter.ArticleResponse response = new NewsApiAdapter.ArticleResponse();
		response.setPublishedAt("   ");
		
		LocalDateTime result = response.getPublishedAtLocalDateTime();
		
		assertNull(result);
	}
	
	@Test
	void testGetPublishedAtLocalDateTime_ISO8601Format_ReturnsLocalDateTime() {
		NewsApiAdapter.ArticleResponse response = new NewsApiAdapter.ArticleResponse();
		response.setPublishedAt("2025-01-18T10:00:00.000Z");
		
		LocalDateTime result = response.getPublishedAtLocalDateTime();
		
		assertNotNull(result);
	}
	
	@Test
	void testGetPublishedAtLocalDateTime_DifferentTimezones_ConvertsCorrectly() {
		NewsApiAdapter.ArticleResponse response1 = new NewsApiAdapter.ArticleResponse();
		response1.setPublishedAt("2025-01-18T10:00:00Z");
		
		NewsApiAdapter.ArticleResponse response2 = new NewsApiAdapter.ArticleResponse();
		response2.setPublishedAt("2025-01-18T10:00:00+00:00");
		
		LocalDateTime result1 = response1.getPublishedAtLocalDateTime();
		LocalDateTime result2 = response2.getPublishedAtLocalDateTime();
		
		assertNotNull(result1);
		assertNotNull(result2);
		// Both should represent the same moment in time
		assertEquals(result1, result2);
	}
	
	@Test
	void testArticleResponse_SettersAndGetters_WorkCorrectly() {
		NewsApiAdapter.ArticleResponse response = new NewsApiAdapter.ArticleResponse();
		
		response.setTitle("Test Title");
		response.setDescription("Test Description");
		response.setAuthor("Test Author");
		response.setPublishedAt("2025-01-18T10:00:00Z");
		
		assertEquals("Test Title", response.getTitle());
		assertEquals("Test Description", response.getDescription());
		assertEquals("Test Author", response.getAuthor());
		assertEquals("2025-01-18T10:00:00Z", response.getPublishedAt());
	}
	
	@Test
	void testNewsApiAdapter_SettersAndGetters_WorkCorrectly() {
		NewsApiAdapter adapter = new NewsApiAdapter();
		
		adapter.setStatus("ok");
		adapter.setArticles(java.util.Collections.emptyList());
		
		assertEquals("ok", adapter.getStatus());
		assertNotNull(adapter.getArticles());
		assertTrue(adapter.getArticles().isEmpty());
	}
}

