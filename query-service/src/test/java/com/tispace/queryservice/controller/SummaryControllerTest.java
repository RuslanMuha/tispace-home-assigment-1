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
	void testGetArticleSummary_InvalidJson_ReturnsBadRequest() throws Exception {
		mockMvc.perform(post("/internal/summary/1")
				.contentType(MediaType.APPLICATION_JSON)
				.content("invalid json"))
			.andExpect(status().isBadRequest());
	}
	
	@Test
	void testGetArticleSummary_MissingContentType_ReturnsBadRequest() throws Exception {
		mockMvc.perform(post("/internal/summary/1")
				.content(objectMapper.writeValueAsString(mockArticleDTO)))
			.andExpect(status().isBadRequest());
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
	void testGetArticleSummary_DifferentArticleId_UsesCorrectId() throws Exception {
		SummaryDTO summary = SummaryDTO.builder()
			.articleId(999L)
			.summary("Summary")
			.cached(true)
			.build();
		
		when(articleSummaryService.getSummary(eq(999L), any(ArticleDTO.class))).thenReturn(summary);
		
		mockMvc.perform(post("/internal/summary/999")
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(mockArticleDTO)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.articleId").value(999));
	}
	
	@Test
	void testGetArticleSummary_NullArticleDTO_HandlesGracefully() throws Exception {
		SummaryDTO summary = SummaryDTO.builder()
			.articleId(1L)
			.summary("Summary")
			.cached(true)
			.build();
		
		when(articleSummaryService.getSummary(eq(1L), any())).thenReturn(summary);
		
		mockMvc.perform(post("/internal/summary/1")
				.contentType(MediaType.APPLICATION_JSON)
				.content("null"))
			.andExpect(status().isOk());
	}
	
	@Test
	void testGetArticleSummary_EmptyBody_HandlesGracefully() throws Exception {
		SummaryDTO summary = SummaryDTO.builder()
			.articleId(1L)
			.summary("Summary")
			.cached(true)
			.build();
		
		when(articleSummaryService.getSummary(eq(1L), any())).thenReturn(summary);
		
		mockMvc.perform(post("/internal/summary/1")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{}"))
			.andExpect(status().isOk());
	}
}

