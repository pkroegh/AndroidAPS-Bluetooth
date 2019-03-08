package info.nightscout.androidaps.plugins.pump.medtronicESP;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import info.nightscout.androidaps.logging.L;

/*
 *   Created by ldaug99 on 2019-02-17
 */

public class MedtronicPump {
    private static Logger log = LoggerFactory.getLogger(L.PUMP);

    private static MedtronicPump instance = null;

    public static MedtronicPump getInstance() {
        if (instance == null) instance = new MedtronicPump();
        return instance;
    }

    public static void reset() {
        log.debug("MedtronicPump reset");
        instance = null;
    }

    public static final String ESP_BATT = "e";
    public static final String ESP_WAKE = "w";
    public static final String ESP_TEMP = "t";
    public static final String ESP_SLEEP = "s";

    public static final String ANDROID_PING = "P";
    public static final String ANDROID_TEMP = "T=";
    public static final String ANDROID_WAKE = "W=";
    public static final String ANDROID_SLEEP = "S";

    public static final String NEW_BT_MESSAGE = "NEW_BLUETOOTH_MESSAGE";

    public String mDevName;

    public boolean mantainingConnection = false;
    public boolean mDeviceSleeping = false;
    public boolean readyForNextMessage = false;
    public boolean isNewPump = true;
    public int wakeInterval = 0;
    public long lastConnection = 0;

    public double reservoirRemainingUnits = 50;
    public int batteryRemaining = 50;

    public double baseBasal;
    public boolean newTemp = false;
    public boolean cancelTemp = false;
    public boolean isTempBasalInProgress;
    public double tempBasal;
    public int tempBasalDuration;

    public double bolusStep = 0.1;
    public double basalStep = 0.1;
}
