package com.beat.repository;

import com.beat.entity.DigestRun;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface DigestRunRepository extends JpaRepository<DigestRun, Long> {
    List<DigestRun> findByChannelId(Long channelId);
}
