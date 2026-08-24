package com.beat.util;

import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class DateParserUtils {

    private static final Pattern RELATIVE_TIME_PATTERN = Pattern.compile("(?i)(\\d+)\\s+(minute|minutes|min|mins|hour|hours|hr|hrs|day|days|week|weeks|wk|wks|month|months)\\s+ago");
    private static final Pattern YESTERDAY_PATTERN = Pattern.compile("(?i)yesterday");

    private static final List<DateTimeFormatter> FORMATTERS = Arrays.asList(
            DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.ENGLISH),
            DateTimeFormatter.ofPattern("MMM dd, yyyy", Locale.ENGLISH),
            DateTimeFormatter.ofPattern("MM/dd/yyyy", Locale.ENGLISH),
            DateTimeFormatter.ofPattern("dd/MM/yyyy", Locale.ENGLISH),
            DateTimeFormatter.ofPattern("yyyy/MM/dd", Locale.ENGLISH),
            DateTimeFormatter.ofPattern("MM-dd-yyyy", Locale.ENGLISH),
            DateTimeFormatter.ofPattern("yyyy-MM-dd", Locale.ENGLISH)
    );

    public static Instant parseInstantOrNull(String rawDate) {
        if (rawDate == null || rawDate.isBlank()) {
            return null;
        }

        String cleaned = rawDate.trim();

        // 1. Try ISO-8601 parsing directly (handles "2026-08-10T14:00:00Z")
        try {
            return Instant.parse(cleaned);
        } catch (DateTimeParseException ignored) {
            // Not ISO-8601 with Z, continue
        }

        // 1b. Try ISO with numeric offset (handles "2026-08-10T14:00:00+05:30")
        try {
            return OffsetDateTime.parse(cleaned).toInstant();
        } catch (DateTimeParseException ignored) {
            // Not ISO with offset, continue
        }

        // 1c. Try RFC 1123 / HTTP-date (handles "Tue, 12 Aug 2026 14:00:00 GMT"),
        // commonly emitted by web APIs and search backends like TinyFish.
        try {
            return DateTimeFormatter.RFC_1123_DATE_TIME.parse(cleaned, Instant::from);
        } catch (DateTimeParseException ignored) {
            // Not RFC 1123, continue
        }

        // 2. Try Relative times
        Matcher relativeMatcher = RELATIVE_TIME_PATTERN.matcher(cleaned);
        if (relativeMatcher.find()) {
            try {
                int amount = Integer.parseInt(relativeMatcher.group(1));
                String unitStr = relativeMatcher.group(2).toLowerCase();

                ChronoUnit unit;
                if (unitStr.startsWith("min")) {
                    unit = ChronoUnit.MINUTES;
                } else if (unitStr.startsWith("h")) {
                    unit = ChronoUnit.HOURS;
                } else if (unitStr.startsWith("day")) {
                    unit = ChronoUnit.DAYS;
                } else if (unitStr.startsWith("w")) {
                    // weeks → convert to days
                    return Instant.now().minus(amount * 7L, ChronoUnit.DAYS);
                } else if (unitStr.startsWith("month")) {
                    // months → approximate as 30 days
                    return Instant.now().minus(amount * 30L, ChronoUnit.DAYS);
                } else {
                    return null;
                }

                return Instant.now().minus(amount, unit);
            } catch (NumberFormatException ignored) {
            }
        }

        if (YESTERDAY_PATTERN.matcher(cleaned).find()) {
            return Instant.now().minus(1, ChronoUnit.DAYS);
        }

        // 3. Try standard locale formats
        for (DateTimeFormatter formatter : FORMATTERS) {
            try {
                LocalDate date = LocalDate.parse(cleaned, formatter);
                return date.atStartOfDay(ZoneOffset.UTC).toInstant();
            } catch (DateTimeParseException ignored) {
            }
        }

        // Failed all attempts
        return null;
    }
}
