package com.tispace.queryservice.controller;

import com.tispace.common.dto.ArticleDTO;
import com.tispace.common.dto.SummaryDTO;
import com.tispace.common.exception.BusinessException;
import com.tispace.queryservice.service.ArticleSummaryService;
import io.swagger.v3.oas.annotations.Hidden;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/internal/summary")
@RequiredArgsConstructor
@Slf4j
@Validated
@Hidden // Internal API - not exposed in OpenAPI documentation
public class SummaryController {
	
	private final ArticleSummaryService articleSummaryService;
	
	@PostMapping("/{articleId}")
	public ResponseEntity<SummaryDTO> generateOrGetSummary(
		@PathVariable
		@NotNull(message = "Article ID is required")
		@Positive(message = "Article ID must be a positive number")
		Long articleId,
		@RequestBody
		@Valid
		@NotNull(message = "Article body is required")
		ArticleDTO article) {

		log.debug("Fetching summary for article with id: {}", articleId);
		var summary = articleSummaryService.getSummary(articleId, article);
		return ResponseEntity.ok(summary);
	}
}

