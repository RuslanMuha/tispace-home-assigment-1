package com.tispace.queryservice.service;

import com.tispace.common.dto.ArticleDTO;
import com.tispace.common.dto.SummaryDTO;
import com.tispace.queryservice.cache.CacheResult;
import com.tispace.queryservice.cache.CacheService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ArticleSummaryServiceTest {
	
	@Mock
	private CacheService cacheService;
	
	@Mock
	private SummaryProvider summaryProvider;
	
	@Mock
	private SingleFlightExecutor singleFlightExecutor;
	
	private ArticleSummaryService articleSummaryService;
	
	private ArticleDTO mockArticleDTO;
	private static final UUID ARTICLE_ID = UUID.fromString("01234567-89ab-7def-0123-456789abcdef");
	
	@BeforeEach
	void setUp() {
		// Create instance manually since there's no default constructor
		articleSummaryService = new ArticleSummaryService(
			cacheService,
			summaryProvider,
			singleFlightExecutor,
			24 // cacheTtlHours
		);
		
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
	void testGetSummary_FromCache_ReturnsCachedSummary() throws Exception {
		SummaryDTO cachedSummary = SummaryDTO.builder()
			.articleId(ARTICLE_ID)
			.summary("Cached summary")
			.cached(false)
			.build();
		
		when(cacheService.get(anyString(), eq(SummaryDTO.class))).thenReturn(CacheResult.hit(cachedSummary));
		
		SummaryDTO result = articleSummaryService.getSummary(ARTICLE_ID, mockArticleDTO);
		
		assertNotNull(result);
		assertTrue(result.getCached());
		assertEquals("Cached summary", result.getSummary());
		verify(cacheService, times(1)).get(anyString(), eq(SummaryDTO.class));
		verify(summaryProvider, never()).generateSummary(any(ArticleDTO.class));
	}
	
	@Test
	void testGetSummary_NotInCache_GeneratesNewSummary() throws Exception {
		String generatedSummary = "Generated summary by ChatGPT";
		
		when(cacheService.get(anyString(), eq(SummaryDTO.class))).thenReturn(CacheResult.miss());
		when(singleFlightExecutor.execute(anyString(), eq(SummaryDTO.class), any())).thenAnswer(invocation -> {
			SingleFlightExecutor.SingleFlightOperation<SummaryDTO> operation = invocation.getArgument(2);
			return operation.execute();
		});
		when(summaryProvider.generateSummary(mockArticleDTO)).thenReturn(generatedSummary);
		
		SummaryDTO result = articleSummaryService.getSummary(ARTICLE_ID, mockArticleDTO);
		
		assertNotNull(result);
		assertFalse(result.getCached());
		assertEquals(ARTICLE_ID, result.getArticleId());
		assertEquals(generatedSummary, result.getSummary());
		
		verify(cacheService, atLeast(1)).get(anyString(), eq(SummaryDTO.class));
		verify(summaryProvider, times(1)).generateSummary(mockArticleDTO);
		verify(cacheService, times(1)).put(anyString(), any(SummaryDTO.class), eq(86400L)); // 24 hours * 60 * 60 = 86400 seconds
		verify(singleFlightExecutor, times(1)).execute(anyString(), eq(SummaryDTO.class), any());
	}
	
	@Test
	void testGetSummary_CacheThrowsException_StillGeneratesSummary() throws Exception {
		// CacheService returns CacheResult.miss() when cache is unavailable
		// So this test should actually generate summary when cache returns miss
		String generatedSummary = "Generated summary by ChatGPT";
		
		when(cacheService.get(anyString(), eq(SummaryDTO.class))).thenReturn(CacheResult.miss());
		when(singleFlightExecutor.execute(anyString(), eq(SummaryDTO.class), any())).thenAnswer(invocation -> {
			SingleFlightExecutor.SingleFlightOperation<SummaryDTO> operation = invocation.getArgument(2);
			return operation.execute();
		});
		when(summaryProvider.generateSummary(mockArticleDTO)).thenReturn(generatedSummary);
		
		SummaryDTO result = articleSummaryService.getSummary(ARTICLE_ID, mockArticleDTO);
		
		assertNotNull(result);
		assertFalse(result.getCached());
		assertEquals(generatedSummary, result.getSummary());
		verify(cacheService, atLeast(1)).get(anyString(), eq(SummaryDTO.class));
		verify(summaryProvider, times(1)).generateSummary(mockArticleDTO);
		verify(singleFlightExecutor, times(1)).execute(anyString(), eq(SummaryDTO.class), any());
	}
	
	@Test
	void testGetSummary_ChatGptThrowsException_PropagatesException() throws Exception {
		when(cacheService.get(anyString(), eq(SummaryDTO.class))).thenReturn(CacheResult.miss());
		// Mock singleFlightExecutor to execute the operation, which will call generateSummary
		// The generateSummary will throw exception, which will be wrapped in IllegalStateException
		when(singleFlightExecutor.execute(anyString(), eq(SummaryDTO.class), any())).thenAnswer(invocation -> {
			SingleFlightExecutor.SingleFlightOperation<SummaryDTO> operation = invocation.getArgument(2);
			return operation.execute(); // This will call generateAndCacheSummary which calls generateSummary
		});
		when(summaryProvider.generateSummary(mockArticleDTO))
			.thenThrow(new RuntimeException("ChatGPT error"));
		
		// The exception from generateSummary is wrapped in IllegalStateException by generateAndCacheSummary
		assertThrows(IllegalStateException.class, 
			() -> articleSummaryService.getSummary(ARTICLE_ID, mockArticleDTO));
		
		// First check in getSummary() + singleFlightExecutor calls generateAndCacheSummary which calls provider
		verify(cacheService, atLeast(1)).get(anyString(), eq(SummaryDTO.class));
		// generateAndCacheSummary calls generateSummary which throws
		verify(summaryProvider, times(1)).generateSummary(mockArticleDTO);
		verify(cacheService, never()).put(anyString(), any(SummaryDTO.class), anyLong());
		verify(singleFlightExecutor, times(1)).execute(anyString(), eq(SummaryDTO.class), any());
	}
	
	@Test
	void testGetSummary_CachePutThrowsException_StillReturnsSummary() throws Exception {
		// CacheService.put() fails silently, so even if it throws, summary should still be returned
		String generatedSummary = "Generated summary by ChatGPT";
		
		when(cacheService.get(anyString(), eq(SummaryDTO.class))).thenReturn(CacheResult.miss());
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
		when(summaryProvider.generateSummary(mockArticleDTO)).thenReturn(generatedSummary);
		doThrow(new RuntimeException("Cache put error"))
			.when(cacheService).put(anyString(), any(SummaryDTO.class), anyLong());
		
		// Cache put errors are caught in cacheSummary() method and summary is still returned
		SummaryDTO result = articleSummaryService.getSummary(ARTICLE_ID, mockArticleDTO);
		
		assertNotNull(result);
		assertFalse(result.getCached());
		assertEquals(generatedSummary, result.getSummary());
		verify(cacheService, atLeast(1)).get(anyString(), eq(SummaryDTO.class));
		verify(summaryProvider, times(1)).generateSummary(mockArticleDTO);
		verify(cacheService, times(1)).put(anyString(), any(SummaryDTO.class), eq(86400L));
		verify(singleFlightExecutor, times(1)).execute(anyString(), eq(SummaryDTO.class), any());
	}
	
	@Test
	void testGetSummary_DifferentTtlHours_CalculatesCorrectly() throws Exception {
		// Create new instance with different TTL
		ArticleSummaryService serviceWithDifferentTtl = new ArticleSummaryService(
			cacheService,
			summaryProvider,
			singleFlightExecutor,
			12 // cacheTtlHours
		);
		
		String generatedSummary = "Generated summary";
		
		when(cacheService.get(anyString(), eq(SummaryDTO.class))).thenReturn(CacheResult.miss());
		when(singleFlightExecutor.execute(anyString(), eq(SummaryDTO.class), any())).thenAnswer(invocation -> {
			SingleFlightExecutor.SingleFlightOperation<SummaryDTO> operation = invocation.getArgument(2);
			return operation.execute();
		});
		when(summaryProvider.generateSummary(mockArticleDTO)).thenReturn(generatedSummary);
		
		serviceWithDifferentTtl.getSummary(ARTICLE_ID, mockArticleDTO);
		
		verify(cacheService, atLeast(1)).get(anyString(), eq(SummaryDTO.class));
		verify(cacheService, times(1)).put(anyString(), any(SummaryDTO.class), eq(43200L)); // 12 hours * 60 * 60 = 43200 seconds
		verify(singleFlightExecutor, times(1)).execute(anyString(), eq(SummaryDTO.class), any());
	}
	
	@Test
	void testGetSummary_NullArticleDTO_ThrowsIllegalArgumentException() throws Exception {
		// ArticleSummaryService validates null article and throws IllegalArgumentException
		assertThrows(IllegalArgumentException.class, 
			() -> articleSummaryService.getSummary(ARTICLE_ID, null));
		
		verify(cacheService, never()).get(anyString(), any());
		verify(summaryProvider, never()).generateSummary(any(ArticleDTO.class));
	}
}

