package com.tispace.dataingestion.service;

import com.tispace.common.entity.Article;
import com.tispace.dataingestion.repository.ArticleBatchRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Service for persisting articles to database.
 * Uses batch UPSERT operations with PostgreSQL ON CONFLICT DO NOTHING
 * for efficient handling of duplicates in multi-instance environment.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ArticlePersistenceService {

    private final ArticleBatchRepository articleBatchRepository;

    public int saveArticles(List<Article> articles) {
        if (articles == null || articles.isEmpty()) {
            return 0;
        }

        int inserted = articleBatchRepository.batchInsertIgnoreDuplicates(articles);

        log.debug("Saved articles: inserted={}, skipped={}", inserted, articles.size() - inserted);
        return inserted;
    }
}

