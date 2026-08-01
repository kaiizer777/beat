package com.beat.service;

import com.beat.dto.ChannelRequest;
import com.beat.dto.ChannelResponse;
import com.beat.entity.Channel;
import com.beat.entity.DigestRun;
import com.beat.exception.BadRequestException;
import com.beat.exception.ForbiddenException;
import com.beat.exception.ResourceNotFoundException;
import com.beat.repository.ChannelRepository;
import com.beat.repository.DigestRunRepository;
import com.beat.repository.NewsItemRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@Transactional
public class ChannelService {

    private final ChannelRepository channelRepository;
    private final DigestRunRepository digestRunRepository;
    private final NewsItemRepository newsItemRepository;
    private final DynamicSchedulerService dynamicSchedulerService;

    public ChannelService(ChannelRepository channelRepository,
                          DigestRunRepository digestRunRepository,
                          NewsItemRepository newsItemRepository,
                          DynamicSchedulerService dynamicSchedulerService) {
        this.channelRepository = channelRepository;
        this.digestRunRepository = digestRunRepository;
        this.newsItemRepository = newsItemRepository;
        this.dynamicSchedulerService = dynamicSchedulerService;
    }

    public ChannelResponse createChannel(ChannelRequest request, String userId) {
        validateTimezone(request.getTimezone());

        Channel channel = new Channel(
                userId,
                request.getName(),
                request.getTopicQuery(),
                request.getArticleCount(),
                request.getCronTime(),
                request.getTimezone(),
                request.getIsActive()
        );

        Channel saved = channelRepository.save(channel);
        dynamicSchedulerService.scheduleChannel(saved);
        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public List<ChannelResponse> getAllChannelsForUser(String userId) {
        return channelRepository.findByUserId(userId).stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public ChannelResponse getChannelByIdForUser(Long id, String userId) {
        Channel channel = channelRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Channel not found with id: " + id));

        if (!channel.getUserId().equals(userId)) {
            throw new ForbiddenException("Access denied to channel id: " + id);
        }

        return toResponse(channel);
    }

    public ChannelResponse updateChannelForUser(Long id, ChannelRequest request, String userId) {
        Channel channel = channelRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Channel not found with id: " + id));

        if (!channel.getUserId().equals(userId)) {
            throw new ForbiddenException("Access denied to channel id: " + id);
        }

        validateTimezone(request.getTimezone());

        channel.setName(request.getName());
        channel.setTopicQuery(request.getTopicQuery());
        channel.setArticleCount(request.getArticleCount());
        channel.setCronTime(request.getCronTime());
        channel.setTimezone(request.getTimezone());
        if (request.getIsActive() != null) {
            channel.setIsActive(request.getIsActive());
        }

        Channel updated = channelRepository.save(channel);
        dynamicSchedulerService.scheduleChannel(updated);
        return toResponse(updated);
    }

    public void deleteChannelForUser(Long id, String userId) {
        Channel channel = channelRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Channel not found with id: " + id));

        if (!channel.getUserId().equals(userId)) {
            throw new ForbiddenException("Access denied to channel id: " + id);
        }

        // Cascade-delete: news_items → digest_runs → channel (FK order matters)
        List<DigestRun> runs = digestRunRepository.findByChannelId(id);
        for (DigestRun run : runs) {
            newsItemRepository.deleteByDigestRunId(run.getId());
        }
        digestRunRepository.deleteAll(runs);

        dynamicSchedulerService.unscheduleChannel(id);
        channelRepository.delete(channel);
    }


    private ChannelResponse toResponse(Channel channel) {
        Optional<DigestRun> latestRunOpt = digestRunRepository.findTopByChannelIdOrderByRunAtDesc(channel.getId());
        String status = latestRunOpt.map(run -> run.getStatus().name()).orElse(null);
        var runAt = latestRunOpt.map(DigestRun::getRunAt).orElse(null);
        return ChannelResponse.fromEntity(channel, status, runAt);
    }

    private void validateTimezone(String timezone) {
        if (timezone == null || !ZoneId.getAvailableZoneIds().contains(timezone)) {
            throw new BadRequestException("Invalid IANA timezone: " + timezone);
        }
    }
}

