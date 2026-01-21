package com.tispace.dataingestion.repository;

import com.tispace.common.entity.Article;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.sql.*;
import java.util.List;

/**
 * Repository for batch operations on articles using JDBC batch inserts for performance.
 * Uses PostgreSQL ON CONFLICT DO NOTHING to handle duplicates efficiently.
 * 
 * <p>Deduplication: Uses title as unique constraint (ON CONFLICT (title) DO NOTHING).
 * Duplicate articles are silently skipped (no error thrown).
 * 
 * <p>Batch processing: Processes articles in batches of 50 for optimal performance.
 * This matches Hibernate batch_size configuration.
 * 
 * <p>Guarantees: Only new articles (by title) are inserted. Duplicates are ignored.
 * Returns count of actually inserted articles (not total processed).
 * 
 * <p>Side effects: Database writes, transaction (method is @Transactional).
 * 
 * <p>Thread safety: Safe for concurrent calls (PostgreSQL handles concurrent inserts).
 */
@Repository
@RequiredArgsConstructor
@Slf4j
public class ArticleBatchRepository {

    private static final String INSERT_SQL =
            "INSERT INTO articles (title, description, author, published_at, category, created_at, updated_at) " +
                    "VALUES (?, ?, ?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP) " +
                    "ON CONFLICT (title) DO NOTHING";

    private final JdbcTemplate jdbcTemplate;

    /**
     * Batch inserts articles, ignoring duplicates based on title.
     * 
     * @param articles list of articles to insert (null/empty returns 0)
     * @return number of actually inserted articles (duplicates not counted)
     * 
     * <p>Side effects: Database batch INSERT operations, transaction commit.
     */
    @Transactional
    public int batchInsertIgnoreDuplicates(List<Article> articles) {
        if (articles == null || articles.isEmpty()) {
            return 0;
        }

        final int batchSize = 50;
        int insertedTotal = 0;

        for (int i = 0; i < articles.size(); i += batchSize) {
            int end = Math.min(i + batchSize, articles.size());
            List<Article> batch = articles.subList(i, end);

            int[] results = jdbcTemplate.batchUpdate(INSERT_SQL, new BatchPreparedStatementSetter() {
                @Override
                public void setValues(PreparedStatement ps, int idx) throws SQLException {
                    Article a = batch.get(idx);
                    ps.setString(1, a.getTitle());
                    ps.setString(2, a.getDescription());
                    ps.setString(3, a.getAuthor());

                    if (a.getPublishedAt() != null) {
                        ps.setTimestamp(4, Timestamp.valueOf(a.getPublishedAt()));
                    } else {
                        ps.setTimestamp(4, null);
                    }

                    ps.setString(5, a.getCategory());
                }

                @Override
                public int getBatchSize() {
                    return batch.size();
                }
            });

            // count only actual inserts (0 = duplicate skipped)
            for (int r : results) {
                if (r > 0) insertedTotal += r;
            }
        }

        return insertedTotal;
    }
}
