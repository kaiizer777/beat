package com.beat.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class GroqUsageTrackerTest {

    private GroqUsageTracker tracker;

    @BeforeEach
    void setUp() {
        tracker = new GroqUsageTracker();
    }

    @Test
    void testInitialCountIsZero() {
        assertEquals(0, tracker.getDailyCallCount());
        assertNotNull(tracker.getCurrentDate());
    }

    @Test
    void testRecordCallIncrementsCount() {
        int count1 = tracker.recordCall();
        assertEquals(1, count1);
        assertEquals(1, tracker.getDailyCallCount());

        int count2 = tracker.recordCall();
        assertEquals(2, count2);
        assertEquals(2, tracker.getDailyCallCount());
    }
}
