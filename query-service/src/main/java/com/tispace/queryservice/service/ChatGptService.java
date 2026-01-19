package com.tispace.queryservice.service;

import com.theokanning.openai.completion.chat.ChatCompletionRequest;
import com.theokanning.openai.completion.chat.ChatMessage;
import com.theokanning.openai.completion.chat.ChatMessageRole;
import com.theokanning.openai.service.OpenAiService;
import com.tispace.common.dto.ArticleDTO;
import com.tispace.common.exception.ExternalApiException;
import com.tispace.queryservice.constants.ChatGptConstants;
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
	
	@Value("${openai.model:gpt-3.5-turbo}")
	private String model;
	
	public ChatGptService(ObjectProvider<OpenAiService> openAiServiceProvider) {
		this.openAiService = openAiServiceProvider.getIfAvailable(); // Can be null if OpenAI API key is not configured
	}
	
	public String generateSummary(ArticleDTO article) {
		if (openAiService == null) {
			log.warn("OpenAI API key is not configured. Returning mock summary for article id: {}", article.getId());
			return generateMockSummary(article);
		}
		
		try {
			log.info("Generating summary for article id: {}", article.getId());
			
			String prompt = PromptBuilder.buildSummaryPrompt(article);
			
			List<ChatMessage> messages = new ArrayList<>();
			messages.add(new ChatMessage(ChatMessageRole.SYSTEM.value(), 
				ChatGptConstants.SYSTEM_ROLE_MESSAGE));
			messages.add(new ChatMessage(ChatMessageRole.USER.value(), prompt));
			
			ChatCompletionRequest request = ChatCompletionRequest.builder()
				.model(model)
				.messages(messages)
				.maxTokens(ChatGptConstants.MAX_TOKENS)
				.temperature(ChatGptConstants.TEMPERATURE)
				.build();
			
			String summary = openAiService.createChatCompletion(request)
				.getChoices()
				.getFirst()
				.getMessage()
				.getContent();
			
			log.info("Successfully generated summary for article id: {}", article.getId());
			return summary.trim();
			
		} catch (Exception e) {
			log.error("Error generating summary for article id: {}", article.getId(), e);
			throw new ExternalApiException(String.format("Failed to generate summary: %s", e.getMessage()), e);
		}
	}
	
	private String generateMockSummary(ArticleDTO article) {
		StringBuilder mockSummary = new StringBuilder();
		mockSummary.append("This is a mock summary for the article \"").append(article.getTitle()).append("\".");
		
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
}

