package com.beat.dto;

import com.beat.entity.NewsItem;

import java.time.Instant;

public class NewsItemResponse {

    private Long id;
    private Long digestRunId;
    private String title;
    private String url;
    private String sourceName;
    private Instant publishedAt;
    private String summaryBlurb;
    private Integer rankPosition;

    public NewsItemResponse() {
    }

    public NewsItemResponse(NewsItem item) {
        this.id = item.getId();
        if (item.getDigestRun() != null) {
            this.digestRunId = item.getDigestRun().getId();
        }
        this.title = item.getTitle();
        this.url = item.getUrl();
        this.sourceName = item.getSourceName();
        this.publishedAt = item.getPublishedAt();
        this.summaryBlurb = item.getSummaryBlurb();
        this.rankPosition = item.getRankPosition();
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getDigestRunId() {
        return digestRunId;
    }

    public void setDigestRunId(Long digestRunId) {
        this.digestRunId = digestRunId;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getSourceName() {
        return sourceName;
    }

    public void setSourceName(String sourceName) {
        this.sourceName = sourceName;
    }

    public Instant getPublishedAt() {
        return publishedAt;
    }

    public void setPublishedAt(Instant publishedAt) {
        this.publishedAt = publishedAt;
    }

    public String getSummaryBlurb() {
        return summaryBlurb;
    }

    public void setSummaryBlurb(String summaryBlurb) {
        this.summaryBlurb = summaryBlurb;
    }

    public Integer getRankPosition() {
        return rankPosition;
    }

    public void setRankPosition(Integer rankPosition) {
        this.rankPosition = rankPosition;
    }
}
