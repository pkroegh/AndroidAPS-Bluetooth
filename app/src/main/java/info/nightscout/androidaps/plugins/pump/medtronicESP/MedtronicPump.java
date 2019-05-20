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

    /*
    public static void reset() {
        log.debug("MedtronicPump reset");
        instance = null;
    }
    */

    public static final char ESP_BATTERY = 'e';
    public static final char ESP_WAKE = 'w';
    public static final char ESP_BOLUS = 'b';
    public static final char ESP_TEMP = 't';
    public static final char ESP_SLEEP = 's';

    public static final String ANDROID_PING = "P";
    public static final String ANDROID_BOLUS = "B=";
    public static final String ANDROID_TEMP = "T=";
    public static final String ANDROID_WAKE = "W=";
    public static final String ANDROID_SLEEP = "S";

    public static final String NEW_BT_MESSAGE = "NEW_BLUETOOTH_MESSAGE";

    public String mDevName;

    public String pump_password = "";

    public boolean isDeviceSleeping = false; // Is the pump sleeping
    public boolean isReadyForMessage = false; // Pump ready for next command
    public boolean loopHandshake = true; // Loop handshake on first connect and on failure
    public boolean failedToReconnect = false; // Pump failed to reconnect after wake
    public int wakeInterval = 1;
    public long lastConnection = 0;

    public double reservoirRemainingUnits = 50;
    public int batteryRemaining = 50;
    public double baseBasal;

    public boolean isSleepSendt = false; // True, when bolus is send to pump
    public boolean isSleepConfirmed = false; // True, when bolus is confirmed by pump

    public double bolusToDeliver;
    public boolean deliverBolus = false; // True, when a new bolus needs to be delivered
    public boolean isBolusSendt = false; // True, when bolus is send to pump
    public boolean isBolusConfirmed = false; // True, when bolus is confirmed by pump

    public double tempBasal;
    public int tempBasalDuration;
    public boolean newTempAction = false; // True, when new temp action
    public int tempAction = 0; // 0: invalid, 1: new temp rate to be set, 2: cancel current temp
    public boolean isTempInProgress = false; // Required to validate cancelTemp
    public boolean isTempActionSendt = false; // True, when temp action is send to pump
    public boolean isTempActionConfirmed = false; // True, when temp action is confirmed by pump

    public double bolusStep = 0.1;
    public double basalStep = 0.1;
}