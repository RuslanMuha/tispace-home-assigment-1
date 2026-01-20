package com.tispace.dataingestion.controller;

import com.tispace.common.dto.ArticleDTO;
import com.tispace.common.dto.SummaryDTO;
import com.tispace.common.validation.SortStringParser;
import com.tispace.dataingestion.client.QueryServiceClient;
import com.tispace.dataingestion.service.ArticleQueryService;
import io.github.resilience4j.ratelimiter.RequestNotPermitted;
import io.github.resilience4j.ratelimiter.annotation.RateLimiter;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/articles")
@Slf4j
@RequiredArgsConstructor
@Validated
@Tag(name = "Articles", description = "Public API for querying news articles and generating AI-powered summaries")
public class ArticleController {
	
	private final ArticleQueryService articleQueryService;
	private final QueryServiceClient queryServiceClient;
	private final SortStringParser sortStringParser;

	@GetMapping
	@RateLimiter(name = "articleController", fallbackMethod = "getArticlesRateLimitFallback")
	@Operation(
		summary = "Get paginated list of articles",
		description = "Retrieves a paginated list of articles with optional filtering by category. Results are sorted by published date in descending order by default. Rate limited to prevent abuse."
	)
	@ApiResponses(value = {
		@ApiResponse(
			responseCode = "200",
			description = "Successfully retrieved articles",
			content = @Content(schema = @Schema(implementation = Page.class))
		),
		@ApiResponse(
			responseCode = "400",
			description = "Invalid request parameters"
		),
		@ApiResponse(
			responseCode = "429",
			description = "Rate limit exceeded. Default limit: 100 requests per minute per endpoint. Retry after the rate limit period expires."
		),
		@ApiResponse(
			responseCode = "500",
			description = "Internal server error"
		)
	})
	public ResponseEntity<Page<ArticleDTO>> getArticles(
		@Parameter(
			description = "Page number (0-indexed). Default: 0",
			example = "0"
		)
		@RequestParam(required = false, defaultValue = "0")
		@Min(value = 0, message = "Page number must be non-negative")
		Integer page,
		@Parameter(
			description = "Page size. Default: 20",
			example = "20"
		)
		@RequestParam(required = false, defaultValue = "20")
		@Min(value = 1, message = "Page size must be at least 1")
		@Max(value = 100, message = "Page size cannot exceed 100")
		Integer size,
		@Parameter(
			description = "Sort field and direction (format: 'field,direction'). Default: 'publishedAt,desc'. Example: 'publishedAt,desc' or 'title,asc'",
			example = "publishedAt,desc"
		)
		@RequestParam(required = false, defaultValue = "publishedAt,desc")
        @Size(max = 50)
		String sort,
		@Parameter(
			description = "Filter articles by category (optional)",
			example = "technology"
		)
		@RequestParam(required = false)
		@Size(max = 100, message = "Category cannot exceed 100 characters")
		String category) {
		
        Sort sortObj = sortStringParser.parse(sort);
        Pageable pageable = org.springframework.data.domain.PageRequest.of(page, size, sortObj);

        log.debug("Fetching articles: page={}, size={}, sort={}, category={}", page, size, sort, category);

        Page<ArticleDTO> articles = articleQueryService.getArticlesDTO(pageable, category);
        return ResponseEntity.ok(articles);
    }
	
	@GetMapping("/{id}")
	@RateLimiter(name = "articleController", fallbackMethod = "getArticleByIdRateLimitFallback")
	@Operation(
		summary = "Get article by ID",
		description = "Retrieves a single article by its unique identifier. " +
			"Rate limited to 100 requests per minute. " +
			"Supports correlation ID via X-Correlation-ID header."
	)
	@ApiResponses(value = {
		@ApiResponse(
			responseCode = "200",
			description = "Successfully retrieved article",
			content = @Content(schema = @Schema(implementation = ArticleDTO.class))
		),
		@ApiResponse(
			responseCode = "404",
			description = "Article not found"
		),
		@ApiResponse(
			responseCode = "400",
			description = "Invalid article ID"
		),
		@ApiResponse(
			responseCode = "429",
			description = "Rate limit exceeded"
		),
		@ApiResponse(
			responseCode = "500",
			description = "Internal server error"
		)
	})
	public ResponseEntity<ArticleDTO> getArticleById(
		@Parameter(
			description = "Unique identifier of the article",
			required = true,
			example = "1"
		)
		@PathVariable
		@jakarta.validation.constraints.NotNull(message = "Article ID is required")
		@jakarta.validation.constraints.Positive(message = "Article ID must be a positive number")
		Long id) {
		
		log.debug("Fetching article with id: {}", id);
		
		ArticleDTO article = articleQueryService.getArticleDTOById(id);
		return ResponseEntity.ok(article);
	}
	
	@GetMapping("/{id}/summary")
	@RateLimiter(name = "articleController", fallbackMethod = "getArticleSummaryRateLimitFallback")
	@Operation(
		summary = "Get AI-generated article summary",
		description = "Retrieves an AI-generated summary of an article using ChatGPT. " +
			"The summary is cached for 24 hours with TTL jitter to prevent cache stampede. " +
			"First request generates the summary, subsequent requests return the cached version. " +
			"Uses single-flight pattern to prevent concurrent generation for the same article. " +
			"Rate limited to 100 requests per minute. " +
			"Supports correlation ID via X-Correlation-ID header."
	)
	@ApiResponses(value = {
		@ApiResponse(
			responseCode = "200",
			description = "Successfully retrieved article summary",
			content = @Content(schema = @Schema(implementation = SummaryDTO.class))
		),
		@ApiResponse(
			responseCode = "404",
			description = "Article not found"
		),
		@ApiResponse(
			responseCode = "400",
			description = "Invalid article ID"
		),
		@ApiResponse(
			responseCode = "429",
			description = "Rate limit exceeded"
		),
		@ApiResponse(
			responseCode = "500",
			description = "Internal server error or ChatGPT API error"
		)
	})
	public ResponseEntity<SummaryDTO> getArticleSummary(
		@Parameter(
			description = "Unique identifier of the article",
			required = true,
			example = "1"
		)
		@PathVariable
		@jakarta.validation.constraints.NotNull(message = "Article ID is required")
		@jakarta.validation.constraints.Positive(message = "Article ID must be a positive number")
		Long id) {
		
		log.debug("Fetching summary for article with id: {}", id);
		
		// Get article first
		ArticleDTO article = articleQueryService.getArticleDTOById(id);
		
		// Get summary from query-service
		SummaryDTO summary = queryServiceClient.getArticleSummary(id, article);
		return ResponseEntity.ok(summary);
	}
	
	/**
	 * Fallback method for rate limit exceeded on getArticles endpoint.
	 */
	@SuppressWarnings("unused")
    private ResponseEntity<Page<ArticleDTO>> getArticlesRateLimitFallback(
            Integer page, Integer size, String sort, String category, RequestNotPermitted e) {
        log.warn("Rate limit exceeded for getArticles. page={}, size={}", page, size);
        return ResponseEntity.status(429).build();
    }


	/**
	 * Fallback method for rate limit exceeded on getArticleById endpoint.
	 */
	@SuppressWarnings("unused")
    private ResponseEntity<ArticleDTO> getArticleByIdRateLimitFallback(Long id, RequestNotPermitted e) {
        log.warn("Rate limit exceeded for getArticleById. id={}", id);
        return ResponseEntity.status(429).build();
    }

	/**
	 * Fallback method for rate limit exceeded on getArticleSummary endpoint.
	 */
	@SuppressWarnings("unused")
    private ResponseEntity<SummaryDTO> getArticleSummaryRateLimitFallback(Long id, RequestNotPermitted e) {
        log.warn("Rate limit exceeded for getArticleSummary. id={}", id);
        return ResponseEntity.status(429).build();
    }
}


