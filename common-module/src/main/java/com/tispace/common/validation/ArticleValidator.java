package com.tispace.common.validation;

import com.tispace.common.dto.ArticleDTO;
import com.tispace.common.entity.Article;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

/**
 * Validator for Article entities.
 * Separates validation logic from business logic (SRP).
 */
@Component
@Slf4j
public class ArticleValidator {

	/**
	 * Validates that an article has a non-empty title.
	 * 
	 * @param article the article to validate
	 * @return true if article is valid, false otherwise
	 */
	public boolean isValid(Article article) {
		if (article == null) {
			log.debug("Article is null");
			return false;
		}
		
		if (StringUtils.isEmpty(article.getTitle())) {
			log.debug("Article title is null or empty");
			return false;
		}
		
		return true;
	}
	
	/**
	 * Validates and returns a descriptive message if invalid.
	 * 
	 * @param article the article to validate
	 * @return validation error message or null if valid
	 */
	public String validate(Article article) {
		if (article == null) {
			return "Article cannot be null";
		}
		
		if (StringUtils.isEmpty(article.getTitle())) {
			return "Article title cannot be null or empty";
		}
		
		return null; // Valid
	}

    public void validateInput(ArticleDTO article) {
        if (article == null) {
            throw new IllegalArgumentException("Article cannot be null");
        }
    }
}


