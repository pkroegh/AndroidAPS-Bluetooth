package info.nightscout.androidaps.plugins.pump.medtronicESP.services;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothProfile;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;
import java.util.UUID;

import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.plugins.pump.medtronicESP.MedtronicPump;
import info.nightscout.androidaps.plugins.pump.medtronicESP.events.EventStatusChanged;
import info.nightscout.androidaps.plugins.pump.medtronicESP.utils.TimeUtil;

public class BluetoothWorkerThread extends Thread {
    //private static Logger log = LoggerFactory.getLogger(L.PUMPBTCOMM);
    private Logger log = LoggerFactory.getLogger("Medtronic");

    private BluetoothAdapter mBluetoothAdapter;
    private BluetoothDevice mBluetoothDevice;
    private BluetoothGatt mBluetoothGatt;

    private BluetoothGattCharacteristic mOutputCharacteristic;
    private BluetoothGattCharacteristic mInputCharacteristic;

    private boolean mRunBluetoothThread = true;

    private int mSendMessageAttempts = 0;
    private int mConnectionAttempts = 0;

    private String mPassword;

    BluetoothWorkerThread(BluetoothDevice bluetoothDevice) {
        super();
        log.debug("Initializing BluetoothWorkerThread");
        mBluetoothDevice = bluetoothDevice;
        getPumpPassword();
        getBluetoothAdapter();
        this.setDaemon(true);
        this.start();
    }

    public boolean getRunConnectThread() {
        return mRunBluetoothThread;
    }

    public void setRunConnectThread(boolean runThread) {
        mRunBluetoothThread = runThread;
    }

    private void getBluetoothAdapter() {
        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if (mBluetoothAdapter == null || mBluetoothDevice == null) {
            log.error("Unable to get bluetooth adapter or device, stopping.");
            mRunBluetoothThread = false;
        }
    }

    private void getPumpPassword() {
        mPassword = MedtronicPump.getInstance().pump_password;
        if (mPassword == null) {
            mRunBluetoothThread = false;
        }
    }

    public boolean isRunning() {
        return mRunBluetoothThread;
    }

    @Override
    public final void run() {
        initializeThread();
        while (mRunBluetoothThread) {
            maintainConnection();
        }
        terminateThread();
    }

    private void initializeThread() {
        log.debug("Starting BluetoothWorkerThread");
        connect();
    }

    private void terminateThread() {
        if (MedtronicPump.getInstance().mActionState != 0) {
            MedtronicPump.getInstance().mActionState = 0;
        }
        disconnect();
        try {
            System.runFinalization();
        } catch (Exception e) {log.error("Thread exception: " + e);}
        log.debug("Stopping BluetoothWorkerThread");
    }

    private void maintainConnection() {
        /*
        if (MedtronicPump.getInstance().getConnected() && isTimedout()) {
            log.debug("Communication has timed out, stopping BluetoothWorkerThread");
            mRunBluetoothThread = false;
        }
        */
    }

    private boolean isTimedout() {
        MedtronicPump pump = MedtronicPump.getInstance();
        return TimeUtil.isTimeDiffLargerMilli(pump.lastMessageTime,
                10000);
    }

    private void connect() {
        if (mBluetoothDevice != null) {
            mBluetoothGatt = mBluetoothDevice.connectGatt(MainApp.instance().getApplicationContext(),
                    false, mGattCallback);
        }
        if (mBluetoothGatt == null) {
            log.debug("Failed to find GATT service from connected device");
            mRunBluetoothThread = false;
        }
    }

    private final BluetoothGattCallback mGattCallback = new BluetoothGattCallback() {
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            MedtronicPump pump = MedtronicPump.getInstance();
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                pump.connectionAttempts = 0;
                pump.failedToConnect = false;
                pump.setConnected(true);
                pump.setConnecting(false);
                pump.lastMessageTime = System.currentTimeMillis();
                log.debug("Connected to GATT server.");
                // Attempts to discover services after successful connection.
                log.debug("Attempting to start service discovery:" +
                        mBluetoothGatt.discoverServices());
                MainApp.bus().post(new EventStatusChanged(MedtronicPump.EVENT_CONNECTED)); // Update fragment, with new pump status
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                if (!pump.getConnected()) {
                    if (mConnectionAttempts >= 10) {
                        pump.connectionAttempts = pump.connectionAttempts + 1;
                        log.debug("Failed to connect to GATT, retrying...");
                        mConnectionAttempts = mConnectionAttempts + 1;
                        connect();
                        MainApp.bus().post(new EventStatusChanged(MedtronicPump.EVENT_CONNECTING)); // Update fragment, with new pump status
                    } else {
                        log.debug("Failed to connect to GATT 10 times");
                        failedToSetupBLE();
                    }
                } else {
                    if (pump.mActionState == 3) {
                        pump.mActionState = 0;
                        pump.setSleeping(true);
                        log.debug("Pump failed to confirm sleep");
                    }
                    pump.setConnecting(false);
                    pump.setConnected(false);
                    pump.lastMessageTime = System.currentTimeMillis();
                    log.debug("Disconnected from GATT server.");
                    mRunBluetoothThread = false;
                    MainApp.bus().post(new EventStatusChanged(MedtronicPump.EVENT_SLEEPING)); // Update fragment, with new pump status
                }
            }
        }

        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                log.debug("Found services");
                if (mBluetoothGatt != null) {
                    BluetoothGattService bleService = mBluetoothGatt.getService(
                            UUID.fromString(MedtronicPump.ESP_UUID_SERVICE));
                    if (bleService != null) {
                        log.debug("Found Medtronic GATT service with expected UUID");
                        mInputCharacteristic = bleService.getCharacteristic(
                                UUID.fromString(MedtronicPump.ESP_UUID_CHARACTERISTIC_RX));
                        mOutputCharacteristic = bleService.getCharacteristic(
                                UUID.fromString(MedtronicPump.ESP_UUID_CHARACTERISTIC_TX));
                        log.debug("Found mInputCharacteristic: " + (mInputCharacteristic != null));
                        log.debug("Found mOutputCharacteristic: " + (mOutputCharacteristic != null));
                        /*
                        if (mInputCharacteristic != null) {
                            log.debug("Found mInputCharacteristic");
                            //log.debug("mInputCharacteristic har properties: " + mInputCharacteristic.getProperties());
                            //log.debug("mInputCharacteristic har permissions: " + mInputCharacteristic.getPermissions());
                            setCharacteristicNotification(mInputCharacteristic, true);
                        }
                        if (mOutputCharacteristic != null) {
                            log.debug("Found mOutputCharacteristic");
                            //log.debug("mOutputCharacteristic har properties: " + mOutputCharacteristic.getProperties());
                            //log.debug("mOutputCharacteristic har permissions: " + mOutputCharacteristic.getPermissions());
                            //sendMessage("Test");
                        }
                        */
                        if (mInputCharacteristic != null && mOutputCharacteristic != null) {
                            setCharacteristicNotification(mInputCharacteristic, true);
                            performNextAction();
                        } else {
                            log.debug("Failed to get input and output characteristics");
                            failedToSetupBLE();
                        }
                    } else {
                        log.debug("Failed to get BLE service from connected device");
                        failedToSetupBLE();
                    }
                }
            } else {
                log.debug("onServicesDiscovered received: " + status);
            }
        }

        private void failedToSetupBLE() {
            MedtronicPump pump = MedtronicPump.getInstance();
            pump.connectionAttempts = pump.connectionAttempts + 1;
            pump.failedToConnect = true;
            pump.setConnecting(false);
            pump.setConnected(false);
            mRunBluetoothThread = false;
            MainApp.bus().post(new EventStatusChanged(MedtronicPump.EVENT_FAILED)); // Update fragment, with new pump status
        }

        /*
        @Override
        public void onCharacteristicRead(BluetoothGatt gatt,
                                         BluetoothGattCharacteristic characteristic,
                                         int status) {
            log.debug("onCharacteristicRead got value " + characteristic.getValue() + " as string: "
                    + characteristic.getStringValue(0));
            if (status == BluetoothGatt.GATT_SUCCESS) {

            } else {
                log.debug("onCharacteristicRead received: " + status);
            }
        }
        */

        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt,
                                            BluetoothGattCharacteristic characteristic) {
            //log.debug("onCharacteristicChanged got value " + characteristic.getValue() +
            //        " as string: " + characteristic.getStringValue(0));
            if (characteristic == mInputCharacteristic) {
                String message = characteristic.getStringValue(0);
                log.debug("Read characteristic value update to: " + message);
                handleMessage(message);
            }
        }
    };

    /**
     * Enables or disables notification on a give characteristic.
     *
     * @param characteristic Characteristic to act on.
     * @param enabled If true, enable notification.  False otherwise.
     */
    private void setCharacteristicNotification(BluetoothGattCharacteristic characteristic,
                                              boolean enabled) {
        try {
            mBluetoothGatt.setCharacteristicNotification(characteristic, enabled);
        } catch (Exception e) {
            log.error("Failed to set characteristic notification with error: " + e);
        }
    }

    /**
     * Wrties a string value to a give characteristic.
     *
     * @param characteristic Characteristic to act on.
     * @param message String message to write.
     */
    private synchronized boolean writeCharacteristic(BluetoothGattCharacteristic characteristic,
                                                     String message) {
        if (mBluetoothGatt != null) {
            characteristic.setValue(message);
            try {
                return mBluetoothGatt.writeCharacteristic(characteristic);
            } catch (Exception e) {
                log.error("Failed to write characteristic value with error: ", e);
            }
        }
        return false;
    }

    private void sendMessage(String message) {
        if (message != null) {
            message = mPassword + ":" + message;
            log.debug("sendMessage Write to output: " + message);
            writeCharacteristic(mOutputCharacteristic, message);
        }
    }

    protected void disconnect() {
        mRunBluetoothThread = false;
        if (mBluetoothGatt != null) {
            try {
                mBluetoothGatt.disconnect();
            } catch (Exception e) {
                log.error("Unhandled GATT exception: " + e);
            }
        }
        if (mBluetoothGatt != null) {
            try {
                mBluetoothGatt.close();
            } catch (Exception e) {
                log.error("Unhandled GATT exception: " + e);
            }
        }
        mBluetoothGatt = null;
    }

    /* Handle inbound bluetooth messages */
    private synchronized void handleMessage(String message) {
        boolean failed = false;
        log.debug("Got message from IOThread: " + message);
        char action = message.charAt(0);
        log.debug("messageHandler on char: " + action);
        switch (action) {
            case MedtronicPump.ESP_BATTERY: // ESP battery status
                log.debug("messageHandler on gotBatteryStatus");
                gotBatteryStatus(message);
                break;
            case MedtronicPump.ESP_TEMP: // Current ESP temp status
                log.debug("messageHandler on gotTempStatus");
                gotTempStatus(message);
                break;
            case MedtronicPump.ESP_BOLUS: // ESP bolus status
                log.debug("messageHandler on gotBolusStatus");
                gotBolusStatus(message);
                break;
            case MedtronicPump.ESP_SLEEP: // ESP confirmed sleep
                log.debug("messageHandler on gotSleepOk");
                gotSleepOk(message);
                break;
            default:
                failed = true;
                log.debug("messageHandler: Failed to interpret command");
        }
        if (!failed) {
            MedtronicPump.getInstance().lastMessageTime = System.currentTimeMillis();
            log.debug("Continuing to next action");
        } else {
            log.debug("Retrying last action");
        }
        performNextAction();
    }

    private void gotBatteryStatus(String message) { // Pump send battery status
        MedtronicPump pump = MedtronicPump.getInstance();
        pump.batteryRemaining = Integer.valueOf(message.substring(
                Character.toString(MedtronicPump.ESP_BATTERY).length() + 1,
                Character.toString(MedtronicPump.ESP_BATTERY).length() + 4)); // TODO is this necessary??
        uploadToNS(MedtronicPump.BT_COMM_CONFIRMED, message);
    }

    private void gotBolusStatus(String message) {
        MedtronicPump pump = MedtronicPump.getInstance(); // TODO implement this



        pump.isBolusConfirmed = true;
        dbCommandConfirmed(MedtronicPump.ANDROID_BOLUS); // Marks command as confirmed
        uploadToNS(MedtronicPump.BT_COMM_CONFIRMED, message);
    }

    private void gotTempStatus(String message) { // Confirm pump temp basal status matches simulated status
        MedtronicPump pump = MedtronicPump.getInstance(); // TODO implement this


        //pump.isTempInProgress

        pump.isTempActionConfirmed = true;
        dbCommandConfirmed(MedtronicPump.ANDROID_TEMP); // Marks command as confirmed
        uploadToNS(MedtronicPump.BT_COMM_CONFIRMED, message);
    }

    private void gotSleepOk(String message) { // Pump confirmed sleep command, pump is sleeping
        confirmWakeInterval(message);
        MedtronicPump pump = MedtronicPump.getInstance();
        pump.isSleepConfirmed = true;
        dbCommandConfirmed(MedtronicPump.ANDROID_SLEEP); // Marks command as confirmed
        uploadToNS(MedtronicPump.BT_COMM_CONFIRMED, Character.toString(MedtronicPump.ANDROID_SLEEP));
    }

    private void confirmWakeInterval(String message) { // Check if pump wake interval matches preferences
        MedtronicPump pump = MedtronicPump.getInstance();
        Integer ESPWakeInterval = Integer.valueOf(message.substring(
                Character.toString(MedtronicPump.ANDROID_WAKE).length()+1,
                Character.toString(MedtronicPump.ANDROID_WAKE).length()+2));
        pump.isWakeOk = Objects.equals(ESPWakeInterval, pump.wakeInterval);
    }

    /* Check pump settings and send commands */
    private void performNextAction() {
        //if (mSendMessageAttempts < 20) {
        MedtronicPump pump = MedtronicPump.getInstance();
        log.debug("performNextAction on action: " + pump.mActionState);
        switch (pump.mActionState) {
            case 0: // Wait for pump wake signal - When received, send ping (Pump wake, when isDeviceSleeping is false)
                checkStatus();
                break;
            case 1: // Pump has confirmed that it's awake, if battery was received (isReadyForNextMessage is true)
                checkBolus();
                break;
            case 2: // Check if temp basal is to be set or current temp basal is to be canceled
                checkTemp();
                break;
            case 3: // Bolus and temp set, no more commands to process - Put pump to sleep
                checkSleep();
                break;
        }
        MainApp.bus().post(new EventStatusChanged(MedtronicPump.EVENT_CONNECTED)); // Update fragment, with new pump status
        try {
            Thread.sleep(200);
        } catch (InterruptedException e) {
            log.error("ConnectThread got interrupted with error: " + e);
        }
        //} else {

        //}
    }

    private void checkStatus() {
        MedtronicPump pump = MedtronicPump.getInstance();
        if (pump.getConnected()) {
            sendCommand(MedtronicPump.ANDROID_PING);
            //sleepThread(50L);
            pump.mActionState = 1;
        }
    }

    private void checkBolus() {
        MedtronicPump pump = MedtronicPump.getInstance();
        if (pump.isBolusSendt) {
            if (pump.isBolusConfirmed) { // Check if bolus command is confirmed
                log.debug("checkBolus: bolus set");
                resetBolus();
                pump.mActionState = 2; // Bolus send and confirmed by pump, proceed
                performNextAction();
            } else { // Bolus command not confirmed, resend
                log.debug("checkBolus: setting bolus again");
                sendCommand(MedtronicPump.ANDROID_BOLUS);
                //sleepThread(60000L);
            }
        } else if (pump.deliverBolus) { // Check if there is any bolus to be delivered
            log.debug("checkBolus: setting bolus");
            sendCommand(MedtronicPump.ANDROID_BOLUS);
            //sleepThread(60000L);
        } else { // No bolus to deliver, proceed
            pump.mActionState = 2;
            log.debug("checkBolus: no bolus to deliver");
            performNextAction();
        }
    }

    private void checkTemp() {
        MedtronicPump pump = MedtronicPump.getInstance();
        if (pump.isTempActionSendt) {
            if (pump.isTempActionConfirmed) {
                resetTemp();
                pump.mActionState = 3; // Bolus send and confirmed by pump, proceed
                performNextAction();
            } else { // Temp action not confirmed, resend
                sendCommand(MedtronicPump.ANDROID_TEMP);
                //sleepThread(60000L);
            }
        } else if (pump.newTempAction) { // Check if there is any bolus to be delivered
            sendCommand(MedtronicPump.ANDROID_TEMP);
            //sleepThread(60000L);
        } else { // No temp action to be send, proceed
            pump.mActionState = 3;
            performNextAction();
        }
    }

    private void checkSleep() {
        MedtronicPump pump = MedtronicPump.getInstance();
        if (pump.isSleepSendt) {
            if (pump.isSleepConfirmed) {
                resetSleep();
                //sleepThread(50L);
                //pump.runCommandThread = false;
                pump.mActionState = 0;
                //if (mSerialIOThread != null) {
                //    mSerialIOThread.disconnect();
                //    log.debug("Thread disconnecting thread");
                //}
                //return;
                disconnect();
            } else {
                sendCommand(MedtronicPump.ANDROID_SLEEP);
                //sleepThread(100L);
            }
        } else {
            sendCommand(MedtronicPump.ANDROID_SLEEP);
            //sleepThread(100L);
        }
    }

    private void resetBolus() {
        MedtronicPump pump = MedtronicPump.getInstance();
        pump.deliverBolus = false; // Reset bolus state
        pump.isBolusSendt = false;
        pump.isBolusConfirmed = false;
        mSendMessageAttempts = 0;
    }

    private void resetTemp() {
        MedtronicPump pump = MedtronicPump.getInstance();
        pump.newTempAction = false;
        pump.tempAction = 0;
        pump.isTempActionSendt = false;
        pump.isTempActionConfirmed = false;
        mSendMessageAttempts = 0;
    }

    private void resetSleep() {
        MedtronicPump pump = MedtronicPump.getInstance();
        pump.isSleepSendt = false;
        pump.isSleepConfirmed = false;
        pump.setSleeping(true);
        mSendMessageAttempts = 0;
        MainApp.bus().post(new EventStatusChanged(MedtronicPump.EVENT_SLEEPING)); // Update fragment, with new pump status
    }

    private void sendCommand(char action) {
        String message = "";
        switch(action) {
            case MedtronicPump.ANDROID_PING:
                message = sendPing();
                break;
            case MedtronicPump.ANDROID_BOLUS:
                message = sendBolus();
                break;
            case MedtronicPump.ANDROID_TEMP:
                message = sendTempAction();
                break;
            case MedtronicPump.ANDROID_SLEEP:
                message = sendSleep();
                break;
        }
        if (!message.contains("ERROR")) {
            message = message + "\r";
            sendMessage(message);
            uploadToNS(MedtronicPump.BT_COMM_SEND, message);
            dbCommandSend(message);
        } else {
            log.error("Error on sendCommand");
        }
    }

    private String sendPing() {
        mSendMessageAttempts += 1;
        return Character.toString(MedtronicPump.ANDROID_PING);
    }

    private String sendBolus() {
        mSendMessageAttempts += 1;
        MedtronicPump pump = MedtronicPump.getInstance();
        pump.isBolusSendt = true;
        return (MedtronicPump.ANDROID_BOLUS + "=" + pump.bolusToDeliver);
    }

    private String sendTempAction() {
        mSendMessageAttempts += 1;
        MedtronicPump pump = MedtronicPump.getInstance();
        if (pump.tempAction == 1) { // Set new temp
            return getTempForESP();
        } else if (pump.tempAction == 2) { // Cancel current temp
            if (pump.isTempInProgress) { // Check if temp is in progress
                return cancelTempESP();
            }
            return "ERROR";
        } else {
            // Invalid temp command TODO this should never happen
            return "ERROR";
        }
    }

    private String getTempForESP() {
        MedtronicPump pump = MedtronicPump.getInstance();
        pump.isTempActionSendt = true;
        return (MedtronicPump.ANDROID_TEMP + "=" + pump.tempBasal + "&=" + pump.tempBasalDuration);
    }

    private String cancelTempESP() {
        MedtronicPump.getInstance().isTempActionSendt = true;
        return (MedtronicPump.ANDROID_TEMP + "=null");
    }

    private String sendSleep() {
        mSendMessageAttempts += 1;
        MedtronicPump.getInstance().isSleepSendt = true;
        return (MedtronicPump.ANDROID_SLEEP + "=" + MedtronicPump.getInstance().wakeInterval);
    }

    /* NS and database interfaces */
    /* Upload event to NS */
    private synchronized void uploadToNS(String uploadType, String command) {
        /*
        if (uploadCommandsToNS) {
            String note = uploadType + command;
            NSUpload.uploadEvent(CareportalEvent.NOTE, DateUtil.now(), note);
        }
        */
    }

    private synchronized void dbCommandConfirmed(char command) {
        /*
        MedtronicActionHistory record = MainApp.getDbHelper().
                getMedtronicActionByCommand(command);
        if (record != null) {
            record.setCommandConfirmed();
        }
        MainApp.getDbHelper().createOrUpdate(record);
        */
    }

    private synchronized void dbCommandSend(String command) {
                /*
                MedtronicActionHistory record = new MedtronicActionHistory(command, DateUtil.now(),
                        MedtronicPump.getInstance().isFakingConnection);
                record.setCommandSend();
                MainApp.getDbHelper().createOrUpdate(record);
                */
    }
}