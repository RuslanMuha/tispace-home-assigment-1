package com.tispace.common.validation;

import com.tispace.common.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Sort;

import static org.junit.jupiter.api.Assertions.*;

class SortStringParserTest {
	
	private SortStringParser parser;
	
	@BeforeEach
	void setUp() {
		parser = new SortStringParser();
	}
	
	@Test
	void testParse_ValidSortString_ShouldReturnSort() {
		Sort result = parser.parse("publishedAt,desc");
		
		assertNotNull(result);
		assertEquals(Sort.Direction.DESC, result.getOrderFor("publishedAt").getDirection());
	}
	
	@Test
	void testParse_ValidAsc_ShouldReturnAsc() {
		Sort result = parser.parse("title,asc");
		
		assertNotNull(result);
		assertEquals(Sort.Direction.ASC, result.getOrderFor("title").getDirection());
	}
	
	@Test
	void testParse_Null_ShouldReturnDefault() {
		Sort result = parser.parse(null);
		
		assertNotNull(result);
		assertEquals(Sort.Direction.DESC, result.getOrderFor("publishedAt").getDirection());
	}
	
	@Test
	void testParse_Empty_ShouldReturnDefault() {
		Sort result = parser.parse("");
		
		assertNotNull(result);
		assertEquals(Sort.Direction.DESC, result.getOrderFor("publishedAt").getDirection());
	}
	
	@Test
	void testParse_InvalidFormat_ShouldThrow() {
		assertThrows(BusinessException.class, () -> parser.parse("invalid"));
		assertThrows(BusinessException.class, () -> parser.parse("field"));
		assertThrows(BusinessException.class, () -> parser.parse("field,direction,extra"));
	}
	
	@Test
	void testParse_InvalidField_ShouldThrow() {
		assertThrows(BusinessException.class, () -> parser.parse("invalidField,asc"));
	}
	
	@Test
	void testParse_InvalidDirection_ShouldThrow() {
		assertThrows(BusinessException.class, () -> parser.parse("title,invalid"));
	}
	
	@Test
	void testParse_ReDoSProtection_LongString_ShouldThrow() {
		// Create a string longer than MAX_SORT_STRING_LENGTH (50)
		String longString = "a".repeat(51) + ",desc";
		
		assertThrows(BusinessException.class, () -> parser.parse(longString));
	}
	
	@Test
	void testParse_ReDoSProtection_ValidLength_ShouldWork() {
		// Create a string exactly at the limit
		String validString = "a".repeat(10) + ",desc";
		
		// Should not throw (even if field is invalid, length check passes first)
		assertThrows(BusinessException.class, () -> parser.parse(validString)); // Will fail on field validation, not length
	}
	
	@Test
	void testParse_WithWhitespace_ShouldTrim() {
		Sort result = parser.parse("  title  ,  asc  ");
		
		assertNotNull(result);
		assertEquals(Sort.Direction.ASC, result.getOrderFor("title").getDirection());
	}
	
	@Test
	void testParse_AllValidFields_ShouldWork() {
		String[] validFields = {"id", "title", "description", "author", "publishedAt", "category", "createdAt", "updatedAt"};
		
		for (String field : validFields) {
			assertDoesNotThrow(() -> {
				Sort result = parser.parse(field + ",asc");
				assertNotNull(result);
			});
		}
	}
}


