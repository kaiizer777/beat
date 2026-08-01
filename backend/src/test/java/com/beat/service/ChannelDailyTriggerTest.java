package com.beat.service;

import org.junit.jupiter.api.Test;
import org.springframework.scheduling.TriggerContext;

import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ChannelDailyTriggerTest {

    @Test
    void testNextExecution_BeforeScheduledTimeToday() {
        LocalTime cronTime = LocalTime.of(14, 0);
        String timezone = "UTC";
        ChannelDailyTrigger trigger = new ChannelDailyTrigger(cronTime, timezone);

        TriggerContext context = mock(TriggerContext.class);
        when(context.lastScheduledExecutionTime()).thenReturn(null);

        Instant next = trigger.nextExecution(context);
        assertNotNull(next);
    }

    @Test
    void testNextExecution_AfterScheduledTimeToday() {
        LocalTime cronTime = LocalTime.of(8, 0);
        String timezone = "Asia/Kolkata";
        ChannelDailyTrigger trigger = new ChannelDailyTrigger(cronTime, timezone);

        TriggerContext context = mock(TriggerContext.class);
        Instant lastScheduledInstant = ZonedDateTime.of(2026, 8, 1, 8, 0, 0, 0, ZoneId.of("Asia/Kolkata")).toInstant();
        when(context.lastScheduledExecutionTime()).thenReturn(Date.from(lastScheduledInstant));

        Instant next = trigger.nextExecution(context);
        Instant expected = ZonedDateTime.of(2026, 8, 2, 8, 0, 0, 0, ZoneId.of("Asia/Kolkata")).toInstant();

        assertEquals(expected, next);
    }
}
