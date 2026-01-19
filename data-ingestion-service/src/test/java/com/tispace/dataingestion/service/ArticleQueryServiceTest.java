package com.tispace.dataingestion.service;

import com.tispace.common.dto.ArticleDTO;
import com.tispace.common.entity.Article;
import com.tispace.common.exception.NotFoundException;
import com.tispace.common.mapper.ArticleMapper;
import com.tispace.common.repository.ArticleRepository;
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

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ArticleQueryServiceTest {
	
	@Mock
	private ArticleRepository articleRepository;
	
	@Mock
	private ArticleMapper articleMapper;
	
	@InjectMocks
	private ArticleQueryService articleQueryService;
	
	private Article mockArticle;
	private ArticleDTO mockArticleDTO;
	private List<Article> mockArticles;
	
	@BeforeEach
	void setUp() {
		mockArticle = new Article();
		mockArticle.setId(1L);
		mockArticle.setTitle("Test Article");
		mockArticle.setDescription("Test Description");
		mockArticle.setAuthor("Test Author");
		mockArticle.setPublishedAt(LocalDateTime.now());
		mockArticle.setCategory("technology");
		
		mockArticleDTO = ArticleDTO.builder()
			.id(1L)
			.title("Test Article")
			.description("Test Description")
			.author("Test Author")
			.publishedAt(LocalDateTime.now())
			.category("technology")
			.build();
		
		mockArticles = new ArrayList<>();
		mockArticles.add(mockArticle);
	}
	
	@Test
	void testGetArticles_WithCategory_ReturnsFilteredArticles() {
		Pageable pageable = PageRequest.of(0, 20);
		Page<Article> page = new PageImpl<>(mockArticles, pageable, 1);
		
		when(articleRepository.findByCategory(anyString(), any(Pageable.class))).thenReturn(page);
		
		Page<Article> result = articleQueryService.getArticles(pageable, "technology");
		
		assertNotNull(result);
		assertEquals(1, result.getContent().size());
		verify(articleRepository, times(1)).findByCategory("technology", pageable);
		verify(articleRepository, never()).findAll(any(Pageable.class));
	}
	
	@Test
	void testGetArticles_WithoutCategory_ReturnsAllArticles() {
		Pageable pageable = PageRequest.of(0, 20);
		Page<Article> page = new PageImpl<>(mockArticles, pageable, 1);
		
		when(articleRepository.findAll(any(Pageable.class))).thenReturn(page);
		
		Page<Article> result = articleQueryService.getArticles(pageable, null);
		
		assertNotNull(result);
		assertEquals(1, result.getContent().size());
		verify(articleRepository, times(1)).findAll(pageable);
		verify(articleRepository, never()).findByCategory(anyString(), any(Pageable.class));
	}
	
	@Test
	void testGetArticlesDTO_WithCategory_ReturnsFilteredDTOs() {
		Pageable pageable = PageRequest.of(0, 20);
		Page<Article> articlePage = new PageImpl<>(mockArticles, pageable, 1);
		
		when(articleRepository.findByCategory(anyString(), any(Pageable.class))).thenReturn(articlePage);
		when(articleMapper.toDTO(any(Article.class))).thenReturn(mockArticleDTO);
		
		Page<ArticleDTO> result = articleQueryService.getArticlesDTO(pageable, "technology");
		
		assertNotNull(result);
		assertEquals(1, result.getContent().size());
		verify(articleMapper, times(1)).toDTO(any(Article.class));
	}
	
	@Test
	void testGetArticleById_Exists_ReturnsArticle() {
		when(articleRepository.findById(1L)).thenReturn(Optional.of(mockArticle));
		
		Article result = articleQueryService.getArticleById(1L);
		
		assertNotNull(result);
		assertEquals(1L, result.getId());
		assertEquals("Test Article", result.getTitle());
		verify(articleRepository, times(1)).findById(1L);
	}
	
	@Test
	void testGetArticleById_NotExists_ThrowsException() {
		when(articleRepository.findById(1L)).thenReturn(Optional.empty());
		
		assertThrows(NotFoundException.class, () -> articleQueryService.getArticleById(1L));
		verify(articleRepository, times(1)).findById(1L);
	}
	
	@Test
	void testGetArticleDTOById_Exists_ReturnsDTO() {
		when(articleRepository.findById(1L)).thenReturn(Optional.of(mockArticle));
		when(articleMapper.toDTO(any(Article.class))).thenReturn(mockArticleDTO);
		
		ArticleDTO result = articleQueryService.getArticleDTOById(1L);
		
		assertNotNull(result);
		assertEquals(1L, result.getId());
		assertEquals("Test Article", result.getTitle());
		verify(articleMapper, times(1)).toDTO(any(Article.class));
	}
	
	@Test
	void testGetArticleDTOById_NotExists_ThrowsException() {
		when(articleRepository.findById(1L)).thenReturn(Optional.empty());
		
		assertThrows(NotFoundException.class, () -> articleQueryService.getArticleDTOById(1L));
		verify(articleRepository, times(1)).findById(1L);
		verify(articleMapper, never()).toDTO(any(Article.class));
	}
	
	@Test
	void testGetArticles_WithEmptyCategory_ReturnsAllArticles() {
		Pageable pageable = PageRequest.of(0, 20);
		Page<Article> page = new PageImpl<>(mockArticles, pageable, 1);
		
		when(articleRepository.findAll(any(Pageable.class))).thenReturn(page);
		
		Page<Article> result = articleQueryService.getArticles(pageable, "");
		
		assertNotNull(result);
		assertEquals(1, result.getContent().size());
		verify(articleRepository, times(1)).findAll(pageable);
		verify(articleRepository, never()).findByCategory(anyString(), any(Pageable.class));
	}
	
	@Test
	void testGetArticles_WithWhitespaceCategory_FiltersByWhitespaceCategory() {
		// Note: StringUtils.isNotEmpty("   ") returns true, so whitespace is treated as valid category
		Pageable pageable = PageRequest.of(0, 20);
		Page<Article> page = new PageImpl<>(mockArticles, pageable, 1);
		
		when(articleRepository.findByCategory(anyString(), any(Pageable.class))).thenReturn(page);
		
		Page<Article> result = articleQueryService.getArticles(pageable, "   ");
		
		assertNotNull(result);
		assertEquals(1, result.getContent().size());
		verify(articleRepository, times(1)).findByCategory("   ", pageable);
		verify(articleRepository, never()).findAll(any(Pageable.class));
	}
	
	@Test
	void testGetArticlesDTO_WithoutCategory_ReturnsAllDTOs() {
		Pageable pageable = PageRequest.of(0, 20);
		Page<Article> articlePage = new PageImpl<>(mockArticles, pageable, 1);
		
		when(articleRepository.findAll(any(Pageable.class))).thenReturn(articlePage);
		when(articleMapper.toDTO(any(Article.class))).thenReturn(mockArticleDTO);
		
		Page<ArticleDTO> result = articleQueryService.getArticlesDTO(pageable, null);
		
		assertNotNull(result);
		assertEquals(1, result.getContent().size());
		verify(articleMapper, times(1)).toDTO(any(Article.class));
		verify(articleRepository, times(1)).findAll(pageable);
	}
	
	@Test
	void testGetArticlesDTO_WithEmptyCategory_ReturnsAllDTOs() {
		Pageable pageable = PageRequest.of(0, 20);
		Page<Article> articlePage = new PageImpl<>(mockArticles, pageable, 1);
		
		when(articleRepository.findAll(any(Pageable.class))).thenReturn(articlePage);
		when(articleMapper.toDTO(any(Article.class))).thenReturn(mockArticleDTO);
		
		Page<ArticleDTO> result = articleQueryService.getArticlesDTO(pageable, "");
		
		assertNotNull(result);
		assertEquals(1, result.getContent().size());
		verify(articleMapper, times(1)).toDTO(any(Article.class));
		verify(articleRepository, times(1)).findAll(pageable);
	}
	
	@Test
	void testGetArticles_EmptyPage_ReturnsEmptyPage() {
		Pageable pageable = PageRequest.of(0, 20);
		Page<Article> emptyPage = new PageImpl<>(new ArrayList<>(), pageable, 0);
		
		when(articleRepository.findAll(any(Pageable.class))).thenReturn(emptyPage);
		
		Page<Article> result = articleQueryService.getArticles(pageable, null);
		
		assertNotNull(result);
		assertTrue(result.isEmpty());
		assertEquals(0, result.getContent().size());
	}
	
	@Test
	void testGetArticleById_NullId_ThrowsNotFoundException() {
		// Repository.findById(null) returns Optional.empty(), which triggers NotFoundException
		when(articleRepository.findById(null)).thenReturn(java.util.Optional.empty());
		
		assertThrows(NotFoundException.class, () -> articleQueryService.getArticleById(null));
		verify(articleRepository, times(1)).findById(null);
	}
}


