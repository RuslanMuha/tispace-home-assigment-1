package com.tispace.common.validation;

import com.tispace.common.entity.Article;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ArticleValidatorTest {
	
	private ArticleValidator validator;
	
	@BeforeEach
	void setUp() {
		validator = new ArticleValidator();
	}
	
	@Test
	void testIsValid_ValidArticle_ShouldReturnTrue() {
		Article article = new Article();
		article.setTitle("Test Title");
		
		assertTrue(validator.isValid(article));
	}
	
	@Test
	void testIsValid_NullArticle_ShouldReturnFalse() {
		assertFalse(validator.isValid(null));
	}
	
	@Test
	void testIsValid_EmptyTitle_ShouldReturnFalse() {
		Article article = new Article();
		article.setTitle("");
		
		assertFalse(validator.isValid(article));
	}
	
	@Test
	void testIsValid_NullTitle_ShouldReturnFalse() {
		Article article = new Article();
		article.setTitle(null);
		
		assertFalse(validator.isValid(article));
	}
	
	@Test
	void testValidate_ValidArticle_ShouldReturnNull() {
		Article article = new Article();
		article.setTitle("Test Title");
		
		assertNull(validator.validate(article));
	}
	
	@Test
	void testValidate_NullArticle_ShouldReturnErrorMessage() {
		String result = validator.validate(null);
		
		assertNotNull(result);
		assertTrue(result.contains("null"));
	}
	
	@Test
	void testValidate_EmptyTitle_ShouldReturnErrorMessage() {
		Article article = new Article();
		article.setTitle("");
		
		String result = validator.validate(article);
		
		assertNotNull(result);
		assertTrue(result.contains("title"));
	}
}


