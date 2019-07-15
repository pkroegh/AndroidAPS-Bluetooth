package info.nightscout.androidaps.plugins.pump.medtronicESP;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import info.nightscout.androidaps.logging.L;

/*
 *   Created by ldaug99 on 2019-02-17
 */

public class MedtronicPump {
    private static MedtronicPump instance = null;

    public static MedtronicPump getInstance() {
        if (instance == null) instance = new MedtronicPump();
        return instance;
    }

    public synchronized boolean getScanning() {
        return isScanning;
    }
    public synchronized void setScanning(boolean state) {
        isScanning = state;
    }

    public synchronized boolean getConnecting() {
        return isConnecting;
    }
    public synchronized void setConnecting(boolean state) {
        isConnecting = state;
    }

    public synchronized boolean getConnected() {
        return isConnected;
    }
    public synchronized void setConnected(boolean state) {
        isConnected = state;
    }

    public synchronized boolean getSleeping() {
        return isSleeping;
    }
    public synchronized void setSleeping(boolean state) {
        isSleeping = state;
    }

    public static final String ESP_UUID_SERVICE = "27652cbb-76f0-45eb-bc37-826ca7315457";
    public static final String ESP_UUID_CHARACTERISTIC_TX = "8983c612-5b25-43cd-85f0-391f8dd3cb67";
    public static final String ESP_UUID_CHARACTERISTIC_RX = "848909c1-a6f0-4fa4-ac2a-06b9a9d4eb60";

    public static final char ESP_BATTERY = 'e';
    public static final char ESP_BOLUS = 'b';
    public static final char ESP_TEMP = 't';
    public static final char ESP_SLEEP = 's';

    public static final char ANDROID_PING = 'P';
    public static final char ANDROID_BOLUS = 'B';
    public static final char ANDROID_TEMP = 'T';
    public static final char ANDROID_WAKE = 'W';
    public static final char ANDROID_SLEEP = 'S';

    public static final String NEW_INBOUND_MESSAGE = "NEW_BLUETOOTH_IN";
    public static final String NEW_OUTBOUND_MESSAGE = "NEW_BLUETOOTH_OUT";
    public static final String NEW_TREATMENT = "NEW_TREATMENT";

    public static final String NEW_BT_MESSAGE = "NEW_BLUETOOTH_MESSAGE";
    public static final String NEW_BT_COMMAND = "NEW_COMMAND_ACTION";

    public static final String BT_COMM_SEND = "COMMAND_SEND";
    public static final String BT_COMM_CONFIRMED = "COMMAND_CONFIRMED";

    // Preference defined variables
    public boolean isFakingConnection = false; // If true, run plugin as normal, but without ESP connection
    public boolean isUsingExtendedBolus = false; // If true, use extended bolus
    public boolean isUploadingToNS = false; // If true, upload commands send to NS and conformations of commands
    public String pump_password = null;

    public String mDevName =  "MedESP"; //TODO: Remove

    private boolean isScanning = false; // True when scanning for devices
    private boolean isConnecting = false; // True when waiting for connection to device
    private boolean isConnected = false; // True when connection has been established with device
    private boolean isSleeping = false; // True when the pump is sleeping

    public boolean failedToConnect = false; // True when failed to connect to device, will trigger another connection attempt
    public int connectionAttempts = 0; // Connection attempts between successful connections
    public long lastMessageTime = 0; // Time of last message (used to calculate when to run scan after sleep)

    public double reservoirRemainingUnits = 50;
    public int batteryRemaining = 50;
    public double baseBasal;

    public int mActionState = 0;

    public int wakeInterval = 1;
    public boolean isWakeOk = false; // True, when wake interval in pump matches preferences

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

    // Event action indicators
    public static final char EVENT_FAILED = 'F'; // Failed to connect to device
    public static final char EVENT_SLEEPING = 'S'; // Device is sleeping
    public static final char EVENT_CONNECTING = 'C'; // Connecting to device
    public static final char EVENT_CONNECTED = 'L'; // Linked to device, connected
    public static final char EVENT_SCAN = 'B'; // Scan status changed


}