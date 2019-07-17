package info.nightscout.androidaps.plugins.pump.medtronicESP;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.R;
import info.nightscout.androidaps.logging.L;
import info.nightscout.androidaps.plugins.general.overview.events.EventNewNotification;
import info.nightscout.androidaps.plugins.general.overview.notifications.Notification;
import info.nightscout.androidaps.plugins.treatments.TreatmentsPlugin;
import info.nightscout.androidaps.utils.SP;

/*
 *   Created by ldaug99 on 2019-02-17
 */

public class MedtronicPump {
    private static MedtronicPump instance = null;

    public static MedtronicPump getInstance() {
        if (instance == null) {
            instance = new MedtronicPump();
            updatePreferences();
        }
        return instance;
    }

    public static void resetInstance() {
        instance = null;
    }

    public static void prepareInstanceForNextWake() {
        boolean fatalError = MedtronicPump.getInstance().fatalError;
        boolean tempStatus = MedtronicPump.getInstance().isTempInProgress;
        long sleepTime = MedtronicPump.getInstance().sleepStartTime;
        instance = null;
        MedtronicPump.getInstance().fatalError = fatalError;
        MedtronicPump.getInstance().isTempInProgress = tempStatus;
        MedtronicPump.getInstance().sleepStartTime = sleepTime;
    }

    // Device status indicators
    public static final char STATE_STARTING_SCAN = 'W';
    public static final char STATE_SCANNING = 'S';
    public static final char STATE_STARTING_SCAN = 'W';

    // Event action indicators
    public static final char EVENT_FAILED = 'F'; // Failed to connect to device
    public static final char EVENT_SLEEPING = 'S'; // Device is sleeping
    public static final char EVENT_CONNECTING = 'C'; // Connecting to device
    public static final char EVENT_CONNECTED = 'L'; // Linked to device, connected
    public static final char EVENT_SCAN = 'B'; // Scan status changed

    // BLE services and characteristics
    public static final String ESP_UUID_SERVICE = "27652cbb-76f0-45eb-bc37-826ca7315457";
    public static final String ESP_UUID_CHARACTERISTIC_TX = "8983c612-5b25-43cd-85f0-391f8dd3cb67";
    public static final String ESP_UUID_CHARACTERISTIC_RX = "848909c1-a6f0-4fa4-ac2a-06b9a9d4eb60";

    // ESP to AndroidAPS command indicators
    public static final char ESP_BATTERY = 'e';
    public static final char ESP_BOLUS = 'b';
    public static final char ESP_TEMP = 't';
    public static final char ESP_SLEEP = 's';

    // AndroidAPS to ESP command indicators
    public static final char ANDROID_PING = 'P';
    public static final char ANDROID_BOLUS = 'B';
    public static final char ANDROID_TEMP = 'T';
    public static final char ANDROID_WAKE = 'W';
    public static final char ANDROID_SLEEP = 'S';

    public static final String NEW_INBOUND_MESSAGE = "NEW_BLUETOOTH_IN"; // TODO: fix
    public static final String NEW_OUTBOUND_MESSAGE = "NEW_BLUETOOTH_OUT"; // TODO: fix
    public static final String NEW_TREATMENT = "NEW_TREATMENT"; // TODO: fix

    public static final String NEW_BT_MESSAGE = "NEW_BLUETOOTH_MESSAGE"; // TODO: fix
    public static final String NEW_BT_COMMAND = "NEW_COMMAND_ACTION"; // TODO: fix

    public static final String BT_COMM_SEND = "COMMAND_SEND"; // TODO: fix
    public static final String BT_COMM_CONFIRMED = "COMMAND_CONFIRMED"; // TODO: fix

    public static final String deviceName =  "MedESP"; // Device name to scan for with BLE TODO: Remove

    // Defined constants
    public static final int scanAlarmThreshold = 5; // Scan time without finding device before alerting user.
    public static final int connectionAttemptThreshold = 10; // Connection attempts before altering user.
    public static final int commandRetryThreshold = 10; // Number of command retries before alerting user.
    public static final long timeIntervalBetweenCommands = 500; // Time in milliseconds before retrying last command.
    public static final long bolusAndTempSetDelay = 1000; // Time in milliseconds before retrying last command.

    // Preference defined variables
    public int wakeInterval = 1; // Wake interval
    public boolean isFakingConnection = false; // If true, run plugin as normal, but without ESP connection.
    public boolean isUsingExtendedBolus = false; // If true, use extended bolus.
    public String pumpPassword = null; // Password for pump.
    public boolean isUploadingToNS = false; // If true, upload commands send to NS and conformations of commands.

    // Connection phases
    public boolean fatalError = false; // True when a fatal error occurred.
    public int connectPhase = 0;
    /*
    * Connection phase diagram
    * State | Meaning
    *   0   | Waiting to start scan
    *   1   | Scanning for device
    *   2   | Found device, connecting
    *   3   | Connected, discovering service
    *   4   | Discovered service, setting commands
     */


    // Phase 1 - connectPhase = 1 -> Is scanning. connectPhase = 2 -> Device found.
    public boolean isScanning = false; // True when scanning for devices.
    public long scanStartTime; // Scan start time.
    //public boolean isDeviceFound = false; // True when device with correct deviceName is found.

    // Phase 2 - connectPhase = 3 -> Is connecting. connectPhase = 4 -> Connected.
    //public boolean isConnecting = false; // True when waiting for connection to device.
    public int connectionAttempts; // Attempts at connection to device.
    //public boolean isConnected = false; // True when connection has been established with device.

    // Phase 3 - connectPhase = 5 -> Found service and characteristic.
    public boolean isServiceAndCharacteristicFound = false; // True, when device broadcasts characteristics and service.

    // Phase 4 - connectPhase = 6 -> Setting commands.
    public int actionState = 0; // Current action state. Specifies what command to send next.
    public int commandRetries = 0; // Number of times the same command has been send to the device.
    public long lastCommandTime = 0; // Time of last send command.
    public boolean responseRecieved = false; // True, when device responded to last command.

    // Phase 4.1 - Ping and status
    public int batteryRemaining = 50;
    public double reservoirRemainingUnits = 50;

    // Phase 4.2 - Bolus
    public double bolusToDeliver; // Bolus amount to deliver.
    public boolean newBolusAction = false; // True, when bolus needs to be delivered.

    // Phase 4.3 - Temp
    public boolean isTempInProgress = false; // True when temp is in progress.
    public double baseBasal; // Base basal rate.
    public double tempBasal; // Temp basal rate to be set.
    public int tempBasalDuration; // Temp duration to be set.
    public boolean newTempAction = false; // True, when new temp action.
    public boolean cancelCurrentTemp = false; // True, when the current temp needs to be cancelled.

    // Phase 4.4 - Sleep
    public long sleepStartTime = 0; // Time when device began sleeping.

    // Increments per bolus and basal step
    public double bolusStep = 0.1;
    public double basalStep = 0.1;

    public static boolean isPasswordSet() {
        return MedtronicPump.getInstance().pumpPassword != null;
    }

    public static void updatePreferences() {
        updateWakeIntervalFromPref();
        updateExtBolusFromPref();
        updateFakeFromPref();
        updatePassFromPref();
        updateNSFromPref();
    }

    private static void updateWakeIntervalFromPref() {
        MedtronicPump pump = MedtronicPump.getInstance();
        int previousValue = pump.wakeInterval;
        int wakeInterval = SP.getInt(R.string.key_medtronicESP_wakeinterval, 1);
        /* //TODO: Gives: "Attempt to invoke virtual method 'android.content.res.Resources android.content.Context.getResources()' on a null object reference" why?
        int maxInterval = this.getResources().getInteger(R.integer.ESP_max_sleep_interval);
        int minInterval = this.getResources().getInteger(R.integer.ESP_min_sleep_interval);
        if (wakeInterval != previousValue) {
            if (wakeInterval > maxInterval) wakeInterval = maxInterval;
            if (wakeInterval < minInterval) wakeInterval = minInterval;
            pump.wakeInterval = wakeInterval;
        }
        */
        if (wakeInterval != previousValue) {
            if (wakeInterval > 5) wakeInterval = 5;
            if (wakeInterval < 1) wakeInterval = 1;
            pump.wakeInterval = wakeInterval;
        }
    }

    private static void updateExtBolusFromPref() {
        MedtronicPump pump = MedtronicPump.getInstance();
        boolean previousValue = pump.isUsingExtendedBolus;
        pump.isUsingExtendedBolus = SP.getBoolean(R.string.key_medtronicESP_useextended, false);
        if (pump.isUsingExtendedBolus != previousValue &&
                TreatmentsPlugin.getPlugin().isInHistoryExtendedBoluslInProgress()) {
            //extendedBolusStop();
        }
    }

    private static void updateFakeFromPref() {
        MedtronicPump pump = MedtronicPump.getInstance();
        boolean previousValue = pump.isFakingConnection;
        pump.isFakingConnection = SP.getBoolean(R.string.key_medtronicESP_fake, false);
        if (pump.isFakingConnection != previousValue) {
            if (!pump.isFakingConnection) {
                //connectESP();
            } else {
                //disconnectESP();
            }
        }
    }

    private static void updatePassFromPref() {
        MedtronicPump pump = MedtronicPump.getInstance();
        String new_password = SP.getString(R.string.key_medtronicESP_password, null);
        if (new_password != null && !new_password.equals(pump.pumpPassword)) {
            pump.pumpPassword = new_password;
            //connectESP();
        }
    }

    private static void updateNSFromPref() {
        MedtronicPump.getInstance().isUploadingToNS =
                SP.getBoolean(R.string.key_medtronicESP_uploadNS, false);
    }
}