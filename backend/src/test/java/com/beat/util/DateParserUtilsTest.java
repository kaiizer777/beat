package com.beat.util;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.junit.jupiter.api.Assertions.*;

class DateParserUtilsTest {

    @Test
    void testParseIso8601() {
        String isoDate = "2026-08-10T14:00:00Z";
        Instant result = DateParserUtils.parseInstantOrNull(isoDate);
        assertNotNull(result);
        assertEquals(Instant.parse(isoDate), result);
    }

    @Test
    void testParseRelativeHoursAgo() {
        String relativeDate = "3 hours ago";
        Instant result = DateParserUtils.parseInstantOrNull(relativeDate);
        assertNotNull(result);
        
        Instant expected = Instant.now().minus(3, ChronoUnit.HOURS);
        // Assert they are within a second of each other to account for execution time difference
        assertTrue(Math.abs(expected.toEpochMilli() - result.toEpochMilli()) < 1000);
    }

    @Test
    void testParseRelativeMinutesAgo() {
        String relativeDate = "45 mins ago";
        Instant result = DateParserUtils.parseInstantOrNull(relativeDate);
        assertNotNull(result);
        
        Instant expected = Instant.now().minus(45, ChronoUnit.MINUTES);
        assertTrue(Math.abs(expected.toEpochMilli() - result.toEpochMilli()) < 1000);
    }

    @Test
    void testParseYesterday() {
        String yesterday = "Yesterday";
        Instant result = DateParserUtils.parseInstantOrNull(yesterday);
        assertNotNull(result);
        
        Instant expected = Instant.now().minus(1, ChronoUnit.DAYS);
        assertTrue(Math.abs(expected.toEpochMilli() - result.toEpochMilli()) < 1000);
    }

    @Test
    void testParseLocaleFormat() {
        String localeDate = "Aug 10, 2026";
        Instant result = DateParserUtils.parseInstantOrNull(localeDate);
        assertNotNull(result);
        assertEquals(Instant.parse("2026-08-10T00:00:00Z"), result);
    }

    @Test
    void testParseLocaleFormatSlash() {
        String localeDate = "08/10/2026";
        Instant result = DateParserUtils.parseInstantOrNull(localeDate);
        assertNotNull(result);
        assertEquals(Instant.parse("2026-08-10T00:00:00Z"), result);
    }

    @Test
    void testParseNullOrEmpty() {
        assertNull(DateParserUtils.parseInstantOrNull(null));
        assertNull(DateParserUtils.parseInstantOrNull(""));
        assertNull(DateParserUtils.parseInstantOrNull("   "));
    }

    @Test
    void testParseYearlessMonthDay() {
        String yearlessDate = "Aug 25";
        Instant result = DateParserUtils.parseInstantOrNull(yearlessDate);
        assertNotNull(result);
        int currentYear = java.time.Year.now().getValue();
        assertEquals(Instant.parse(currentYear + "-08-25T00:00:00Z"), result);
    }

    @Test
    void testParseYearlessSingleDigitDay() {
        String yearlessDate = "Aug 5";
        Instant result = DateParserUtils.parseInstantOrNull(yearlessDate);
        assertNotNull(result);
        int currentYear = java.time.Year.now().getValue();
        assertEquals(Instant.parse(currentYear + "-08-05T00:00:00Z"), result);
    }

    @Test
    void testParseYearlessAug28Variants() {
        int currentYear = java.time.Year.now().getValue();
        Instant expected = Instant.parse(currentYear + "-08-28T00:00:00Z");

        assertEquals(expected, DateParserUtils.parseInstantOrNull("Aug 28"));
        assertEquals(expected, DateParserUtils.parseInstantOrNull("August 28"));
        assertEquals(expected, DateParserUtils.parseInstantOrNull("Aug. 28"));
        assertEquals(expected, DateParserUtils.parseInstantOrNull("28 Aug"));
        assertEquals(expected, DateParserUtils.parseInstantOrNull("28 August"));
    }

    @Test
    void testParseFullYearVariants() {
        Instant expected = Instant.parse("2026-08-28T00:00:00Z");

        assertEquals(expected, DateParserUtils.parseInstantOrNull("Aug 28, 2026"));
        assertEquals(expected, DateParserUtils.parseInstantOrNull("August 28, 2026"));
        assertEquals(expected, DateParserUtils.parseInstantOrNull("Aug. 28, 2026"));
        assertEquals(expected, DateParserUtils.parseInstantOrNull("28 Aug 2026"));
        assertEquals(expected, DateParserUtils.parseInstantOrNull("28 August 2026"));
    }

    @Test
    void testParseInvalidFormat() {
        assertNull(DateParserUtils.parseInstantOrNull("Unknown date format"));
        assertNull(DateParserUtils.parseInstantOrNull("next week"));
    }
}
