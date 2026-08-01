package com.beat.dto;

import com.beat.entity.Channel;
import com.fasterxml.jackson.annotation.JsonFormat;

import java.time.Instant;
import java.time.LocalTime;

public class ChannelResponse {

    private Long id;
    private String name;
    private String topicQuery;
    private Integer articleCount;

    @JsonFormat(pattern = "HH:mm:ss")
    private LocalTime cronTime;

    private String timezone;
    private Boolean isActive;
    private String lastRunStatus;
    private Instant lastRunAt;
    private Instant createdAt;
    private Instant updatedAt;

    public ChannelResponse() {
    }

    public static ChannelResponse fromEntity(Channel channel) {
        return fromEntity(channel, null, null);
    }

    public static ChannelResponse fromEntity(Channel channel, String lastRunStatus, Instant lastRunAt) {
        ChannelResponse resp = new ChannelResponse();
        resp.setId(channel.getId());
        resp.setName(channel.getName());
        resp.setTopicQuery(channel.getTopicQuery());
        resp.setArticleCount(channel.getArticleCount());
        resp.setCronTime(channel.getCronTime());
        resp.setTimezone(channel.getTimezone());
        resp.setIsActive(channel.getIsActive());
        resp.setLastRunStatus(lastRunStatus);
        resp.setLastRunAt(lastRunAt);
        resp.setCreatedAt(channel.getCreatedAt());
        resp.setUpdatedAt(channel.getUpdatedAt());
        return resp;
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

    public String getLastRunStatus() {
        return lastRunStatus;
    }

    public void setLastRunStatus(String lastRunStatus) {
        this.lastRunStatus = lastRunStatus;
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

