package com.beat.entity;

import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(name = "news_item")
public class NewsItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "digest_run_id", nullable = false)
    private DigestRun digestRun;

    @Column(name = "title", nullable = false, columnDefinition = "TEXT")
    private String title;

    @Column(name = "url", nullable = false, columnDefinition = "TEXT")
    private String url;

    @Column(name = "source_name")
    private String sourceName;

    @Column(name = "published_at")
    private Instant publishedAt;

    @Column(name = "summary_blurb", columnDefinition = "TEXT")
    private String summaryBlurb;

    @Column(name = "rank_position")
    private Integer rankPosition;

    public NewsItem() {
    }

    public NewsItem(DigestRun digestRun, String title, String url, String sourceName, Instant publishedAt, String summaryBlurb, Integer rankPosition) {
        this.digestRun = digestRun;
        this.title = title;
        this.url = url;
        this.sourceName = sourceName;
        this.publishedAt = publishedAt;
        this.summaryBlurb = summaryBlurb;
        this.rankPosition = rankPosition;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public DigestRun getDigestRun() {
        return digestRun;
    }

    public void setDigestRun(DigestRun digestRun) {
        this.digestRun = digestRun;
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
