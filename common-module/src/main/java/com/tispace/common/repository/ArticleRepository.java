package com.tispace.common.repository;

import com.tispace.common.entity.Article;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ArticleRepository extends JpaRepository<Article, Long> {
	
	Page<Article> findByCategory(String category, Pageable pageable);
	
	Optional<Article> findTop1ByOrderByCreatedAtDesc();

}

