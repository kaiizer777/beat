package com.beat.service;

import org.springframework.scheduling.Trigger;
import org.springframework.scheduling.TriggerContext;

import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Date;

public class ChannelDailyTrigger implements Trigger {

    private final LocalTime cronTime;
    private final ZoneId zoneId;

    public ChannelDailyTrigger(LocalTime cronTime, String timezone) {
        if (cronTime == null) {
            throw new IllegalArgumentException("cronTime cannot be null");
        }
        if (timezone == null || timezone.isBlank()) {
            throw new IllegalArgumentException("timezone cannot be null or blank");
        }
        this.cronTime = cronTime;
        this.zoneId = ZoneId.of(timezone);
    }

    @Override
    public Instant nextExecution(TriggerContext triggerContext) {
        Date lastScheduledDate = triggerContext.lastScheduledExecutionTime();
        Instant lastScheduled = (lastScheduledDate != null) ? lastScheduledDate.toInstant() : null;
        Instant reference = (lastScheduled != null) ? lastScheduled : Instant.now();

        ZonedDateTime zdt = reference.atZone(zoneId);
        ZonedDateTime candidate = zdt.with(cronTime).withNano(0);

        if (!candidate.isAfter(zdt)) {
            candidate = candidate.plusDays(1);
        }

        return candidate.toInstant();
    }
}
