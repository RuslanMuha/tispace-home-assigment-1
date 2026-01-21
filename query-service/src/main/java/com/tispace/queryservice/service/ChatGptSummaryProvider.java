package com.tispace.queryservice.service;

import com.tispace.common.contract.ArticleDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@Slf4j
@RequiredArgsConstructor
public class ChatGptSummaryProvider implements SummaryProvider {
	
	private final ChatGptService chatGptService;
	
	@Override
	public String generateSummary(ArticleDTO article) throws Exception {
		return chatGptService.generateSummary(article);
	}
	
	@Override
	public String getProviderName() {
		return "ChatGPT";
	}
	
	@Override
	public boolean isAvailable() {
		return true;
	}
}



