package com.tispace.queryservice.controller;

import com.tispace.common.dto.ArticleDTO;
import com.tispace.common.dto.SummaryDTO;
import com.tispace.queryservice.service.ArticleSummaryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import java.time.LocalDateTime;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class SummaryControllerTest {
	
	private MockMvc mockMvc;
	
	@Mock
	private ArticleSummaryService articleSummaryService;
	
	@InjectMocks
	private SummaryController summaryController;
	
	private ArticleDTO mockArticleDTO;
	private ObjectMapper objectMapper;
	
	@BeforeEach
	void setUp() {
		mockMvc = MockMvcBuilders.standaloneSetup(summaryController)
			.setControllerAdvice(new com.tispace.common.exception.GlobalExceptionHandler())
			.build();
		
		objectMapper = new ObjectMapper();
		objectMapper.registerModule(new JavaTimeModule());
		
		mockArticleDTO = ArticleDTO.builder()
			.id(1L)
			.title("Test Article")
			.description("Test Description")
			.author("Test Author")
			.publishedAt(LocalDateTime.now())
			.category("technology")
			.build();
	}
	
	@Test
	void testGetArticleSummary_Cached() throws Exception {
		SummaryDTO cachedSummary = SummaryDTO.builder()
			.articleId(1L)
			.summary("Cached summary")
			.cached(true)
			.build();
		
		when(articleSummaryService.getSummary(eq(1L), any(ArticleDTO.class))).thenReturn(cachedSummary);
		
		mockMvc.perform(post("/internal/summary/1")
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(mockArticleDTO)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.summary").value("Cached summary"))
			.andExpect(jsonPath("$.cached").value(true));
	}
	
	@Test
	void testGetArticleSummary_NotCached_GeneratesSummary() throws Exception {
		SummaryDTO generatedSummary = SummaryDTO.builder()
			.articleId(1L)
			.summary("Generated summary")
			.cached(false)
			.build();
		
		when(articleSummaryService.getSummary(eq(1L), any(ArticleDTO.class))).thenReturn(generatedSummary);
		
		mockMvc.perform(post("/internal/summary/1")
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(mockArticleDTO)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.summary").value("Generated summary"))
			.andExpect(jsonPath("$.cached").value(false));
	}
	
	@Test
	void testGetArticleSummary_InvalidJson_ReturnsInternalServerError() throws Exception {
		// Invalid JSON causes deserialization error which is handled by GlobalExceptionHandler
		// Using malformed JSON structure - unclosed brace
		String invalidJson = "{\"id\":1,\"title\":\"Test\"";
		mockMvc.perform(post("/internal/summary/1")
				.contentType(MediaType.APPLICATION_JSON)
				.content(invalidJson))
			.andExpect(status().isInternalServerError());
	}
	
	@Test
	void testGetArticleSummary_MissingContentType_ReturnsInternalServerError() throws Exception {
		// Missing Content-Type causes deserialization error
		mockMvc.perform(post("/internal/summary/1")
				.content(objectMapper.writeValueAsString(mockArticleDTO)))
			.andExpect(status().isInternalServerError());
	}
	
	@Test
	void testGetArticleSummary_ServiceThrowsException_ReturnsError() throws Exception {
		when(articleSummaryService.getSummary(eq(1L), any(ArticleDTO.class)))
			.thenThrow(new RuntimeException("Service error"));
		
		mockMvc.perform(post("/internal/summary/1")
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(mockArticleDTO)))
			.andExpect(status().isInternalServerError());
	}
	
	@Test
	void testGetArticleSummary_DifferentArticleId_ReturnsBadRequest() throws Exception {
		// Controller validates that article ID in path matches ID in body
		ArticleDTO articleWithDifferentId = ArticleDTO.builder()
			.id(999L)
			.title("Test Article")
			.build();
		
		mockMvc.perform(post("/internal/summary/1")
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(articleWithDifferentId)))
			.andExpect(status().isBadRequest());
	}
	
	@Test
	void testGetArticleSummary_NullArticleDTO_ReturnsInternalServerError() throws Exception {
		// JSON "null" deserializes to null object, which causes NullPointerException 
		// when trying to access article.getId() in controller, resulting in 500
		mockMvc.perform(post("/internal/summary/1")
				.contentType(MediaType.APPLICATION_JSON)
				.content("null"))
			.andExpect(status().isInternalServerError());
	}
	
	@Test
	void testGetArticleSummary_EmptyBody_ReturnsBadRequest() throws Exception {
		// Empty body with @NotNull validation fails
		mockMvc.perform(post("/internal/summary/1")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{}"))
			.andExpect(status().isBadRequest());
	}
}

