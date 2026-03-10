package me.wattguy.timetracker.util;

public class TimeUtil {
    public static String formatDuration(long totalSeconds) {
        long hours = totalSeconds / 3600;
        long minutes = (totalSeconds % 3600) / 60;
        long secs = totalSeconds % 60;
        return String.format("%02d:%02d:%02d", hours, minutes, secs);
    }

    public static String formatSmartDuration(long totalSeconds) {
        if (totalSeconds < 60) return totalSeconds + "с";
        if (totalSeconds < 3600) return String.format("%.1fм", totalSeconds / 60.0);
        return String.format("%.1fч", totalSeconds / 3600.0);
    }
}