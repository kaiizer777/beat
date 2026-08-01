package com.beat.service;

import com.beat.entity.Channel;
import com.beat.repository.ChannelRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.stereotype.Service;

import com.beat.entity.DigestRun;
import com.beat.entity.DigestRunStatus;
import com.beat.repository.DigestRunRepository;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;

@Service
public class DynamicSchedulerService {

    private static final Logger log = LoggerFactory.getLogger(DynamicSchedulerService.class);

    private final ThreadPoolTaskScheduler taskScheduler;
    private final ChannelRepository channelRepository;
    private final DigestRunRepository digestRunRepository;
    private final DigestPipelineService digestPipelineService;

    private final Map<Long, ScheduledFuture<?>> scheduledTasks = new ConcurrentHashMap<>();
    private final Set<Long> runningChannelIds = ConcurrentHashMap.newKeySet();

    public DynamicSchedulerService(ThreadPoolTaskScheduler taskScheduler,
                                  ChannelRepository channelRepository,
                                  DigestRunRepository digestRunRepository,
                                  DigestPipelineService digestPipelineService) {
        this.taskScheduler = taskScheduler;
        this.channelRepository = channelRepository;
        this.digestRunRepository = digestRunRepository;
        this.digestPipelineService = digestPipelineService;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void initSchedules() {
        // A8 fix: recover any PENDING runs left by a crashed/killed JVM
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

        log.info("Initializing dynamic schedules for active channels on application startup...");
        List<Channel> activeChannels = channelRepository.findByIsActiveTrue();
        for (Channel channel : activeChannels) {
            scheduleChannel(channel);
        }
        log.info("Initialized dynamic schedules for {} active channels.", activeChannels.size());
    }

    public synchronized void scheduleChannel(Channel channel) {
        if (channel == null || channel.getId() == null) {
            return;
        }

        unscheduleChannel(channel.getId());

        if (!Boolean.TRUE.equals(channel.getIsActive())) {
            log.info("Channel ID {} ('{}') is inactive. Not scheduling.", channel.getId(), channel.getName());
            return;
        }

        try {
            ChannelDailyTrigger trigger = new ChannelDailyTrigger(channel.getCronTime(), channel.getTimezone());
            Runnable task = () -> executeChannelPipeline(channel.getId());

            ScheduledFuture<?> future = taskScheduler.schedule(task, trigger);
            scheduledTasks.put(channel.getId(), future);

            log.info("Successfully scheduled Channel ID: {} ('{}') for daily execution at {} [{}].",
                    channel.getId(), channel.getName(), channel.getCronTime(), channel.getTimezone());
        } catch (Exception e) {
            log.error("Failed to schedule Channel ID {}: {}", channel.getId(), e.getMessage(), e);
        }
    }

    public synchronized void unscheduleChannel(Long channelId) {
        if (channelId == null) {
            return;
        }
        ScheduledFuture<?> future = scheduledTasks.remove(channelId);
        if (future != null) {
            future.cancel(false);
            log.info("Unscheduled task for Channel ID: {}.", channelId);
        }
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

        // Wait for the async task to create the PENDING digest_run row in the DB.
        // Retry up to 5 times with 300ms intervals (1.5s total max wait).
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
