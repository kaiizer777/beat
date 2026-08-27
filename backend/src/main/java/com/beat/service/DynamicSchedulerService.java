package com.beat.service;

import com.beat.entity.Channel;
import com.beat.entity.DigestRun;
import com.beat.entity.DigestRunStatus;
import com.beat.repository.ChannelRepository;
import com.beat.repository.DigestRunRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class DynamicSchedulerService {

    private static final Logger log = LoggerFactory.getLogger(DynamicSchedulerService.class);

    private final ChannelRepository channelRepository;
    private final DigestRunRepository digestRunRepository;
    private final DigestPipelineService digestPipelineService;

    private final Set<Long> runningChannelIds = ConcurrentHashMap.newKeySet();

    public DynamicSchedulerService(ChannelRepository channelRepository,
                                  DigestRunRepository digestRunRepository,
                                  DigestPipelineService digestPipelineService) {
        this.channelRepository = channelRepository;
        this.digestRunRepository = digestRunRepository;
        this.digestPipelineService = digestPipelineService;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void initSchedules() {
        // Recover any PENDING runs left by a crashed/killed JVM
        List<DigestRun> stuckRuns = digestRunRepository.findByStatus(DigestRunStatus.PENDING);
        if (!stuckRuns.isEmpty()) {
            log.warn("[STARTUP RECOVERY] Found {} PENDING digest_run(s) from a previous crash. Marking as FAILED.", stuckRuns.size());
            for (DigestRun stuckRun : stuckRuns) {
                stuckRun.setStatus(DigestRunStatus.FAILED);
                stuckRun.setErrorMessage("Run was interrupted: JVM was killed or crashed while this run was in progress.");
                digestRunRepository.save(stuckRun);
                log.warn("[STARTUP RECOVERY] Marked digest_run #{} (channel_id={}) as FAILED.", stuckRun.getId(), stuckRun.getChannel().getId());
            }
        }
        log.info("Stateless dynamic scheduler initialized. Pending channels will be polled via GitHub Actions or internal endpoint.");
    }

    public List<Long> processDueChannels() {
        Instant now = Instant.now();
        List<Channel> activeChannels = channelRepository.findByIsActiveTrue();
        List<Long> triggeredChannelIds = new ArrayList<>();

        log.info("Evaluating {} active channel(s) for due execution at {}...", activeChannels.size(), now);

        for (Channel channel : activeChannels) {
            if (isDue(channel, now)) {
                log.info("Channel ID {} ('{}') is due for execution. Executing pipeline...", channel.getId(), channel.getName());
                executeChannelPipeline(channel.getId());
                triggeredChannelIds.add(channel.getId());
            } else {
                log.debug("Channel ID {} ('{}') is not due at {}.", channel.getId(), channel.getName(), now);
            }
        }
        Instant cutoff = now.minus(Duration.ofMinutes(15));
        List<DigestRun> stuckRuns = digestRunRepository.findByStatus(DigestRunStatus.PENDING);
        for (DigestRun stuckRun : stuckRuns) {
            if (stuckRun.getRunAt().isBefore(cutoff)) {
                log.warn("[CRON RECOVERY] Found stuck PENDING run #{} older than 15m. Marking as FAILED.", stuckRun.getId());
                stuckRun.setStatus(DigestRunStatus.FAILED);
                stuckRun.setErrorMessage("Run timed out or was interrupted (recovered by cron).");
                digestRunRepository.save(stuckRun);
                runningChannelIds.remove(stuckRun.getChannel().getId());
            }
        }

        log.info("Finished processing due channels. Total triggered: {} / evaluated: {}", triggeredChannelIds.size(), activeChannels.size());
        return triggeredChannelIds;
    }

    public boolean isDue(Channel channel, Instant now) {
        if (channel == null || !Boolean.TRUE.equals(channel.getIsActive())) {
            return false;
        }
        if (channel.getCronTime() == null || channel.getTimezone() == null) {
            return false;
        }

        ZoneId zoneId;
        try {
            zoneId = ZoneId.of(channel.getTimezone());
        } catch (Exception e) {
            log.error("Invalid timezone '{}' for Channel ID {}", channel.getTimezone(), channel.getId());
            return false;
        }

        ZonedDateTime zdtNow = now.atZone(zoneId);
        ZonedDateTime todayScheduled = zdtNow.toLocalDate().atTime(channel.getCronTime()).atZone(zoneId);

        Instant mostRecentScheduled;
        if (todayScheduled.toInstant().isAfter(now)) {
            mostRecentScheduled = todayScheduled.minusDays(1).toInstant();
        } else {
            mostRecentScheduled = todayScheduled.toInstant();
        }

        Duration timeSinceScheduled = Duration.between(mostRecentScheduled, now);

        // Due if the scheduled instance is in the past and we haven't run it yet
        boolean isPastScheduled = !timeSinceScheduled.isNegative();

        // Overlap protection: check if channel has already run on or after the scheduled instance
        // lastRunAt is only advanced after successful pipeline execution (see executeChannelPipeline),
        // so a failed run will remain due on next evaluation and be retried.
        Instant lastRunAt = channel.getLastRunAt();
        boolean alreadyRan = (lastRunAt != null) && !lastRunAt.isBefore(mostRecentScheduled);

        return isPastScheduled && !alreadyRan;
    }

    public void executeChannelPipeline(Long channelId) {
        if (!runningChannelIds.add(channelId)) {
            log.warn("Execution skipped for Channel ID: {} - previous pipeline run is still in progress (overlap protection).", channelId);
            return;
        }

        try {
            Channel channel = channelRepository.findById(channelId).orElse(null);
            if (channel == null || !Boolean.TRUE.equals(channel.getIsActive())) {
                log.info("Channel ID {} is deleted or inactive. Skipping execution.", channelId);
                return;
            }

            log.info("Trigger fired for Channel ID: {} ('{}'). Executing digest pipeline.", channel.getId(), channel.getName());
            digestPipelineService.executeDigestPipeline(channel);
            // Only advance lastRunAt after successful pipeline execution so isDue() can retry on failure
            channel.setLastRunAt(Instant.now());
            channelRepository.save(channel);
        } catch (Exception e) {
            log.error("Error executing digest pipeline for Channel ID {}: {}", channelId, e.getMessage(), e);
        } finally {
            runningChannelIds.remove(channelId);
        }
    }

    public DigestRun triggerManualRun(Long channelId) {
        Channel channel = channelRepository.findById(channelId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Channel not found: " + channelId));

        if (runningChannelIds.contains(channelId)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "A digest run is already in progress for channel: " + channelId);
        }

        Optional<DigestRun> latestRunOpt = digestRunRepository.findTopByChannelIdOrderByRunAtDesc(channelId);
        if (latestRunOpt.isPresent() && latestRunOpt.get().getStatus() == DigestRunStatus.PENDING) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "A digest run is currently pending for channel: " + channelId);
        }

        log.info("Triggering manual digest run for Channel ID: {} ('{}')", channel.getId(), channel.getName());
        CompletableFuture.runAsync(() -> executeChannelPipeline(channelId));

        for (int attempt = 0; attempt < 5; attempt++) {
            try {
                Thread.sleep(300);
            } catch (InterruptedException ignored) {}
            Optional<DigestRun> runOpt = digestRunRepository.findTopByChannelIdOrderByRunAtDesc(channelId);
            if (runOpt.isPresent()) {
                return runOpt.get();
            }
        }
        throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                "Pipeline was triggered but the run record was not created in time for channel: " + channelId);
    }
}
