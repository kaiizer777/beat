package com.beat.repository;

import com.beat.entity.DigestRun;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface DigestRunRepository extends JpaRepository<DigestRun, Long> {
    List<DigestRun> findByChannelId(Long channelId);
    List<DigestRun> findByChannelIdOrderByRunAtDesc(Long channelId);
    Optional<DigestRun> findTopByChannelIdOrderByRunAtDesc(Long channelId);
    List<DigestRun> findByStatus(com.beat.entity.DigestRunStatus status);
}

