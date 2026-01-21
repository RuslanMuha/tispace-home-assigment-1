package com.tispace.dataingestion.domain.validation;

import com.tispace.common.contract.ArticleDTO;
import com.tispace.dataingestion.domain.entity.Article;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class ArticleValidator {

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
	
	public String validate(Article article) {
		if (article == null) {
			return "Article cannot be null";
		}
		
		if (StringUtils.isEmpty(article.getTitle())) {
			return "Article title cannot be null or empty";
		}
		
		return null;
	}

    public void validateInput(ArticleDTO article) {
        if (article == null) {
            throw new IllegalArgumentException("Article cannot be null");
        }
    }
}

