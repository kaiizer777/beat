package com.beat.entity;

import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(name = "digest_run")
public class DigestRun {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "channel_id", nullable = false)
    private Channel channel;

    @Column(name = "run_at", nullable = false)
    private Instant runAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private DigestRunStatus status;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    public DigestRun() {
    }

    public DigestRun(Channel channel, Instant runAt, DigestRunStatus status, String errorMessage) {
        this.channel = channel;
        this.runAt = runAt;
        this.status = status;
        this.errorMessage = errorMessage;
    }

    @PrePersist
    protected void onCreate() {
        if (this.runAt == null) {
            this.runAt = Instant.now();
        }
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Channel getChannel() {
        return channel;
    }

    public void setChannel(Channel channel) {
        this.channel = channel;
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
}
