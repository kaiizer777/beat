const FALLBACK_TIMEZONES = [
  "Asia/Kolkata",
  "UTC",
  "America/New_York",
  "America/Chicago",
  "America/Denver",
  "America/Los_Angeles",
  "America/Toronto",
  "Europe/London",
  "Europe/Paris",
  "Europe/Berlin",
  "Europe/Amsterdam",
  "Europe/Zurich",
  "Asia/Tokyo",
  "Asia/Shanghai",
  "Asia/Singapore",
  "Asia/Dubai",
  "Asia/Hong_Kong",
  "Australia/Sydney",
  "Australia/Melbourne",
  "Pacific/Auckland",
];

export function getAvailableTimezones(): string[] {
  try {
    if (typeof Intl !== "undefined" && "supportedValuesOf" in Intl) {
      // @ts-ignore supportedValuesOf exists in modern environments
      const supported = Intl.supportedValuesOf("timeZone") as string[];
      if (supported && supported.length > 0) {
        return supported;
      }
    }
  } catch (e) {
    // Fall back if unsupported
  }
  return FALLBACK_TIMEZONES;
}

export function searchTimezones(query: string, allZones: string[] = getAvailableTimezones()): string[] {
  if (!query || !query.trim()) {
    return allZones.slice(0, 50); // Show top 50 by default
  }
  const q = query.toLowerCase().trim();
  return allZones.filter((tz) => tz.toLowerCase().includes(q));
}
