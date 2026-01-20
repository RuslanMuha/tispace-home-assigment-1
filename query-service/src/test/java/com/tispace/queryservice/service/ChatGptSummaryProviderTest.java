package com.tispace.queryservice.service;

import com.tispace.common.dto.ArticleDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ChatGptSummaryProviderTest {
	
	@Mock
	private ChatGptService chatGptService;
	
	private ChatGptSummaryProvider provider;
	
	@BeforeEach
	void setUp() {
		provider = new ChatGptSummaryProvider(chatGptService);
	}
	
	@Test
	void testGenerateSummary_Success_ShouldReturnSummary() throws Exception {
		ArticleDTO article = new ArticleDTO();
		article.setId(1L);
		article.setTitle("Test Article");
		
		String expectedSummary = "This is a test summary";
		when(chatGptService.generateSummary(article)).thenReturn(expectedSummary);
		
		String result = provider.generateSummary(article);
		
		assertEquals(expectedSummary, result);
		verify(chatGptService, times(1)).generateSummary(article);
	}
	
	@Test
	void testGetProviderName_ShouldReturnChatGPT() {
		assertEquals("ChatGPT", provider.getProviderName());
	}
	
	@Test
	void testIsAvailable_ShouldReturnTrue() {
		assertTrue(provider.isAvailable());
	}
	
	@Test
	void testGenerateSummary_Exception_ShouldPropagate() throws Exception {
		ArticleDTO article = new ArticleDTO();
		when(chatGptService.generateSummary(article)).thenThrow(new RuntimeException("API error"));
		
		assertThrows(RuntimeException.class, () -> provider.generateSummary(article));
	}
}


