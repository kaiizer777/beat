package com.beat.repository;

import com.beat.entity.NewsItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface NewsItemRepository extends JpaRepository<NewsItem, Long> {
    List<NewsItem> findByDigestRunIdOrderByRankPositionAsc(Long digestRunId);
}
