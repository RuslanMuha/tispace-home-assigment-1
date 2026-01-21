package com.tispace.queryservice.service;

import com.tispace.common.contract.ArticleDTO;

public interface SummaryProvider {
	String generateSummary(ArticleDTO article) throws Exception;
	String getProviderName();
	boolean isAvailable();
}

