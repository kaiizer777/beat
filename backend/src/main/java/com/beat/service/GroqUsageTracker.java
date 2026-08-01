package com.beat.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicInteger;

@Component
public class GroqUsageTracker {

    private static final Logger log = LoggerFactory.getLogger(GroqUsageTracker.class);
    public static final int DAILY_CEILING = 1000;
    public static final int WARNING_THRESHOLD = 800;

    private LocalDate currentDate = LocalDate.now(ZoneOffset.UTC);
    private final AtomicInteger dailyCallCount = new AtomicInteger(0);

    public synchronized int recordCall() {
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        if (!today.equals(currentDate)) {
            log.info("[GROQ TRACKER] Resetting daily Groq call counter for new UTC date: {}. Previous count was {}.", today, dailyCallCount.get());
            currentDate = today;
            dailyCallCount.set(0);
        }

        int count = dailyCallCount.incrementAndGet();
        log.info("[GROQ TRACKER] Groq API call recorded. Daily count for UTC date {}: {}/{}", currentDate, count, DAILY_CEILING);

        if (count >= WARNING_THRESHOLD) {
            log.warn("[GROQ TRACKER WARNING] Daily Groq API usage has reached {}/{} calls for UTC date {}! Approaching 1,000 RPD ceiling.",
                    count, DAILY_CEILING, currentDate);
        }

        return count;
    }

    public synchronized int getDailyCallCount() {
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        if (!today.equals(currentDate)) {
            currentDate = today;
            dailyCallCount.set(0);
        }
        return dailyCallCount.get();
    }

    public synchronized LocalDate getCurrentDate() {
        return currentDate;
    }
}
