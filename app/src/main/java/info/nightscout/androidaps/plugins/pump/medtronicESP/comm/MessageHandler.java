package info.nightscout.androidaps.plugins.pump.medtronicESP.comm;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;

import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.logging.L;
import info.nightscout.androidaps.plugins.pump.medtronicESP.MedtronicPump;
import info.nightscout.androidaps.plugins.pump.medtronicESP.events.EventESPStatusUpdate;
import info.nightscout.androidaps.plugins.pump.medtronicESP.services.MedtronicService;

public class MessageHandler {
    private static Logger log = LoggerFactory.getLogger(L.PUMPCOMM);

    public static void handleMessage(String message) {
        log.debug("Got message from IOThread: " + message);
        char action = message.charAt(0);
        log.debug("messageHandler on char: " + action);
        switch (action) {
            case MedtronicPump.ESP_WAKE: // ESP is handshaking
                log.debug("messageHandler on gotWake");
                gotWake(message);
                break;
            case MedtronicPump.ESP_BATTERY: // ESP battery status
                log.debug("messageHandler on gotBatteryStatus");
                gotBatteryStatus(message);
                break;
            case MedtronicPump.ESP_BOLUS: // ESP bolus status
                log.debug("messageHandler on gotBolusStatus");
                gotBolusStatus(message);
                break;
            case MedtronicPump.ESP_TEMP: // Current ESP temp status
                log.debug("messageHandler on gotTempStatus");
                gotTempStatus(message);
                break;
            case MedtronicPump.ESP_SLEEP: // ESP confirmed sleep
                log.debug("messageHandler on gotSleepOk");
                gotSleepOk();
                break;
            default:
                log.debug("messageHandler: Failed to interpret command");
        }
    }

    private static void gotWake(String message) {
        MedtronicPump pump = MedtronicPump.getInstance();
        confirmWakeInterval(message);
        if (pump.loopHandshake) {
            pump.loopHandshake = false;
        }
        pump.isDeviceSleeping = false;
        pump.isReadyForMessage = true; // Message processed, ready to continue
        pump.runCommandThread = true;
        MainApp.bus().post(new EventESPStatusUpdate()); // Update fragment, with new pump status
    }

    private static void confirmWakeInterval(String message) { // Check if pump wake interval matches preferences
        MedtronicPump pump = MedtronicPump.getInstance();
        Integer ESPWakeInterval = Integer.valueOf(message.substring(
                Character.toString(MedtronicPump.ANDROID_WAKE).length()+1,
                Character.toString(MedtronicPump.ANDROID_WAKE).length()+2));
        pump.isWakeOk = Objects.equals(ESPWakeInterval, pump.wakeInterval);
    }

    private static void gotBatteryStatus(String message) { // Pump send battery status
        MedtronicPump pump = MedtronicPump.getInstance();
        pump.batteryRemaining = Integer.valueOf(message.substring(
                Character.toString(MedtronicPump.ESP_BATTERY).length() + 1,
                Character.toString(MedtronicPump.ESP_BATTERY).length() + 4)); // TODO is this necessary??
        uploadToNS(MedtronicPump.BT_COMM_CONFIRMED, message);
        pump.isReadyForMessage = true; // Message processed, ready to continue
    }

    private static void gotBolusStatus(String message) {
        MedtronicPump pump = MedtronicPump.getInstance(); // TODO implement this



        pump.isBolusConfirmed = true;
        dbCommandConfirmed(MedtronicPump.ANDROID_BOLUS); // Marks command as confirmed
        uploadToNS(MedtronicPump.BT_COMM_CONFIRMED, message);
        pump.isReadyForMessage = true; // Message processed, ready to continue
    }

    private static void gotTempStatus(String message) { // Confirm pump temp basal status matches simulated status
        MedtronicPump pump = MedtronicPump.getInstance(); // TODO implement this


        //pump.isTempInProgress

        pump.isTempActionConfirmed = true;
        dbCommandConfirmed(MedtronicPump.ANDROID_TEMP); // Marks command as confirmed
        uploadToNS(MedtronicPump.BT_COMM_CONFIRMED, message);
        pump.isReadyForMessage = true; // Message processed, ready to continue
    }

    private static void gotSleepOk() { // Pump confirmed sleep command, pump is sleeping
        MedtronicPump pump = MedtronicPump.getInstance();
        pump.lastConnection = System.currentTimeMillis();
        pump.isSleepConfirmed = true;
        dbCommandConfirmed(MedtronicPump.ANDROID_SLEEP); // Marks command as confirmed
        uploadToNS(MedtronicPump.BT_COMM_CONFIRMED, Character.toString(MedtronicPump.ANDROID_SLEEP));
        MainApp.bus().post(new EventESPStatusUpdate()); // Update fragment, with new pump status
    }

    /* Upload event to NS */
    private static void uploadToNS(String uploadType, String command) {
        /*
        if (uploadCommandsToNS) {
            String note = uploadType + command;
            NSUpload.uploadEvent(CareportalEvent.NOTE, DateUtil.now(), note);
        }
        */
    }

    public static void dbCommandConfirmed(char command) {
        /*
        MedtronicActionHistory record = MainApp.getDbHelper().
                getMedtronicActionByCommand(command);
        if (record != null) {
            record.setCommandConfirmed();
        }
        MainApp.getDbHelper().createOrUpdate(record);
        */
    }
}
