package com.beat.repository;

import com.beat.entity.Channel;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ChannelRepository extends JpaRepository<Channel, Long> {
    List<Channel> findByIsActiveTrue();
    List<Channel> findByUserId(String userId);
    Optional<Channel> findByIdAndUserId(Long id, String userId);
}

