package com.trendpulse.lib;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

public class TimeUtils {
    
    /**
     * Rounds down a timestamp to the start of its time window.
     *
     * @param timestamp The Instant to round down
     * @param windowMinutes Size of the time window in minutes
     * @return Instant representing the start of the time window
     * 
     * Example:
     * Input: 2024-01-01T01:17:00Z with windowMinutes=15
     * Output: 2024-01-01T01:15:00Z (rounds down to nearest 15 min window)
     */
    public static Instant timestampToWindowStart(Instant timestamp, int windowMinutes) {
        // Convert to LocalDateTime to easily work with hours and minutes
        LocalDateTime dateTime = LocalDateTime.ofInstant(timestamp, ZoneId.systemDefault());
        
        int minutesSinceMidnight = dateTime.getHour() * 60 + dateTime.getMinute();
        int windowStartMinutes = (minutesSinceMidnight / windowMinutes) * windowMinutes;
        
        // Create new LocalDateTime with rounded down minutes
        LocalDateTime windowStart = dateTime
            .withHour(windowStartMinutes / 60)
            .withMinute(windowStartMinutes % 60)
            .withSecond(0)
            .withNano(0);
            
        // Convert back to Instant
        return windowStart.atZone(ZoneId.systemDefault()).toInstant();
    }
}