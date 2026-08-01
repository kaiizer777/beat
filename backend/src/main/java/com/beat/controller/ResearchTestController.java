package com.beat.controller;

import com.beat.dto.RawArticle;
import com.beat.service.ResearchPipelineService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/test")
public class ResearchTestController {

    private static final Logger log = LoggerFactory.getLogger(ResearchTestController.class);
    private final ResearchPipelineService researchPipelineService;

    public ResearchTestController(ResearchPipelineService researchPipelineService) {
        this.researchPipelineService = researchPipelineService;
    }

    @GetMapping("/research")
    public ResponseEntity<Map<String, Object>> testResearchPipeline(@RequestParam(defaultValue = "AI agents") String topic) {
        log.info("--- MANUAL TEST HARNESS TRIGGERED FOR TOPIC: '{}' ---", topic);

        long startTime = System.currentTimeMillis();
        List<RawArticle> articles = researchPipelineService.executeResearch(topic);
        long duration = System.currentTimeMillis() - startTime;

        log.info("--- TEST HARNESS RESULTS ---");
        log.info("Topic: {}", topic);
        log.info("Total Deduplicated Articles: {}", articles.size());
        log.info("Duration: {} ms", duration);

        for (int i = 0; i < articles.size(); i++) {
            RawArticle article = articles.get(i);
            log.info("[Article #{}] Title: {}", i + 1, article.getTitle());
            log.info("              Publisher: {} | Date: {} | Source: {}", article.getPublisher(), article.getPublishedAt(), article.getFetchSource());
            log.info("              URL: {}", article.getUrl());
            log.info("              Full Text Length: {} chars", article.getFullText() != null ? article.getFullText().length() : 0);
            if (article.getFullText() != null && !article.getFullText().isBlank()) {
                String preview = article.getFullText().substring(0, Math.min(150, article.getFullText().length())).replaceAll("\n", " ");
                log.info("              Text Snippet: {}...", preview);
            }
        }
        log.info("----------------------------");

        List<Map<String, Object>> articleSummaries = articles.stream().map(a -> {
            Map<String, Object> map = new HashMap<>();
            map.put("title", a.getTitle());
            map.put("url", a.getUrl());
            map.put("publisher", a.getPublisher());
            map.put("publishedAt", a.getPublishedAt());
            map.put("fetchSource", a.getFetchSource());
            map.put("snippet", a.getSnippet());
            map.put("fullTextLength", a.getFullText() != null ? a.getFullText().length() : 0);
            if (a.getFullText() != null) {
                map.put("textPreview", a.getFullText().substring(0, Math.min(200, a.getFullText().length())));
            }
            return map;
        }).collect(Collectors.toList());

        Map<String, Object> response = new HashMap<>();
        response.put("topic", topic);
        response.put("totalArticles", articles.size());
        response.put("durationMs", duration);
        response.put("articles", articleSummaries);

        return ResponseEntity.ok(response);
    }
}
