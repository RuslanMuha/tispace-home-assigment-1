package com.tispace.queryservice.service;

import com.tispace.common.dto.ArticleDTO;

/**
 * Strategy interface for summary generation providers.
 * Allows easy extension to support multiple summary sources (ChatGPT, Claude, etc.).
 * Follows Strategy pattern and Open/Closed Principle.
 */
public interface SummaryProvider {
	
	/**
	 * Generates a summary for the given article.
	 * 
	 * @param article the article to generate summary for
	 * @return the generated summary text
	 * @throws Exception if summary generation fails
	 */
	String generateSummary(ArticleDTO article) throws Exception;
	
	/**
	 * Gets the name of this provider.
	 * 
	 * @return provider name
	 */
	String getProviderName();
	
	/**
	 * Checks if this provider is available/configured.
	 * 
	 * @return true if provider is available
	 */
	boolean isAvailable();
}

