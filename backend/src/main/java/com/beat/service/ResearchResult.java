package com.beat.service;

import com.beat.dto.RawArticle;

import java.util.List;
import java.util.Map;

/**
 * Wrapper returned by {@link ResearchPipelineService#executeResearch(String)}.
 * Carries the final candidate pool plus a structured metrics map so callers
 * (currently {@link DigestPipelineService}) can log a single end-of-pipeline
 * summary without re-instrumenting every stage.
 */
public final class ResearchResult {

    private final List<RawArticle> articles;
    private final Map<String, Object> metrics;

    public ResearchResult(List<RawArticle> articles, Map<String, Object> metrics) {
        this.articles = articles;
        this.metrics = metrics;
    }

    public List<RawArticle> getArticles() {
        return articles;
    }

    public Map<String, Object> getMetrics() {
        return metrics;
    }
}
