package com.tispace.dataingestion.controller;

import com.tispace.common.dto.ArticleDTO;
import com.tispace.common.exception.NotFoundException;
import com.tispace.dataingestion.service.ArticleQueryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableHandlerMethodArgumentResolver;
import org.springframework.data.web.SortHandlerMethodArgumentResolver;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class ArticleControllerTest {
	
	private MockMvc mockMvc;
	
	@Mock
	private ArticleQueryService articleQueryService;
	
	@InjectMocks
	private ArticleController articleController;
	
	private ArticleDTO mockArticleDTO;
	
	@BeforeEach
	void setUp() {
		PageableHandlerMethodArgumentResolver pageableResolver = new PageableHandlerMethodArgumentResolver();
		pageableResolver.setOneIndexedParameters(false);
		
		mockMvc = MockMvcBuilders.standaloneSetup(articleController)
			.setControllerAdvice(new com.tispace.common.exception.GlobalExceptionHandler())
			.setCustomArgumentResolvers(
				pageableResolver,
				new SortHandlerMethodArgumentResolver()
			)
			.build();
		
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
	void testGetArticles_Success() throws Exception {
		List<ArticleDTO> articles = new ArrayList<>();
		articles.add(mockArticleDTO);
		Page<ArticleDTO> page = new PageImpl<>(articles, PageRequest.of(0, 20), 1);
		
		when(articleQueryService.getArticlesDTO(any(Pageable.class), any())).thenReturn(page);
		
		mockMvc.perform(get("/api/articles")
				.param("page", "0")
				.param("size", "20")
				.contentType(MediaType.APPLICATION_JSON))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.content").isArray())
			.andExpect(jsonPath("$.content[0].title").value("Test Article"));
	}
	
	@Test
	void testGetArticles_WithCategory_FiltersByCategory() throws Exception {
		List<ArticleDTO> articles = new ArrayList<>();
		articles.add(mockArticleDTO);
		Page<ArticleDTO> page = new PageImpl<>(articles, PageRequest.of(0, 20), 1);
		
		when(articleQueryService.getArticlesDTO(any(Pageable.class), eq("technology"))).thenReturn(page);
		
		mockMvc.perform(get("/api/articles")
				.param("page", "0")
				.param("size", "20")
				.param("category", "technology")
				.contentType(MediaType.APPLICATION_JSON))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.content").isArray());
	}
	
	@Test
	void testGetArticleById_Success() throws Exception {
		when(articleQueryService.getArticleDTOById(1L)).thenReturn(mockArticleDTO);
		
		mockMvc.perform(get("/api/articles/1")
				.contentType(MediaType.APPLICATION_JSON))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.id").value(1))
			.andExpect(jsonPath("$.title").value("Test Article"));
	}
	
	@Test
	void testGetArticleById_NotFound() throws Exception {
		when(articleQueryService.getArticleDTOById(1L))
			.thenThrow(new NotFoundException("Article", 1L));
		
		mockMvc.perform(get("/api/articles/1")
				.contentType(MediaType.APPLICATION_JSON))
			.andExpect(status().isNotFound());
	}
	
	@Test
	void testGetArticles_EmptyPage_ReturnsEmptyPage() throws Exception {
		Page<ArticleDTO> emptyPage = new PageImpl<>(new ArrayList<>(), PageRequest.of(0, 20), 0);
		
		when(articleQueryService.getArticlesDTO(any(Pageable.class), any())).thenReturn(emptyPage);
		
		mockMvc.perform(get("/api/articles")
				.param("page", "0")
				.param("size", "20")
				.contentType(MediaType.APPLICATION_JSON))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.content").isArray())
			.andExpect(jsonPath("$.content").isEmpty());
	}
	
	@Test
	void testGetArticles_WithEmptyCategory_ReturnsAllArticles() throws Exception {
		List<ArticleDTO> articles = new ArrayList<>();
		articles.add(mockArticleDTO);
		Page<ArticleDTO> page = new PageImpl<>(articles, PageRequest.of(0, 20), 1);
		
		when(articleQueryService.getArticlesDTO(any(Pageable.class), eq(""))).thenReturn(page);
		
		mockMvc.perform(get("/api/articles")
				.param("page", "0")
				.param("size", "20")
				.param("category", "")
				.contentType(MediaType.APPLICATION_JSON))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.content").isArray());
	}
	
	@Test
	void testGetArticles_ServiceThrowsException_ReturnsError() throws Exception {
		when(articleQueryService.getArticlesDTO(any(Pageable.class), any()))
			.thenThrow(new RuntimeException("Service error"));
		
		mockMvc.perform(get("/api/articles")
				.param("page", "0")
				.param("size", "20")
				.contentType(MediaType.APPLICATION_JSON))
			.andExpect(status().isInternalServerError());
	}
	
	@Test
	void testGetArticleById_ServiceThrowsException_ReturnsError() throws Exception {
		when(articleQueryService.getArticleDTOById(1L))
			.thenThrow(new RuntimeException("Service error"));
		
		mockMvc.perform(get("/api/articles/1")
				.contentType(MediaType.APPLICATION_JSON))
			.andExpect(status().isInternalServerError());
	}
	
	@Test
	void testGetArticles_InvalidPageNumber_ReturnsInternalServerError() throws Exception {
		// Spring doesn't validate pageable parameters automatically
		// Invalid pageable parameters cause exceptions in the service layer
		// which are handled by GlobalExceptionHandler and return 500
		mockMvc.perform(get("/api/articles")
				.param("page", "-1")
				.param("size", "20")
				.contentType(MediaType.APPLICATION_JSON))
			.andExpect(status().isInternalServerError());
	}
	
	@Test
	void testGetArticles_InvalidSize_ReturnsInternalServerError() throws Exception {
		// Spring doesn't validate pageable parameters automatically
		// Invalid pageable parameters cause exceptions in the service layer
		// which are handled by GlobalExceptionHandler and return 500
		mockMvc.perform(get("/api/articles")
				.param("page", "0")
				.param("size", "0")
				.contentType(MediaType.APPLICATION_JSON))
			.andExpect(status().isInternalServerError());
	}
	
	@Test
	void testGetArticles_NoPaginationParams_UsesDefaults() throws Exception {
		List<ArticleDTO> articles = new ArrayList<>();
		articles.add(mockArticleDTO);
		Page<ArticleDTO> page = new PageImpl<>(articles, PageRequest.of(0, 20), 1);
		
		when(articleQueryService.getArticlesDTO(any(Pageable.class), any())).thenReturn(page);
		
		mockMvc.perform(get("/api/articles")
				.contentType(MediaType.APPLICATION_JSON))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.content").isArray());
	}
	
	@Test
	void testGetArticleById_InvalidId_HandlesGracefully() throws Exception {
		when(articleQueryService.getArticleDTOById(999L))
			.thenThrow(new NotFoundException("Article", 999L));
		
		mockMvc.perform(get("/api/articles/999")
				.contentType(MediaType.APPLICATION_JSON))
			.andExpect(status().isNotFound());
	}
}

