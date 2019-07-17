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
import info.nightscout.androidaps.plugins.pump.medtronicESP.utils.ConnUtil;
import info.nightscout.androidaps.plugins.pump.medtronicESP.utils.TimeUtil;

/*
 * Created by ldaubg99 on 2019-06-22
 */

public class BluetoothWorkerThread extends Thread {
    //private static Logger log = LoggerFactory.getLogger(L.PUMPBTCOMM);
    private Logger log = LoggerFactory.getLogger("Medtronic");

    private BluetoothAdapter mBluetoothAdapter;
    private BluetoothDevice mBluetoothDevice;
    private BluetoothGatt mBluetoothGatt;

    private BluetoothGattCharacteristic mOutputCharacteristic;
    private BluetoothGattCharacteristic mInputCharacteristic;

    private boolean mRunBluetoothThread = true;

    private String mPassword;

    BluetoothWorkerThread(BluetoothDevice bluetoothDevice) {
        super();
        log.debug("Initializing BluetoothWorkerThread.");
        mBluetoothDevice = bluetoothDevice;
        getPumpPassword();
        getBluetoothAdapter();
        this.setDaemon(true);
        this.start();
    }

    public void setRunConnectThread(boolean runThread) {
        mRunBluetoothThread = runThread;
    }

    private void getBluetoothAdapter() {
        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if (mBluetoothAdapter == null || mBluetoothDevice == null) {
            MedtronicPump.getInstance().fatalError = true;
            mRunBluetoothThread = false;
            ConnUtil.hardwareError();
            log.error("Unable to get bluetooth adapter.");
        }
    }

    private void getPumpPassword() {
        if (!MedtronicPump.isPasswordSet()) {
            MedtronicPump.getInstance().fatalError = true;
            mRunBluetoothThread = false;
            ConnUtil.internalError();
            log.error("Unable to get device password.");
        }
        mPassword = MedtronicPump.getInstance().pumpPassword;
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
        log.debug("Starting BluetoothWorkerThread.");
        connect();
    }

    private void terminateThread() {
        disconnect();
        try {
            System.runFinalization();
        } catch (Exception e) {log.error("Thread exception: " + e);}
        log.debug("Stopping BluetoothWorkerThread.");
    }

    private void maintainConnection() {
        MedtronicPump pump = MedtronicPump.getInstance();
        if (pump.fatalError) {
            mRunBluetoothThread = false;
            return;
        }
        if (pump.isConnected && pump.isServiceAndCharacteristicFound) {
            if (pump.commandRetries <= pump.commandRetryThreshold) {
                MedtronicPump.getInstance().fatalError = true;
                mRunBluetoothThread = false;
                ConnUtil.bleError();
                log.error("Device didn't respond to commands.");
            } else {
                performNextAction();
            }
        }
    }

    private void connect() {
        if (mBluetoothDevice != null) {
            MedtronicPump.getInstance().isConnecting = true;
            mBluetoothGatt = mBluetoothDevice.connectGatt(MainApp.instance().getApplicationContext(),
                    false, mGattCallback);
        }
        if (mBluetoothGatt == null) {
            MedtronicPump.getInstance().fatalError = true;
            mRunBluetoothThread = false;
            ConnUtil.bleError();
            log.error("Failed to get GATT instance from connected device.");
        }
        MainApp.bus().post(new EventStatusChanged(MedtronicPump.EVENT_CONNECTING)); // Update fragment, with new pump status.
    }

    private final BluetoothGattCallback mGattCallback = new BluetoothGattCallback() {
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            MedtronicPump pump = MedtronicPump.getInstance();
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                pump.isConnected = true;
                pump.isConnecting = false;
                log.debug("Connected to GATT server.");
                // Attempts to discover services after successful connection.
                log.debug("Attempting to start service discovery:" +
                        mBluetoothGatt.discoverServices());
                MainApp.bus().post(new EventStatusChanged(MedtronicPump.EVENT_CONNECTED)); // Update fragment, with new pump status.
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                log.debug("Disconnected from GATT server.");
                if (pump.isConnecting && !pump.isConnected) {
                    failedToConnectToBLE();
                } else {
                    deviceDisconnected();
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
                            MedtronicPump.getInstance().isServiceAndCharacteristicFound = true;
                        } else {
                            log.debug("Failed to get input and output characteristics.");
                            failedToGetServiceOrCharacteristics();
                        }
                    } else {
                        log.debug("Failed to get service from device.");
                        failedToGetServiceOrCharacteristics();
                    }
                }
            } else {
                log.debug("onServicesDiscovered received: " + status);
            }
        }

        private void failedToConnectToBLE() {
            MedtronicPump pump = MedtronicPump.getInstance();
            pump.connectionAttempts = pump.connectionAttempts + 1;
            if (pump.connectionAttempts <= pump.connectionAttemptThreshold) {
                MedtronicPump.getInstance().fatalError = true;
                mRunBluetoothThread = false;
                ConnUtil.bleError();
                log.error("Failed to connect to BLE service " +
                        pump.connectionAttemptThreshold + " times.");
            } else {
                log.debug("Failed to connect to GATT, retrying...");
                connect();
            }
        }

        private void deviceDisconnected() {
            MedtronicPump pump = MedtronicPump.getInstance();
            if (pump.actionState != 4) {
                MedtronicPump.getInstance().fatalError = true;
                mRunBluetoothThread = false;
                ConnUtil.bleError();
                log.error("Device did not finish communication before disconnecting.");
            } else {
                mRunBluetoothThread = false;
                pump.sleepStartTime = System.currentTimeMillis();
                MedtronicPump.prepareInstanceForNextWake();
                MainApp.bus().post(new EventStatusChanged(MedtronicPump.EVENT_SLEEPING)); // Update fragment, with new pump status.
                log.debug("Device disconnected as expected.");
            }
        }

        private void failedToGetServiceOrCharacteristics() {
            MedtronicPump.getInstance().fatalError = true;
            mRunBluetoothThread = false;
            ConnUtil.bleError();
            log.error("Failed to get service or characteristics. Device does not advertise correctly.");
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
            MedtronicPump.getInstance().lastCommandTime = System.currentTimeMillis();
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
                log.debug("messageHandler: Failed to interpret command");
        }
    }

    private void gotBatteryStatus(String message) { // Pump send battery status
        MedtronicPump pump = MedtronicPump.getInstance();
        pump.batteryRemaining = Integer.valueOf(message.substring(
                Character.toString(MedtronicPump.ESP_BATTERY).length() + 1,
                Character.toString(MedtronicPump.ESP_BATTERY).length() + 4)); // TODO is this necessary??
        pump.actionState = 1;
        pump.responseRecieved = true;
    }

    private void gotBolusStatus(String message) {
        MedtronicPump pump = MedtronicPump.getInstance();
        //TODO: Implement check of delivered bolus
        //TODO: Implement NS upload and command database
        pump.actionState = 2;
        pump.responseRecieved = true;
    }

    private void gotTempStatus(String message) {
        MedtronicPump pump = MedtronicPump.getInstance();
        //TODO: Implement check of set temp
        //TODO: Implement NS upload and command database
        pump.actionState = 3;
        pump.responseRecieved = true;
    }

    private void gotSleepOk(String message) { // Device confirmed sleep.
        MedtronicPump pump = MedtronicPump.getInstance();
        //TODO: Implement check of set sleep time
        Integer ESPWakeInterval = Integer.valueOf(message.substring(
                Character.toString(MedtronicPump.ANDROID_WAKE).length()+1,
                Character.toString(MedtronicPump.ANDROID_WAKE).length()+2));
        log.debug("Device will sleep for " + ESPWakeInterval + " minutes.");
        // Objects.equals(ESPWakeInterval, pump.wakeInterval);
        //TODO: Implement NS upload and command database
        pump.actionState = 4;
        pump.responseRecieved = true;
        sendSleep(); // TODO: Needed to make sure device goes to sleep, find a better solution
    }

    /* Check pump settings and send commands */
    private void performNextAction() {
        MedtronicPump pump = MedtronicPump.getInstance();
        if (!pump.responseRecieved && pump.lastCommandTime != 0) {
            if (pump.actionState == 1 || pump.actionState == 2) {
                if (!TimeUtil.isTimeDiffLargerMilli(pump.lastCommandTime,
                        pump.bolusAndTempSetDelay)) {
                    return;
                } else {
                    pump.commandRetries = pump.commandRetries + 1;
                }
            } else if (!TimeUtil.isTimeDiffLargerMilli(pump.lastCommandTime,
                    pump.timeIntervalBetweenCommands)) {
                return;
            } else {
                pump.commandRetries = pump.commandRetries + 1;
            }
        } else if (pump.responseRecieved) {
            pump.responseRecieved = false;
            pump.commandRetries = 0;
        }
        log.debug("performNextAction on action: " + pump.actionState);
        switch (pump.actionState) {
            case 0: // Send ping.
                sendPing();
                break;
            case 1: // Send bolus.
                sendBolus();
                break;
            case 2: // Send temp.
                sendTemp();
                break;
            case 3: // Send sleep.
                sendSleep();
                break;
            case 4: // Wait for disconnect.

                break;
        }
        MainApp.bus().post(new EventStatusChanged(MedtronicPump.EVENT_CONNECTED)); // Update fragment, with new pump status.
    }

    private void sendPing() {
        sendMessage(Character.toString(MedtronicPump.ANDROID_PING));
    }

    private void sendBolus() {
        MedtronicPump pump = MedtronicPump.getInstance();
        if (pump.newBolusAction) {
            sendMessage(MedtronicPump.ANDROID_BOLUS + "=" + pump.bolusToDeliver);
        } else {
            pump.actionState = 2;
        }
    }

    private void sendTemp() {
        MedtronicPump pump = MedtronicPump.getInstance();
        if (pump.newTempAction) {
            if (pump.cancelCurrentTemp) {
                if (pump.isTempInProgress) {
                    sendMessage(MedtronicPump.ANDROID_TEMP + "=null");
                } else {
                    log.error("Attempted to cancel temp, but temp is not active.");
                }
            } else {
                sendMessage(MedtronicPump.ANDROID_TEMP + "=" + pump.tempBasal +
                        "&=" + pump.tempBasalDuration);
            }
        } else {
            pump.actionState = 3;
        }
    }

    private void sendSleep() {
        sendMessage(MedtronicPump.ANDROID_SLEEP + "=" + MedtronicPump.getInstance().wakeInterval);
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