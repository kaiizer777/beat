package com.beat.service;

import com.beat.dto.ChannelRequest;
import com.beat.dto.ChannelResponse;
import com.beat.entity.Channel;
import com.beat.exception.BadRequestException;
import com.beat.exception.ResourceNotFoundException;
import com.beat.repository.ChannelRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZoneId;
import java.util.List;
import java.util.stream.Collectors;

@Service
@Transactional
public class ChannelService {

    private final ChannelRepository channelRepository;

    public ChannelService(ChannelRepository channelRepository) {
        this.channelRepository = channelRepository;
    }

    public ChannelResponse createChannel(ChannelRequest request) {
        validateTimezone(request.getTimezone());

        Channel channel = new Channel(
                request.getName(),
                request.getTopicQuery(),
                request.getArticleCount(),
                request.getCronTime(),
                request.getTimezone(),
                request.getIsActive()
        );

        Channel saved = channelRepository.save(channel);
        return ChannelResponse.fromEntity(saved);
    }

    @Transactional(readOnly = true)
    public List<ChannelResponse> getAllChannels() {
        return channelRepository.findAll().stream()
                .map(ChannelResponse::fromEntity)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public ChannelResponse getChannelById(Long id) {
        Channel channel = channelRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Channel not found with id: " + id));
        return ChannelResponse.fromEntity(channel);
    }

    public ChannelResponse updateChannel(Long id, ChannelRequest request) {
        Channel channel = channelRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Channel not found with id: " + id));

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
        return ChannelResponse.fromEntity(updated);
    }

    public void deleteChannel(Long id) {
        Channel channel = channelRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Channel not found with id: " + id));
        channelRepository.delete(channel);
    }

    private void validateTimezone(String timezone) {
        if (timezone == null || !ZoneId.getAvailableZoneIds().contains(timezone)) {
            throw new BadRequestException("Invalid IANA timezone: " + timezone);
        }
    }
}
