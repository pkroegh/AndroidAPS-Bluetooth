package info.nightscout.androidaps.plugins.pump.medtronicESP;

import info.nightscout.androidaps.R;
import info.nightscout.androidaps.plugins.treatments.TreatmentsPlugin;
import info.nightscout.androidaps.utils.SP;

/*
 *   Created by ldaug99 on 2019-02-17
 */

public class MedtronicESPPump {
    private static MedtronicESPPump instance = null;

    public static synchronized MedtronicESPPump getInstance() {
        if (instance == null) {
            instance = new MedtronicESPPump();
            updatePreferences();
        }
        return instance;
    }

    public static void clearInstance() {
        MedtronicESPPump pump = MedtronicESPPump.getInstance();
        pump.fatalError = false;
        pump.connectPhase = 0;
        pump.scanStartTime = 0;
        pump.connectionAttempts = 0;
        pump.actionState = 0;
        pump.commandRetries = 0;
        pump.lastCommandTime = 0;
        pump.responseRecieved = false;
        pump.newBolusAction = false;
        pump.expectingTempUpdate = false;
        pump.sleepStartTime = 0;
    }

    /*
    public static void resetInstance() {
        MedtronicESPPump pump = MedtronicESPPump.getInstance();
        pump.fatalError = false;
        // Keep connection attempts


        instance = null;
    }

    public static void prepareInstanceForNextWake() {
        // Keep sleepStartTime

        int wakeInterval = MedtronicESPPump.getInstance().wakeInterval;
        boolean fatalError = MedtronicESPPump.getInstance().fatalError;
        boolean tempStatus = MedtronicESPPump.getInstance().isTempInProgress;
        long sleepTime = MedtronicESPPump.getInstance().sleepStartTime;
        double baseBasal = MedtronicESPPump.getInstance().baseBasal;
        double tempBasal = MedtronicESPPump.getInstance().tempBasal;
        int tempBasalDuration = MedtronicESPPump.getInstance().tempBasalDuration;
        instance = null;
        MedtronicESPPump.getInstance().wakeInterval = wakeInterval;
        MedtronicESPPump.getInstance().fatalError = fatalError;
        MedtronicESPPump.getInstance().isTempInProgress = tempStatus;
        MedtronicESPPump.getInstance().sleepStartTime = sleepTime;
        MedtronicESPPump.getInstance().baseBasal = baseBasal;
        MedtronicESPPump.getInstance().tempBasal = tempBasal;
        MedtronicESPPump.getInstance().tempBasalDuration = tempBasalDuration;
    }
    */

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
    public static final char ANDROID_END = '!';

    /*
    public static final String NEW_INBOUND_MESSAGE = "NEW_BLUETOOTH_IN"; // TODO: fix
    public static final String NEW_OUTBOUND_MESSAGE = "NEW_BLUETOOTH_OUT"; // TODO: fix
    public static final String NEW_TREATMENT = "NEW_TREATMENT"; // TODO: fix

    public static final String NEW_BT_MESSAGE = "NEW_BLUETOOTH_MESSAGE"; // TODO: fix
    public static final String NEW_BT_COMMAND = "NEW_COMMAND_ACTION"; // TODO: fix

    public static final String BT_COMM_SEND = "COMMAND_SEND"; // TODO: fix
    public static final String BT_COMM_CONFIRMED = "COMMAND_CONFIRMED"; // TODO: fix
    */

    public static final String deviceName =  "MedESP"; // Device name to scan for with BLE TODO: Remove

    // Defined constants
    public static final int scanAlarmThreshold = 10; // Scan time without finding device before alerting user.
    public static final int connectionAttemptThreshold = 10; // Connection attempts before altering user.
    public static final int commandRetryThreshold = 10; // Number of command retries before alerting user.
    public static final long timeIntervalBetweenCommands = 500; // Time in milliseconds before retrying last command.

    public static final double pumpBolusStep = 0.05;
    public static final double pumpBasalStep = 0.025;
    public static final int pumpDurationStep = 30;
    public static final int pumpButtonPressTime = 50; // Press time in milliseconds.
    public static final int pumpButtonPressDleay = 200; // Time between button press in milliseconds.
    public static final long delayError = 15000; // Additional time to make sure temp or bolus is set.
    public static final long tempNullDelay = 25000; // Additional time to make sure temp or bolus is set.

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
    //public boolean isScanning = false; // True when scanning for devices.
    public long scanStartTime = 0; // Scan start time.
    //public boolean isDeviceFound = false; // True when device with correct deviceName is found.

    // Phase 2 - connectPhase = 3 -> Is connecting. connectPhase = 4 -> Connected.
    //public boolean isConnecting = false; // True when waiting for connection to device.
    public int connectionAttempts = 0; // Attempts at connection to device.
    //public boolean isConnected = false; // True when connection has been established with device.

    // Phase 3 - connectPhase = 5 -> Found service and characteristic.
    //public boolean isServiceAndCharacteristicFound = false; // True, when device broadcasts characteristics and service.

    // Phase 4 - connectPhase = 6 -> Setting commands.
    public int actionState = 0; // Current action state. Specifies what command to send next.
    public int commandRetries = 0; // Number of times the same command has been send to the device.
    public long lastCommandTime = 0; // Time of last send command.
    public boolean responseRecieved = false; // True, when device responded to last command.

    // Phase 4.1 - Ping and status
    public int batteryRemaining = 50;
    public double reservoirRemainingUnits = 50;

    // Phase 4.2 - Bolus
    public double bolusToDeliver = 0; // Bolus amount to deliver.
    public boolean newBolusAction = false; // True, when bolus needs to be delivered.

    // Phase 4.3 - Temp
    //public boolean isTempInProgress = false; // True when temp is in progress.
    public boolean expectingTempUpdate = false;
    public double baseBasal = 0; // Base basal rate.
    public double tempBasal = 0; // Temp basal rate to be set.
    public int tempBasalDuration = 0; // Temp duration to be set.
    public boolean cancelCurrentTemp = false; // True, when the current temp needs to be cancelled.

    // Phase 4.4 - Sleep
    public long sleepStartTime = 0; // Time when device began sleeping.

    // Increments per bolus and basal step
    public double bolusStep = 0.1;
    public double basalStep = 0.1;

    public static boolean isPasswordSet() {
        return MedtronicESPPump.getInstance().pumpPassword != null;
    }

    private static void updatePreferences() {
        updateExtBolusFromPref();
        updateFakeFromPref();
        updatePassFromPref();
        updateNSFromPref();
    }

    public static void updateWakeIntervalFromPref() {
        int previousValue = MedtronicESPPump.getInstance().wakeInterval;
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
            MedtronicESPPump.getInstance().wakeInterval = wakeInterval;
        }
    }

    public static boolean updateExtBolusFromPref() {
        boolean previousValue = MedtronicESPPump.getInstance().isUsingExtendedBolus;
        MedtronicESPPump.getInstance().isUsingExtendedBolus =
                SP.getBoolean(R.string.key_medtronicESP_useextended, false);
        return (MedtronicESPPump.getInstance().isUsingExtendedBolus != previousValue &&
                TreatmentsPlugin.getPlugin().isInHistoryExtendedBoluslInProgress());
    }

    public static boolean updateFakeFromPref() {
        boolean previousValue = MedtronicESPPump.getInstance().isFakingConnection;
        MedtronicESPPump.getInstance().isFakingConnection =
                SP.getBoolean(R.string.key_medtronicESP_fake, false);
        return (MedtronicESPPump.getInstance().isFakingConnection != previousValue);
    }

    public static boolean updatePassFromPref() {
        String new_password = SP.getString(R.string.key_medtronicESP_password, null);
        if (new_password != null && !new_password.equals(MedtronicESPPump.getInstance().pumpPassword)) {
            MedtronicESPPump.getInstance().pumpPassword = new_password;
            return true;
        }
        return false;
    }

    public static void updateNSFromPref() {
        MedtronicESPPump.getInstance().isUploadingToNS =
                SP.getBoolean(R.string.key_medtronicESP_uploadNS, false);
    }
}
