package com.tispace.dataingestion.controller;

import com.tispace.common.contract.ArticleDTO;
import com.tispace.common.contract.ErrorResponseDTO;
import com.tispace.common.contract.SummaryDTO;
import com.tispace.common.util.LogRateLimiter;
import com.tispace.dataingestion.application.validation.SortStringParser;
import com.tispace.dataingestion.client.QueryServiceClient;
import com.tispace.dataingestion.controller.docs.ArticleApiDoc;
import com.tispace.dataingestion.service.ArticleQueryService;
import io.github.resilience4j.ratelimiter.RequestNotPermitted;
import io.github.resilience4j.ratelimiter.annotation.RateLimiter;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.UUID;

@RestController
@RequestMapping("/api/articles")
@Slf4j
@RequiredArgsConstructor
@Validated
public class ArticleController implements ArticleApiDoc {
	
	private static final LogRateLimiter LOG_LIMITER = LogRateLimiter.getInstance();
	private static final Duration RATELIMIT_LOG_WINDOW = Duration.ofSeconds(30);
	
	private final ArticleQueryService articleQueryService;
	private final QueryServiceClient queryServiceClient;
	private final SortStringParser sortStringParser;

	@GetMapping
	@RateLimiter(name = "articleController", fallbackMethod = "getArticlesRateLimitFallback")
	@Override
	public ResponseEntity<Page<ArticleDTO>> getArticles(
		@RequestParam(required = false, defaultValue = "0")
		@Min(value = 0, message = "Page number must be non-negative")
		Integer page,
		@RequestParam(required = false, defaultValue = "20")
		@Min(value = 1, message = "Page size must be at least 1")
		@Max(value = 100, message = "Page size cannot exceed 100")
		Integer size,
		@RequestParam(required = false, defaultValue = "publishedAt,desc")
        @Size(max = 50)
		String sort,
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
	@Override
	public ResponseEntity<ArticleDTO> getArticleById(
		@PathVariable
		@jakarta.validation.constraints.NotNull(message = "Article ID is required")
		UUID id) {
		
		log.debug("Fetching article with id: {}", id);
		
		ArticleDTO article = articleQueryService.getArticleDTOById(id);
		return ResponseEntity.ok(article);
	}
	
	@GetMapping("/{id}/summary")
	@RateLimiter(name = "articleController", fallbackMethod = "getArticleSummaryRateLimitFallback")
	@Override
	public ResponseEntity<SummaryDTO> getArticleSummary(
		@PathVariable
		@jakarta.validation.constraints.NotNull(message = "Article ID is required")
		UUID id) {
		
		log.debug("Fetching summary for article with id: {}", id);
		
		ArticleDTO article = articleQueryService.getArticleDTOById(id);
		SummaryDTO summary = queryServiceClient.getArticleSummary(id, article);
		return ResponseEntity.ok(summary);
	}
	
	@SuppressWarnings("unused")
    private ResponseEntity<ErrorResponseDTO> getArticlesRateLimitFallback(
            Integer page, Integer size, String sort, String category, RequestNotPermitted e) {
        if (LOG_LIMITER.shouldLog("ratelimit:articles_list", RATELIMIT_LOG_WINDOW)) {
            log.warn("Rate limit exceeded for getArticles. page={}, size={}", page, size);
        }
        return buildRateLimitResponse("/api/articles");
    }


	@SuppressWarnings("unused")
    private ResponseEntity<ErrorResponseDTO> getArticleByIdRateLimitFallback(UUID id, RequestNotPermitted e) {
        if (LOG_LIMITER.shouldLog("ratelimit:articles_get", RATELIMIT_LOG_WINDOW)) {
            log.warn("Rate limit exceeded for getArticleById. id={}", id);
        }
        return buildRateLimitResponse("/api/articles/" + id);
    }

	@SuppressWarnings("unused")
    private ResponseEntity<ErrorResponseDTO> getArticleSummaryRateLimitFallback(UUID id, RequestNotPermitted e) {
        if (LOG_LIMITER.shouldLog("ratelimit:articles_summary", RATELIMIT_LOG_WINDOW)) {
            log.warn("Rate limit exceeded for getArticleSummary. id={}", id);
        }
        return buildRateLimitResponse("/api/articles/" + id + "/summary");
    }

    private ResponseEntity<ErrorResponseDTO> buildRateLimitResponse(String path) {
        ErrorResponseDTO error = ErrorResponseDTO.builder()
                .errorCode("RATE_LIMIT_EXCEEDED")
                .message("Rate limit exceeded. Please try again later.")
                .timestamp(LocalDateTime.now())
                .path(path)
                .build();
        return ResponseEntity.status(429).body(error);
    }
}


