package com.beat.controller;

import com.beat.dto.RawArticle;
import com.beat.entity.Channel;
import com.beat.entity.DigestRun;
import com.beat.entity.NewsItem;
import com.beat.repository.ChannelRepository;
import com.beat.repository.NewsItemRepository;
import com.beat.service.DigestPipelineService;
import com.beat.service.ResearchPipelineService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/test")
public class ResearchTestController {

    private static final Logger log = LoggerFactory.getLogger(ResearchTestController.class);

    private final ResearchPipelineService researchPipelineService;
    private final DigestPipelineService digestPipelineService;
    private final ChannelRepository channelRepository;
    private final NewsItemRepository newsItemRepository;

    public ResearchTestController(ResearchPipelineService researchPipelineService,
                                  DigestPipelineService digestPipelineService,
                                  ChannelRepository channelRepository,
                                  NewsItemRepository newsItemRepository) {
        this.researchPipelineService = researchPipelineService;
        this.digestPipelineService = digestPipelineService;
        this.channelRepository = channelRepository;
        this.newsItemRepository = newsItemRepository;
    }

    @GetMapping("/research")
    public ResponseEntity<Map<String, Object>> testResearchPipeline(@RequestParam(defaultValue = "AI agents") String topic) {
        log.info("--- MANUAL TEST HARNESS TRIGGERED FOR TOPIC: '{}' ---", topic);

        long startTime = System.currentTimeMillis();
        List<RawArticle> articles = researchPipelineService.executeResearch(topic);
        long duration = System.currentTimeMillis() - startTime;

        List<Map<String, Object>> articleSummaries = articles.stream().map(a -> {
            Map<String, Object> map = new HashMap<>();
            map.put("title", a.getTitle());
            map.put("url", a.getUrl());
            map.put("publisher", a.getPublisher());
            map.put("publishedAt", a.getPublishedAt());
            map.put("fetchSource", a.getFetchSource());
            map.put("snippet", a.getSnippet());
            map.put("fullTextLength", a.getFullText() != null ? a.getFullText().length() : 0);
            return map;
        }).collect(Collectors.toList());

        Map<String, Object> response = new HashMap<>();
        response.put("topic", topic);
        response.put("totalArticles", articles.size());
        response.put("durationMs", duration);
        response.put("articles", articleSummaries);

        return ResponseEntity.ok(response);
    }

    @PostMapping("/digest-run/{channelId}")
    public ResponseEntity<Map<String, Object>> testDigestPipelineForChannel(@PathVariable Long channelId) {
        log.info("--- MANUAL DIGEST PIPELINE TRIGGERED FOR CHANNEL ID: {} ---", channelId);

        Channel channel = channelRepository.findById(channelId)
                .orElseThrow(() -> new IllegalArgumentException("Channel not found with ID: " + channelId));

        long startTime = System.currentTimeMillis();
        DigestRun run = digestPipelineService.executeDigestPipeline(channel);
        long duration = System.currentTimeMillis() - startTime;

        List<NewsItem> items = newsItemRepository.findByDigestRunIdOrderByRankPositionAsc(run.getId());

        Map<String, Object> response = new HashMap<>();
        response.put("digestRunId", run.getId());
        response.put("channelId", channel.getId());
        response.put("channelName", channel.getName());
        response.put("status", run.getStatus());
        response.put("errorMessage", run.getErrorMessage());
        response.put("durationMs", duration);
        response.put("itemsCount", items.size());
        response.put("items", items);

        return ResponseEntity.ok(response);
    }

    @GetMapping("/digest-test")
    public ResponseEntity<Map<String, Object>> testAdhocDigestPipeline(
            @RequestParam(defaultValue = "AI/ML News") String name,
            @RequestParam(defaultValue = "artificial intelligence and machine learning developments") String topic,
            @RequestParam(defaultValue = "5") Integer count) {

        log.info("--- MANUAL AD-HOC DIGEST PIPELINE TRIGGERED FOR TOPIC: '{}' ---", topic);

        // Find or create transient channel for testing
        Channel channel = new Channel();
        channel.setName(name);
        channel.setTopicQuery(topic);
        channel.setArticleCount(count);
        channel.setCronTime(LocalTime.of(8, 0));
        channel.setTimezone("Asia/Kolkata");
        channel.setIsActive(true);
        channel.setUserId("test-user");
        channel = channelRepository.save(channel);

        long startTime = System.currentTimeMillis();
        DigestRun run = digestPipelineService.executeDigestPipeline(channel);
        long duration = System.currentTimeMillis() - startTime;

        List<NewsItem> items = newsItemRepository.findByDigestRunIdOrderByRankPositionAsc(run.getId());

        Map<String, Object> response = new HashMap<>();
        response.put("digestRunId", run.getId());
        response.put("channelId", channel.getId());
        response.put("channelName", channel.getName());
        response.put("status", run.getStatus());
        response.put("errorMessage", run.getErrorMessage());
        response.put("durationMs", duration);
        response.put("itemsCount", items.size());
        response.put("items", items);

        return ResponseEntity.ok(response);
    }
}
