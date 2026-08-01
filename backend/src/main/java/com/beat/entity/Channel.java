package com.beat.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.time.LocalTime;

@Entity
@Table(name = "channel")
public class Channel {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank
    @Column(name = "name", nullable = false)
    private String name;

    @NotBlank
    @Column(name = "topic_query", nullable = false, columnDefinition = "TEXT")
    private String topicQuery;

    @NotNull
    @Min(5)
    @Max(25)
    @Column(name = "article_count", nullable = false)
    private Integer articleCount;

    @NotNull
    @Column(name = "cron_time", nullable = false)
    private LocalTime cronTime;

    @NotBlank
    @Column(name = "timezone", nullable = false)
    private String timezone;

    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;

    @NotBlank
    @Column(name = "user_id", nullable = false)
    private String userId;

    @Column(name = "last_run_at")
    private Instant lastRunAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public Channel() {
    }

    public Channel(String userId, String name, String topicQuery, Integer articleCount, LocalTime cronTime, String timezone, Boolean isActive) {
        this.userId = userId;
        this.name = name;
        this.topicQuery = topicQuery;
        this.articleCount = articleCount;
        this.cronTime = cronTime;
        this.timezone = timezone;
        this.isActive = isActive != null ? isActive : true;
    }

    public Channel(String name, String topicQuery, Integer articleCount, LocalTime cronTime, String timezone, Boolean isActive) {
        this("test_user_id", name, topicQuery, articleCount, cronTime, timezone, isActive);
    }


    @PrePersist
    protected void onCreate() {
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
        if (this.isActive == null) {
            this.isActive = true;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getTopicQuery() {
        return topicQuery;
    }

    public void setTopicQuery(String topicQuery) {
        this.topicQuery = topicQuery;
    }

    public Integer getArticleCount() {
        return articleCount;
    }

    public void setArticleCount(Integer articleCount) {
        this.articleCount = articleCount;
    }

    public LocalTime getCronTime() {
        return cronTime;
    }

    public void setCronTime(LocalTime cronTime) {
        this.cronTime = cronTime;
    }

    public String getTimezone() {
        return timezone;
    }

    public void setTimezone(String timezone) {
        this.timezone = timezone;
    }

    public Boolean getIsActive() {
        return isActive;
    }

    public void setIsActive(Boolean isActive) {
        this.isActive = isActive;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public Instant getLastRunAt() {
        return lastRunAt;
    }

    public void setLastRunAt(Instant lastRunAt) {
        this.lastRunAt = lastRunAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
