package com.tispace.dataingestion.repository;

import com.github.f4b6a3.uuid.UuidCreator;
import com.tispace.dataingestion.domain.entity.Article;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.sql.*;
import java.util.List;

@Repository
@RequiredArgsConstructor
@Slf4j
public class ArticleBatchRepository {

    private static final String INSERT_SQL =
            "INSERT INTO articles (id, title, description, author, published_at, category, created_at, updated_at) " +
                    "VALUES (?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP) " +
                    "ON CONFLICT (title) DO NOTHING";

    private final JdbcTemplate jdbcTemplate;

    @Transactional
    public int batchInsertIgnoreDuplicates(List<Article> articles) {
        if (articles == null || articles.isEmpty()) {
            return 0;
        }

        for (Article article : articles) {
            if (article.getId() == null) {
                article.setId(UuidCreator.getTimeOrderedEpoch());
            }
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
                    ps.setObject(1, a.getId(), Types.OTHER);
                    ps.setString(2, a.getTitle());
                    ps.setString(3, a.getDescription());
                    ps.setString(4, a.getAuthor());

                    if (a.getPublishedAt() != null) {
                        ps.setTimestamp(5, Timestamp.valueOf(a.getPublishedAt()));
                    } else {
                        ps.setTimestamp(5, null);
                    }

                    ps.setString(6, a.getCategory());
                }

                @Override
                public int getBatchSize() {
                    return batch.size();
                }
            });

            for (int r : results) {
                if (r > 0) insertedTotal += r;
            }
        }

        return insertedTotal;
    }
}
