package info.nightscout.androidaps.plugins.pump.medtronicESP.utils;

import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.R;
import info.nightscout.androidaps.db.CareportalEvent;
import info.nightscout.androidaps.db.MedtronicActionHistory;
import info.nightscout.androidaps.plugins.general.nsclient.NSUpload;
import info.nightscout.androidaps.plugins.pump.medtronicESP.MedtronicPump;
import info.nightscout.androidaps.plugins.treatments.TreatmentsPlugin;
import info.nightscout.androidaps.utils.SP;

import static info.nightscout.androidaps.utils.DateUtil.now;

public class TimeUtil {
    public static final long minToMillisec = 60000;

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
}