package com.tispace.queryservice.service;

import com.theokanning.openai.completion.chat.ChatCompletionRequest;
import com.theokanning.openai.completion.chat.ChatCompletionResult;
import com.theokanning.openai.completion.chat.ChatMessage;
import com.theokanning.openai.completion.chat.ChatMessageRole;
import com.theokanning.openai.service.OpenAiService;
import com.tispace.common.dto.ArticleDTO;
import com.tispace.common.exception.ExternalApiException;
import com.tispace.common.validation.ArticleValidator;
import com.tispace.queryservice.constants.ChatGptConstants;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import org.apache.commons.lang3.StringUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
@Slf4j
public class ChatGptService {
	
	private final OpenAiService openAiService;
    private final ArticleValidator articleValidator;
	
	@Value("${openai.model:gpt-3.5-turbo}")
	private String model;
	
	public ChatGptService(ObjectProvider<OpenAiService> openAiServiceProvider, ArticleValidator articleValidator) {
		this.openAiService = openAiServiceProvider.getIfAvailable(); // Can be null if OpenAI API key is not configured
        this.articleValidator = articleValidator;
    }
	
	@CircuitBreaker(name = "openAiApi", fallbackMethod = "generateSummaryFallback")
	@Retry(name = "openAiApi")
	public String generateSummary(ArticleDTO article) {
        articleValidator.validateInput(article);
		
		if (openAiService == null) {
			log.warn("OpenAI API key is not configured. Returning mock summary for article id: {}", article.getId());
			return generateMockSummary(article);
		}
		
		log.info("Generating summary for article id: {}", article.getId());
		
		String prompt = PromptBuilder.buildSummaryPrompt(article);
		if (StringUtils.isBlank(prompt)) {
			log.warn("Generated prompt is null or empty for article id: {}. Using mock summary.", article.getId());
			return generateMockSummary(article);
		}
		
		ChatCompletionRequest request = buildRequest(prompt);
		ChatCompletionResult completion = openAiService.createChatCompletion(request);
		
		return extractContent(completion, article.getId());
	}

	/**
	 * Builds ChatGPT completion request.
	 */
	private ChatCompletionRequest buildRequest(String prompt) {
		List<ChatMessage> messages = new ArrayList<>();
		messages.add(new ChatMessage(ChatMessageRole.SYSTEM.value(), 
			ChatGptConstants.SYSTEM_ROLE_MESSAGE));
		messages.add(new ChatMessage(ChatMessageRole.USER.value(), prompt));
		
		return ChatCompletionRequest.builder()
			.model(model)
			.messages(messages)
			.maxTokens(ChatGptConstants.MAX_TOKENS)
			.temperature(ChatGptConstants.TEMPERATURE)
			.build();
	}
	
	/**
	 * Extracts content from ChatGPT completion response.
	 */
	private String extractContent(ChatCompletionResult completion, Long articleId) {
		if (completion == null) {
			log.error("OpenAI API returned null completion for article id: {}", articleId);
			throw new ExternalApiException("OpenAI API returned null response");
		}
		
		java.util.List<com.theokanning.openai.completion.chat.ChatCompletionChoice> choices = completion.getChoices();
		if (choices == null || choices.isEmpty()) {
			log.error("OpenAI API returned empty choices for article id: {}", articleId);
			throw new ExternalApiException("OpenAI API returned no choices in response");
		}
		
		com.theokanning.openai.completion.chat.ChatCompletionChoice firstChoice = choices.getFirst();
		if (firstChoice == null || firstChoice.getMessage() == null) {
			log.error("OpenAI API returned null choice or message for article id: {}", articleId);
			throw new ExternalApiException("OpenAI API returned invalid response structure");
		}
		
		String content = firstChoice.getMessage().getContent();
		if (content == null || content.trim().isEmpty()) {
			log.error("OpenAI API returned null or empty content for article id: {}", articleId);
			throw new ExternalApiException("OpenAI API returned empty summary content");
		}
		
		log.info("Successfully generated summary for article id: {}", articleId);
		return content.trim();
	}
	
	private String generateMockSummary(ArticleDTO article) {
		if (article == null) {
			return "Mock summary: Article data is not available. [Note: This is a mock summary generated because OpenAI API key is not configured.]";
		}
		
		StringBuilder mockSummary = new StringBuilder();
		String title = article.getTitle();
		if (title != null && !title.isEmpty()) {
			mockSummary.append("This is a mock summary for the article \"").append(title).append("\".");
		} else {
			mockSummary.append("This is a mock summary for an article without a title.");
		}
		
		if (article.getDescription() != null && !article.getDescription().isEmpty()) {
			// Extract first sentence or first 150 characters from description
			String descriptionPreview = article.getDescription().length() > 150 
				? String.format("%s...", article.getDescription().substring(0, 150))
				: article.getDescription();
			mockSummary.append(" ").append(descriptionPreview);
		}
		
		mockSummary.append(" [Note: This is a mock summary generated because OpenAI API key is not configured.]");
		return mockSummary.toString();
	}
	
	/**
	 * Fallback method when OpenAI API circuit breaker is open or service is unavailable
	 */
	public String generateSummaryFallback(ArticleDTO article, Exception e) {
		log.error("OpenAI API circuit breaker is open or service unavailable. Using fallback for article id: {}", 
			article != null ? article.getId() : "unknown", e);
		// Return mock summary as fallback when API is unavailable
		return generateMockSummary(article);
	}
}

