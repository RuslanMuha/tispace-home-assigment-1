package com.tispace.dataingestion.service;

import com.tispace.dataingestion.domain.entity.Article;
import com.tispace.dataingestion.repository.ArticleBatchRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Persists articles using batch UPSERT with ON CONFLICT DO NOTHING.
 * Handles duplicates efficiently in multi-instance deployments.
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

