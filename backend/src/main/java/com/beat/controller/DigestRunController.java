package com.beat.controller;

import com.beat.dto.DigestRunResponse;
import com.beat.dto.NewsItemResponse;
import com.beat.entity.DigestRun;
import com.beat.entity.NewsItem;
import com.beat.repository.ChannelRepository;
import com.beat.repository.DigestRunRepository;
import com.beat.repository.NewsItemRepository;
import com.beat.service.DynamicSchedulerService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import com.beat.entity.Channel;
import com.beat.exception.ForbiddenException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.List;

@RestController
@RequestMapping("/api")
public class DigestRunController {

    private final DigestRunRepository digestRunRepository;
    private final NewsItemRepository newsItemRepository;
    private final ChannelRepository channelRepository;
    private final DynamicSchedulerService dynamicSchedulerService;

    public DigestRunController(DigestRunRepository digestRunRepository,
                               NewsItemRepository newsItemRepository,
                               ChannelRepository channelRepository,
                               DynamicSchedulerService dynamicSchedulerService) {
        this.digestRunRepository = digestRunRepository;
        this.newsItemRepository = newsItemRepository;
        this.channelRepository = channelRepository;
        this.dynamicSchedulerService = dynamicSchedulerService;
    }

    @GetMapping("/channels/{channelId}/runs")
    public ResponseEntity<List<DigestRunResponse>> getChannelRuns(@PathVariable Long channelId,
                                                                  @AuthenticationPrincipal Jwt jwt) {
        Channel channel = channelRepository.findById(channelId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Channel not found: " + channelId));

        if (!channel.getUserId().equals(jwt.getSubject())) {
            throw new ForbiddenException("Access denied to channel runs for channel id: " + channelId);
        }

        List<DigestRun> runs = digestRunRepository.findByChannelIdOrderByRunAtDesc(channelId);
        List<DigestRunResponse> response = runs.stream().map(run -> {
            List<NewsItem> items = newsItemRepository.findByDigestRunIdOrderByRankPositionAsc(run.getId());
            return new DigestRunResponse(run, items.size());
        }).toList();

        return ResponseEntity.ok(response);
    }

    @GetMapping("/runs/{runId}")
    public ResponseEntity<DigestRunResponse> getRunDetails(@PathVariable Long runId,
                                                            @AuthenticationPrincipal Jwt jwt) {
        DigestRun run = digestRunRepository.findById(runId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Digest run not found: " + runId));

        if (!run.getChannel().getUserId().equals(jwt.getSubject())) {
            throw new ForbiddenException("Access denied to digest run id: " + runId);
        }

        List<NewsItem> items = newsItemRepository.findByDigestRunIdOrderByRankPositionAsc(run.getId());
        return ResponseEntity.ok(new DigestRunResponse(run, items.size()));
    }

    @GetMapping("/runs/{runId}/items")
    public ResponseEntity<List<NewsItemResponse>> getRunItems(@PathVariable Long runId,
                                                              @AuthenticationPrincipal Jwt jwt) {
        DigestRun run = digestRunRepository.findById(runId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Digest run not found: " + runId));

        if (!run.getChannel().getUserId().equals(jwt.getSubject())) {
            throw new ForbiddenException("Access denied to digest run items for run id: " + runId);
        }

        List<NewsItem> items = newsItemRepository.findByDigestRunIdOrderByRankPositionAsc(runId);
        List<NewsItemResponse> response = items.stream().map(NewsItemResponse::new).toList();
        return ResponseEntity.ok(response);
    }

    @PostMapping("/channels/{channelId}/run-now")
    public ResponseEntity<DigestRunResponse> triggerRunNow(@PathVariable Long channelId,
                                                           @AuthenticationPrincipal Jwt jwt) {
        Channel channel = channelRepository.findById(channelId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Channel not found: " + channelId));

        if (!channel.getUserId().equals(jwt.getSubject())) {
            throw new ForbiddenException("Access denied to trigger run-now for channel id: " + channelId);
        }

        DigestRun run = dynamicSchedulerService.triggerManualRun(channelId);
        List<NewsItem> items = newsItemRepository.findByDigestRunIdOrderByRankPositionAsc(run.getId());
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(new DigestRunResponse(run, items.size()));
    }
}

