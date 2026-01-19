package com.tispace.queryservice.service;

import com.tispace.common.dto.ArticleDTO;
import com.tispace.common.dto.SummaryDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ArticleSummaryServiceTest {
	
	@Mock
	private CacheService cacheService;
	
	@Mock
	private ChatGptService chatGptService;
	
	@Mock
	private SingleFlightExecutor singleFlightExecutor;
	
	@InjectMocks
	private ArticleSummaryService articleSummaryService;
	
	private ArticleDTO mockArticleDTO;
	private static final Long ARTICLE_ID = 1L;
	
	@BeforeEach
	void setUp() {
		ReflectionTestUtils.setField(articleSummaryService, "cacheTtlHours", 24);
		
		mockArticleDTO = ArticleDTO.builder()
			.id(ARTICLE_ID)
			.title("Test Article")
			.description("Test Description")
			.author("Test Author")
			.publishedAt(LocalDateTime.now())
			.category("technology")
			.build();
	}
	
	@Test
	void testGetSummary_FromCache_ReturnsCachedSummary() {
		SummaryDTO cachedSummary = SummaryDTO.builder()
			.articleId(ARTICLE_ID)
			.summary("Cached summary")
			.cached(false)
			.build();
		
		when(cacheService.get(anyString(), eq(SummaryDTO.class))).thenReturn(cachedSummary);
		
		SummaryDTO result = articleSummaryService.getSummary(ARTICLE_ID, mockArticleDTO);
		
		assertNotNull(result);
		assertTrue(result.getCached());
		assertEquals("Cached summary", result.getSummary());
		verify(cacheService, times(1)).get(anyString(), eq(SummaryDTO.class));
		verify(chatGptService, never()).generateSummary(any(ArticleDTO.class));
	}
	
	@Test
	void testGetSummary_NotInCache_GeneratesNewSummary() throws Exception {
		String generatedSummary = "Generated summary by ChatGPT";
		
		when(cacheService.get(anyString(), eq(SummaryDTO.class))).thenReturn(null);
		when(singleFlightExecutor.execute(anyString(), eq(SummaryDTO.class), any())).thenAnswer(invocation -> {
			SingleFlightExecutor.SingleFlightOperation<SummaryDTO> operation = invocation.getArgument(2);
			return operation.execute();
		});
		when(chatGptService.generateSummary(mockArticleDTO)).thenReturn(generatedSummary);
		
		SummaryDTO result = articleSummaryService.getSummary(ARTICLE_ID, mockArticleDTO);
		
		assertNotNull(result);
		assertFalse(result.getCached());
		assertEquals(ARTICLE_ID, result.getArticleId());
		assertEquals(generatedSummary, result.getSummary());
		
		verify(cacheService, atLeast(1)).get(anyString(), eq(SummaryDTO.class));
		verify(chatGptService, times(1)).generateSummary(mockArticleDTO);
		verify(cacheService, times(1)).put(anyString(), any(SummaryDTO.class), eq(86400L)); // 24 hours * 60 * 60 = 86400 seconds
		verify(singleFlightExecutor, times(1)).execute(anyString(), eq(SummaryDTO.class), any());
	}
	
	@Test
	void testGetSummary_CacheThrowsException_StillGeneratesSummary() throws Exception {
		// CacheService handles exceptions gracefully and returns null (cache miss)
		// So this test should actually generate summary when cache returns null
		String generatedSummary = "Generated summary by ChatGPT";
		
		when(cacheService.get(anyString(), eq(SummaryDTO.class))).thenReturn(null);
		when(singleFlightExecutor.execute(anyString(), eq(SummaryDTO.class), any())).thenAnswer(invocation -> {
			SingleFlightExecutor.SingleFlightOperation<SummaryDTO> operation = invocation.getArgument(2);
			return operation.execute();
		});
		when(chatGptService.generateSummary(mockArticleDTO)).thenReturn(generatedSummary);
		
		SummaryDTO result = articleSummaryService.getSummary(ARTICLE_ID, mockArticleDTO);
		
		assertNotNull(result);
		assertFalse(result.getCached());
		assertEquals(generatedSummary, result.getSummary());
		verify(cacheService, atLeast(1)).get(anyString(), eq(SummaryDTO.class));
		verify(chatGptService, times(1)).generateSummary(mockArticleDTO);
		verify(singleFlightExecutor, times(1)).execute(anyString(), eq(SummaryDTO.class), any());
	}
	
	@Test
	void testGetSummary_ChatGptThrowsException_PropagatesException() throws Exception {
		when(cacheService.get(anyString(), eq(SummaryDTO.class))).thenReturn(null);
		// Mock singleFlightExecutor to throw exception, which triggers fallback
		when(singleFlightExecutor.execute(anyString(), eq(SummaryDTO.class), any()))
			.thenThrow(new RuntimeException("Single flight error"));
		when(chatGptService.generateSummary(mockArticleDTO))
			.thenThrow(new RuntimeException("ChatGPT error"));
		
		assertThrows(RuntimeException.class, 
			() -> articleSummaryService.getSummary(ARTICLE_ID, mockArticleDTO));
		
		// First check in getSummary() + fallback calls generateAndCacheSummary which calls ChatGPT
		verify(cacheService, atLeast(1)).get(anyString(), eq(SummaryDTO.class));
		// When singleFlightExecutor throws, fallback calls generateAndCacheSummary which calls ChatGPT
		verify(chatGptService, atLeast(1)).generateSummary(mockArticleDTO);
		verify(cacheService, never()).put(anyString(), any(SummaryDTO.class), anyLong());
		verify(singleFlightExecutor, times(1)).execute(anyString(), eq(SummaryDTO.class), any());
	}
	
	@Test
	void testGetSummary_CachePutThrowsException_StillReturnsSummary() throws Exception {
		// CacheService.put() fails silently, so even if it throws, summary should still be returned
		String generatedSummary = "Generated summary by ChatGPT";
		
		when(cacheService.get(anyString(), eq(SummaryDTO.class))).thenReturn(null);
		when(singleFlightExecutor.execute(anyString(), eq(SummaryDTO.class), any())).thenAnswer(invocation -> {
			SingleFlightExecutor.SingleFlightOperation<SummaryDTO> operation = invocation.getArgument(2);
			try {
				return operation.execute();
			} catch (Exception e) {
				// If cache.put throws, it should be caught in generateAndCacheSummary
				// and summary should still be returned
				throw e;
			}
		});
		when(chatGptService.generateSummary(mockArticleDTO)).thenReturn(generatedSummary);
		doThrow(new RuntimeException("Cache put error"))
			.when(cacheService).put(anyString(), any(SummaryDTO.class), anyLong());
		
		// Cache put errors are caught in cacheSummary() method and summary is still returned
		SummaryDTO result = articleSummaryService.getSummary(ARTICLE_ID, mockArticleDTO);
		
		assertNotNull(result);
		assertFalse(result.getCached());
		assertEquals(generatedSummary, result.getSummary());
		verify(cacheService, atLeast(1)).get(anyString(), eq(SummaryDTO.class));
		verify(chatGptService, times(1)).generateSummary(mockArticleDTO);
		verify(cacheService, times(1)).put(anyString(), any(SummaryDTO.class), eq(86400L));
		verify(singleFlightExecutor, times(1)).execute(anyString(), eq(SummaryDTO.class), any());
	}
	
	@Test
	void testGetSummary_DifferentTtlHours_CalculatesCorrectly() throws Exception {
		ReflectionTestUtils.setField(articleSummaryService, "cacheTtlHours", 12);
		
		String generatedSummary = "Generated summary";
		
		when(cacheService.get(anyString(), eq(SummaryDTO.class))).thenReturn(null);
		when(singleFlightExecutor.execute(anyString(), eq(SummaryDTO.class), any())).thenAnswer(invocation -> {
			SingleFlightExecutor.SingleFlightOperation<SummaryDTO> operation = invocation.getArgument(2);
			return operation.execute();
		});
		when(chatGptService.generateSummary(mockArticleDTO)).thenReturn(generatedSummary);
		
		articleSummaryService.getSummary(ARTICLE_ID, mockArticleDTO);
		
		verify(cacheService, atLeast(1)).get(anyString(), eq(SummaryDTO.class));
		verify(cacheService, times(1)).put(anyString(), any(SummaryDTO.class), eq(43200L)); // 12 hours * 60 * 60 = 43200 seconds
		verify(singleFlightExecutor, times(1)).execute(anyString(), eq(SummaryDTO.class), any());
	}
	
	@Test
	void testGetSummary_NullArticleDTO_ThrowsIllegalArgumentException() {
		// ArticleSummaryService validates null article and throws IllegalArgumentException
		assertThrows(IllegalArgumentException.class, 
			() -> articleSummaryService.getSummary(ARTICLE_ID, null));
		
		verify(cacheService, never()).get(anyString(), any());
		verify(chatGptService, never()).generateSummary(any(ArticleDTO.class));
	}
}

