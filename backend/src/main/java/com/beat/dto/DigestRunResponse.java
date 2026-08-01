package com.beat.dto;

import com.beat.entity.DigestRun;
import com.beat.entity.DigestRunStatus;

import java.time.Instant;

public class DigestRunResponse {

    private Long id;
    private Long channelId;
    private String channelName;
    private Instant runAt;
    private DigestRunStatus status;
    private String errorMessage;
    private Boolean emailSent;
    private Integer itemCount;

    public DigestRunResponse() {
    }

    public DigestRunResponse(DigestRun run, Integer itemCount) {
        this.id = run.getId();
        if (run.getChannel() != null) {
            this.channelId = run.getChannel().getId();
            this.channelName = run.getChannel().getName();
        }
        this.runAt = run.getRunAt();
        this.status = run.getStatus();
        this.errorMessage = run.getErrorMessage();
        this.emailSent = run.getEmailSent();
        this.itemCount = itemCount;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getChannelId() {
        return channelId;
    }

    public void setChannelId(Long channelId) {
        this.channelId = channelId;
    }

    public String getChannelName() {
        return channelName;
    }

    public void setChannelName(String channelName) {
        this.channelName = channelName;
    }

    public Instant getRunAt() {
        return runAt;
    }

    public void setRunAt(Instant runAt) {
        this.runAt = runAt;
    }

    public DigestRunStatus getStatus() {
        return status;
    }

    public void setStatus(DigestRunStatus status) {
        this.status = status;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public Boolean getEmailSent() {
        return emailSent;
    }

    public void setEmailSent(Boolean emailSent) {
        this.emailSent = emailSent;
    }

    public Integer getItemCount() {
        return itemCount;
    }

    public void setItemCount(Integer itemCount) {
        this.itemCount = itemCount;
    }
}
