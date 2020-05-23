package info.nightscout.androidaps.plugins.pump.medtronicESP.utils;

public class TimeUtil {
    private static final long minToMillisec = 60000;
    private static final double millisecToSec = 0.001;

    /**
     * Check if more then a given time has passed in minutes.
     *
     * @param lastTime   Time to compare with threshold.
     * @param threshold  Threshold.
     * @return True if time threshold has been passed.
     */
    public static boolean isTimeDiffLargerMin(long lastTime, int threshold) {
        return (System.currentTimeMillis() - lastTime) >= (threshold * minToMillisec);
    }

    /**
     * Check if more then a given time has passed in minutes.
     *
     * @param lastTime   Time to compare with threshold.
     * @param threshold  Threshold.
     * @return True if time threshold has been passed.
     */
    public static boolean isTimeDiffLargerMilli(long lastTime, long threshold) {
        return (System.currentTimeMillis() - lastTime) >= threshold;
    }

    /**
     * Get the elapsed time in seconds
     *
     * @param startTime  Starting time to get elapsed from.
     * @return Elapsed time from given time.
     */
    public static double elapsedTime(long startTime) {
        return ((System.currentTimeMillis() - startTime) * millisecToSec);
    }

    /**
     * Remaining time until a specified amount of time has passed.
     *
     * @param startTime  Time of event start.
     * @param threshold  Threshold in minutes.
     * @return The remaining time until threshold is reached.
     */
    public static double countdownTimer(long startTime, int threshold) {
        return (((threshold * minToMillisec) - (System.currentTimeMillis() - startTime)) *
                millisecToSec);
    }
}