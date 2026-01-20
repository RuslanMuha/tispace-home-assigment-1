package com.tispace.queryservice.constants;

public final class ArticleConstants {
	
	private ArticleConstants() {
		// Utility class - prevent instantiation
	}
	
	public static final String CACHE_KEY_PREFIX = "article:summary:";
	public static final String SINGLE_FLIGHT_KEY_KEY_PREFIX = "singleflight:article:summary:";

	public static String buildCacheKey(Long articleId) {
		return CACHE_KEY_PREFIX + articleId;
	}

    public static String buildSingleFlightKey(String cacheKey) {
        return SINGLE_FLIGHT_KEY_KEY_PREFIX + cacheKey;
    }
}


