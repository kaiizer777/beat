package com.beat.service;

import com.beat.entity.Channel;
import com.beat.repository.ChannelRepository;
import com.beat.repository.DigestRunRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;

import static org.junit.jupiter.api.Assertions.*;

class DynamicSchedulerServiceTest {

    private ChannelRepository channelRepository;
    private DigestRunRepository digestRunRepository;
    private DigestPipelineService digestPipelineService;
    private DynamicSchedulerService dynamicSchedulerService;

    @BeforeEach
    void setUp() {
        channelRepository = Mockito.mock(ChannelRepository.class);
        digestRunRepository = Mockito.mock(DigestRunRepository.class);
        digestPipelineService = Mockito.mock(DigestPipelineService.class);
        dynamicSchedulerService = new DynamicSchedulerService(channelRepository, digestRunRepository, digestPipelineService);
    }

    @Test
    void testIsDue_ChannelInactive_ReturnsFalse() {
        Channel channel = new Channel("user1", "AI News", "AI", 10, LocalTime.of(8, 0), "Asia/Kolkata", false);
        Instant now = ZonedDateTime.of(2026, 8, 2, 8, 2, 0, 0, ZoneId.of("Asia/Kolkata")).toInstant();

        assertFalse(dynamicSchedulerService.isDue(channel, now));
    }

    @Test
    void testIsDue_WithinPollingWindow_ReturnsTrue() {
        // Scheduled at 08:00 Asia/Kolkata
        Channel channel = new Channel("user1", "AI News", "AI", 10, LocalTime.of(8, 0), "Asia/Kolkata", true);
        // Poll runs at 08:03 Asia/Kolkata
        Instant now = ZonedDateTime.of(2026, 8, 2, 8, 3, 0, 0, ZoneId.of("Asia/Kolkata")).toInstant();

        assertTrue(dynamicSchedulerService.isDue(channel, now));
    }

    @Test
    void testIsDue_OutsidePollingWindow_ReturnsFalse() {
        Channel channel = new Channel("user1", "AI News", "AI", 10, LocalTime.of(8, 0), "Asia/Kolkata", true);
        // Poll runs at 08:15 Asia/Kolkata (>10 min window)
        Instant now = ZonedDateTime.of(2026, 8, 2, 8, 15, 0, 0, ZoneId.of("Asia/Kolkata")).toInstant();

        assertFalse(dynamicSchedulerService.isDue(channel, now));
    }

    @Test
    void testIsDue_AlreadyRan_ReturnsFalse() {
        Channel channel = new Channel("user1", "AI News", "AI", 10, LocalTime.of(8, 0), "Asia/Kolkata", true);
        Instant now = ZonedDateTime.of(2026, 8, 2, 8, 5, 0, 0, ZoneId.of("Asia/Kolkata")).toInstant();
        
        // Channel ran at 08:01 today
        Instant lastRun = ZonedDateTime.of(2026, 8, 2, 8, 1, 0, 0, ZoneId.of("Asia/Kolkata")).toInstant();
        channel.setLastRunAt(lastRun);

        assertFalse(dynamicSchedulerService.isDue(channel, now));
    }

    @Test
    void testIsDue_RanYesterday_ReturnsTrueForToday() {
        Channel channel = new Channel("user1", "AI News", "AI", 10, LocalTime.of(8, 0), "Asia/Kolkata", true);
        Instant now = ZonedDateTime.of(2026, 8, 2, 8, 2, 0, 0, ZoneId.of("Asia/Kolkata")).toInstant();

        // Channel ran yesterday at 08:01
        Instant lastRunYesterday = ZonedDateTime.of(2026, 8, 1, 8, 1, 0, 0, ZoneId.of("Asia/Kolkata")).toInstant();
        channel.setLastRunAt(lastRunYesterday);

        assertTrue(dynamicSchedulerService.isDue(channel, now));
    }
}
