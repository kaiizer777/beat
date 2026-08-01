package com.beat.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalTime;

public class ChannelRequest {

    @NotBlank(message = "Name is required")
    private String name;

    @NotBlank(message = "Topic query is required")
    private String topicQuery;

    @NotNull(message = "Article count is required")
    @Min(value = 5, message = "Article count must be at least 5")
    @Max(value = 25, message = "Article count must be at most 25")
    private Integer articleCount;

    @NotNull(message = "Cron time is required")
    @JsonFormat(pattern = "HH:mm[:ss]")
    private LocalTime cronTime;

    @NotBlank(message = "Timezone is required")
    private String timezone;

    private Boolean isActive = true;

    public ChannelRequest() {
    }

    public ChannelRequest(String name, String topicQuery, Integer articleCount, LocalTime cronTime, String timezone, Boolean isActive) {
        this.name = name;
        this.topicQuery = topicQuery;
        this.articleCount = articleCount;
        this.cronTime = cronTime;
        this.timezone = timezone;
        this.isActive = isActive;
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
}
