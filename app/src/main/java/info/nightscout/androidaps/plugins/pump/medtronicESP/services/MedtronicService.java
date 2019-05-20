package info.nightscout.androidaps.plugins.pump.medtronicESP.services;

import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Binder;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;

import com.squareup.otto.Subscribe;

import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.Objects;

import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.R;
import info.nightscout.androidaps.db.CareportalEvent;
import info.nightscout.androidaps.events.EventPreferenceChange;
import info.nightscout.androidaps.plugins.general.nsclient.NSUpload;
import info.nightscout.androidaps.plugins.pump.medtronicESP.MedtronicPump;
import info.nightscout.androidaps.plugins.pump.medtronicESP.events.EventESPStatusUpdate;
import info.nightscout.androidaps.plugins.treatments.TreatmentsPlugin;
import info.nightscout.androidaps.utils.SP;

import static info.nightscout.androidaps.utils.DateUtil.now; // Get now() function, which returns current time
/*
 *   Created by ldaug99 on 2019-02-17
 */

public class MedtronicService extends AbstractMedtronicService {
    private static final long minToMillisec = 60000;

    private boolean runConnectThread = false;
    private boolean runCommandThread = false;

    /* NS upload types */
    private static final String COMMAND_SEND = "Send command: ";
    private static final String COMMAND_CONFIRMED = "Command confirmed: ";

    /* NS command types */
    private static final int SEND_PING = 1;
    private static final int SEND_BOLUS = 2;
    private static final int SEND_TEMP = 3;
    private static final int SEND_SLEEP = 4;

    public MedtronicService() {
        mBinder = new MedtronicService.LocalBinder();
        registerBus();
        registerLocalBroadcastReceiver();
        updateWakeIntervalFromPref();
        connectESP();
    }

    void killService() {
        unregisterLocalBroadcastReceiver();
    }

    public class LocalBinder extends Binder {
        public MedtronicService getServiceInstance() {
            return MedtronicService.this;
        }
    }

    private void registerBus() {
        try {
            MainApp.bus().unregister(this);
        } catch (RuntimeException x) {
        }
        MainApp.bus().register(this);
    }

    /* Connect and disconnect pump */
    public void connectESP() {
        if (isFakingConnection()) return;
        MedtronicPump pump = MedtronicPump.getInstance();
        pump.isDeviceSleeping = false;
        pump.loopHandshake = true;
        runConnectThread = true;
        maintainConnection();
        pumpCommandQueue();
        MainApp.bus().post(new EventESPStatusUpdate());
    }

    public void disconnectESP() {
        MedtronicPump pump = MedtronicPump.getInstance();
        pump.isDeviceSleeping = false;
        pump.loopHandshake = false;
        pump.isReadyForMessage = false;
        pump.failedToReconnect = false;
        runConnectThread = false;
        runCommandThread = false;
        disconnectThread();
        MainApp.bus().post(new EventESPStatusUpdate());
    }

    /* Pump actions */
    public void bolus(double bolus) {
        if (isFakingConnection()) return;
        MedtronicPump pump = MedtronicPump.getInstance();
        pump.bolusToDeliver = bolus;
        pump.deliverBolus = true;
    }

    public void tempBasalStop() {
        if (isFakingConnection()) return;
        MedtronicPump pump = MedtronicPump.getInstance();
        pump.tempAction = 2;
        pump.newTempAction = true;
    }

    public void tempBasal(double absoluteRate, int durationInMinutes) {
        if (isFakingConnection()) return;
        MedtronicPump pump = MedtronicPump.getInstance();
        pump.tempBasal = absoluteRate;
        pump.tempBasalDuration = durationInMinutes;
        pump.tempAction = 1;
        pump.newTempAction = true;
    }

    public void extendedBolus(double insulin, int durationInHalfHours) {  // TODO implement this
        //if (isFakingConnection()) return;
    }

    public void extendedBolusStop() {  // TODO implement this
        //if (isFakingConnection()) return;
    }

    /* Broadcast listeners, for BL connection and message reception */
    private void registerLocalBroadcastReceiver() {
        MainApp.instance().getApplicationContext().registerReceiver(BluetoothReceiver, new IntentFilter(BluetoothDevice.ACTION_ACL_CONNECTED));
        MainApp.instance().getApplicationContext().registerReceiver(BluetoothReceiver, new IntentFilter(BluetoothDevice.ACTION_ACL_DISCONNECTED));
        LocalBroadcastManager.getInstance(MainApp.instance().getApplicationContext()).registerReceiver(BluetoothMessage, new IntentFilter(MedtronicPump.NEW_BT_MESSAGE));
    }

    private void unregisterLocalBroadcastReceiver() {
        MainApp.instance().getApplicationContext().unregisterReceiver(BluetoothMessage);
        MainApp.instance().getApplicationContext().unregisterReceiver(BluetoothReceiver);
    }

    private BroadcastReceiver BluetoothReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
            String action = intent.getAction();
            if (BluetoothDevice.ACTION_ACL_DISCONNECTED.equals(action)) {
                log.debug("Device was disconnected: " + device.getName()); //Device was disconnected
                if (mBTDevice != null && mBTDevice.getName() != null && mBTDevice.getName().equals(device.getName())) {
                    if (mSerialIOThread != null) {
                        mSerialIOThread.disconnect();
                    }
                    if (MedtronicPump.getInstance().isDeviceSleeping) {
                        //TODO device disconnected without receiving sleep signal!
                    }
                }
            } else if (BluetoothDevice.ACTION_ACL_CONNECTED.equals(action)){
                log.debug("Connected to: " + device.getName());
            }
        }
    };

    /* Handle messages from pump to AndroidAPS */
    private BroadcastReceiver BluetoothMessage = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            MedtronicPump.getInstance().isReadyForMessage = false;
            String message = intent.getStringExtra("message");
            Log.d("receiver", "Got message: " + message);
            messageHandler(message);
        }
    };

    private void messageHandler(String message) {
        log.debug("Got message from IOThread: " + message);
        char action = message.charAt(0);
        switch (action) {
            case MedtronicPump.ESP_WAKE: // ESP is handshaking
                gotWake(message);
                break;
            case MedtronicPump.ESP_BATTERY: // ESP battery status
                gotBatteryStatus(message);
                break;
            case MedtronicPump.ESP_BOLUS: // ESP bolus status
                gotBolusStatus(message);
                break;
            case MedtronicPump.ESP_TEMP: // Current ESP temp status
                gotTempStatus(message);
                break;
            case MedtronicPump.ESP_SLEEP: // ESP confirmed sleep
                gotSleepOk();
                break;
        }
    }

    private void gotWake(String message) {
        MedtronicPump pump = MedtronicPump.getInstance();
        confirmWakeInterval(message);
        if (pump.loopHandshake) {
            pump.loopHandshake = false;
        }
        pump.isDeviceSleeping = false;
        pump.isReadyForMessage = true; // Message processed, ready to continue
        uploadToNS(COMMAND_CONFIRMED, message);
        MainApp.bus().post(new EventESPStatusUpdate()); // Update fragment, with new pump status
    }

    private void confirmWakeInterval(String message) { // Check if pump wake interval matches preferences
        MedtronicPump pump = MedtronicPump.getInstance();
        updateWakeIntervalFromPref();
        Integer ESPWakeInterval = Integer.valueOf(message.substring(
                MedtronicPump.ANDROID_WAKE.length(), MedtronicPump.ANDROID_WAKE.length()+1));
        if (!Objects.equals(ESPWakeInterval, pump.wakeInterval)) {
            message = MedtronicPump.ANDROID_WAKE + pump.wakeInterval + "\r";
            mSerialIOThread.sendMessage(message);
        }
    }

    private void gotBatteryStatus(String message) { // Pump send battery status
        MedtronicPump pump = MedtronicPump.getInstance();
        pump.batteryRemaining = Integer.valueOf(message.substring(
                Character.toString(MedtronicPump.ESP_BATTERY).length() + 1,
                Character.toString(MedtronicPump.ESP_BATTERY).length() + 4)); // TODO is this necessary??
        uploadToNS(COMMAND_CONFIRMED, message);
        pump.isReadyForMessage = true; // Message processed, ready to continue
    }

    private void gotBolusStatus(String message) {
        MedtronicPump pump = MedtronicPump.getInstance(); // TODO implement this



        pump.isBolusConfirmed = true;
        uploadToNS(COMMAND_CONFIRMED, message);
        pump.isReadyForMessage = true; // Message processed, ready to continue
    }

    private void gotTempStatus(String message) { // Confirm pump temp basal status matches simulated status
        MedtronicPump pump = MedtronicPump.getInstance(); // TODO implement this


        //pump.isTempInProgress

        pump.isTempActionConfirmed = true;
        uploadToNS(COMMAND_CONFIRMED, message);
        pump.isReadyForMessage = true; // Message processed, ready to continue
    }

    private void gotSleepOk() { // Pump confirmed sleep command, pump is sleeping
        MedtronicPump pump = MedtronicPump.getInstance();
        pump.isDeviceSleeping = true;
        pump.isSleepSendt = false;
        pump.lastConnection = System.currentTimeMillis();
        pump.isReadyForMessage = true;
        uploadToNS(COMMAND_CONFIRMED, MedtronicPump.ANDROID_SLEEP);
        MainApp.bus().post(new EventESPStatusUpdate()); // Update fragment, with new pump status
    }

    /* Reconnect to pump on wake and send next message when awake and connected */
    private void maintainConnection() {
        Thread thread = new Thread("connectThread") {
            MedtronicPump pump = MedtronicPump.getInstance();
            public void run(){
                while (runConnectThread) {
                    if (isBTConnected()) { // Pump connected, send messages
                        runCommandThread = true;
                    } else if (reconnectAfterSleep()) { // Pump is not connected, but it's time to reconnect after sleep
                        getBTSocketForSelectedPump();
                        if (mRfcommSocket == null || mBTDevice == null) {
                            return; // Device not found
                        }
                        try {
                            mRfcommSocket.connect();
                        } catch (IOException e) {
                            if (e.getMessage().contains("socket closed")) {
                                log.error("Unhandled exception", e);
                            }
                        }
                        if (isBTConnected()) {
                            if (mSerialIOThread != null) {
                                mSerialIOThread.disconnect();
                            }
                            try {
                                Thread.sleep(200);
                            } catch (Exception e) {
                                log.error("Thread sleep exception: ", e);
                            }
                            mSerialIOThread = new IOThread(mRfcommSocket);
                            pump.lastConnection = System.currentTimeMillis();
                        }
                    }
                    pumpTimedOut();
                }
            }
        };
        thread.start();
    }

    private void pumpCommandQueue() {
        Thread thread = new Thread("commandThread") {
            int actionState = 0;
            public void run(){
                while(runCommandThread) {
                    switch (actionState) {
                        case 0: // Wait for pump wake signal - When received, send ping (Pump wake, when isDeviceSleeping is false)
                            checkWake();
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
                }

            }

            private  void checkWake() {
                MedtronicPump pump = MedtronicPump.getInstance();
                if (!pump.isDeviceSleeping) {
                    sendCommand(SEND_PING);
                    sleepThread();
                    actionState = 1;
                }
            }

            private void checkBolus() {
                MedtronicPump pump = MedtronicPump.getInstance();
                if (pump.isReadyForMessage) {
                    if (pump.isBolusSendt) {
                        if (pump.isBolusConfirmed) { // Check if bolus command is confirmed
                            resetBolus();
                            actionState = 2; // Bolus send and confirmed by pump, proceed
                            return;
                        } else { // Bolus command not confirmed, resend
                            sendCommand(SEND_BOLUS);
                            sleepThread();
                        }
                    }
                    if (pump.deliverBolus) { // Check if there is any bolus to be delivered
                        sendCommand(SEND_BOLUS);
                        sleepThread();
                    } else { // No bolus to deliver, proceed
                        actionState = 2;
                    }
                }
            }

            private void checkTemp() {
                MedtronicPump pump = MedtronicPump.getInstance();
                if (pump.isReadyForMessage) {
                    if (pump.isTempActionSendt) {
                        if (pump.isTempActionConfirmed) {
                            resetTemp();
                            actionState = 3; // Bolus send and confirmed by pump, proceed
                            return;
                        } else { // Temp action not confirmed, resend
                            sendCommand(SEND_TEMP);
                            sleepThread();
                        }
                    }
                    if (pump.newTempAction) { // Check if there is any bolus to be delivered
                        sendCommand(SEND_TEMP);
                        sleepThread();
                    } else { // No temp action to be send, proceed
                        actionState = 3;
                    }
                }
            }

            private void checkSleep() {
                MedtronicPump pump = MedtronicPump.getInstance();
                if (pump.isReadyForMessage) {
                    if (pump.isSleepSendt) {
                        if (pump.isSleepConfirmed) {
                            resetSleep();
                            sleepThread();
                            runCommandThread = false;
                            actionState = 0;
                            return;
                        } else {
                            sendCommand(SEND_SLEEP);
                            sleepThread();
                        }
                    }
                    sendCommand(SEND_SLEEP);
                    sleepThread();
                }
            }

            private void sleepThread() {
                try {
                    Thread.sleep(500);
                } catch (Exception e) {
                    log.error("Thread sleep exception: ", e);
                }
            }

            private void resetBolus() {
                MedtronicPump pump = MedtronicPump.getInstance();
                pump.deliverBolus = false; // Reset bolus state
                pump.isBolusSendt = false;
                pump.isBolusConfirmed = false;
            }

            private void resetTemp() {
                MedtronicPump pump = MedtronicPump.getInstance();
                pump.newTempAction = false;
                pump.tempAction = 0;
                pump.isTempActionSendt = false;
                pump.isTempActionConfirmed = false;
            }

            private void resetSleep() {
                MedtronicPump pump = MedtronicPump.getInstance();
                pump.isSleepSendt = false;
                pump.isSleepConfirmed = false;
                pump.isReadyForMessage = false;
                pump.isDeviceSleeping = true;
            }
        };
        thread.start();
    }

    private void sendCommand(int action) {
        MedtronicPump pump = MedtronicPump.getInstance(); // Get pump instance, save to pump variable
        String message = "";
        switch(action) {
            case SEND_PING:
                message = sendPing();
                break;
            case SEND_BOLUS:
                message = sendBolus();
                break;
            case SEND_TEMP:
                message = sendTempAction();
                break;
            case SEND_SLEEP:
                message = sendSleep();
                break;
        }
        message = message + "\r";
        mSerialIOThread.sendMessage(message);
        uploadToNS(COMMAND_SEND, message);
    }

    private String sendPing() {
        return MedtronicPump.ANDROID_PING;
    }

    private String sendBolus() {
        MedtronicPump pump = MedtronicPump.getInstance();
        pump.isBolusSendt = true;
        return (MedtronicPump.ANDROID_BOLUS + "=" + pump.bolusToDeliver);
    }

    private String sendTempAction() {
        MedtronicPump pump = MedtronicPump.getInstance();
        if (pump.tempAction == 1) { // Set new temp
            return getTempForESP();
        } else if (pump.tempAction == 2) { // Cancel current temp
            if (pump.isTempInProgress) { // Check if temp is in progress
                return cancelTempESP();
            }
            return "ERROR - TEMP NOT IN PROGRESS ON CANCEL";
        } else {
            // Invalid temp command TODO this should never happen
            return "FATAL ERROR";
        }
    }


    private String getTempForESP() {
        MedtronicPump pump = MedtronicPump.getInstance();
        pump.isTempActionSendt = true;
        return (MedtronicPump.ANDROID_TEMP + "=" + pump.tempBasal + "0=" + pump.tempBasalDuration);
    }

    private String cancelTempESP() {
        MedtronicPump.getInstance().isTempActionSendt = true;
        return (MedtronicPump.ANDROID_TEMP + "=null");
    }

    private String sendSleep() {
        MedtronicPump.getInstance().isSleepSendt = true;
        return (MedtronicPump.ANDROID_SLEEP);
    }

    private boolean reconnectAfterSleep(){
        MedtronicPump pump = MedtronicPump.getInstance();
        if (!pump.loopHandshake) {
            return pump.isDeviceSleeping && isTimeDifferenceLarger(pump.lastConnection,
                    pump.wakeInterval);
        }
        return true;
    }

    private void pumpTimedOut(){
        MedtronicPump pump = MedtronicPump.getInstance();
        if (!pump.loopHandshake && isTimeDifferenceLarger(pump.lastConnection,
                pump.wakeInterval*2)) {
            pump.isDeviceSleeping = false;
            pump.loopHandshake = true;
            pump.failedToReconnect = true;
        }
    }

    private boolean isTimeDifferenceLarger(long lastTime, int threshold) {
        return (System.currentTimeMillis() - lastTime) >= (threshold * minToMillisec);
    }

    public boolean isThreadRunning() {
        return runConnectThread;
    }

    private boolean threadNotNull() {
        return mSerialIOThread != null;
    }

    private void disconnectThread() {
        if (threadNotNull()) mSerialIOThread.disconnect();
    }

    /* Upload event to NS */
    private void uploadToNS(String uploadType, String command) {
        if (uploadCommandsToNS) {
            String note = uploadType + command;
            NSUpload.uploadEvent(CareportalEvent.NOTE, now(), note);
        }
    }

    /* Preference management */
    @Subscribe
    public void onStatusEvent(final EventPreferenceChange s) {
        updatePreferences();
    }

    public void updatePreferences() {
        updateWakeIntervalFromPref();
        updateExtBolusFromPref();
        updateFakeFromPref();
        updatePassFromPref();
        updateNSFromPref();
    }

    private void updateWakeIntervalFromPref() {
        MedtronicPump pump = MedtronicPump.getInstance();
        int previousValue = pump.wakeInterval;
        int wakeInterval = SP.getInt(R.string.key_medtronicESP_wakeinterval, 1);
        int maxInterval = this.getResources().getInteger(R.integer.ESP_max_sleep_interval);
        int minInterval = this.getResources().getInteger(R.integer.ESP_min_sleep_interval);
        if (wakeInterval != previousValue) {
            if (wakeInterval > maxInterval) wakeInterval = maxInterval;
            if (wakeInterval < minInterval) wakeInterval = minInterval;
            pump.wakeInterval = wakeInterval;
        }
    }
    
    private void updateExtBolusFromPref() {
        boolean previousValue = useExtendedBoluses;
        useExtendedBoluses = SP.getBoolean(R.string.key_medtronicESP_useextended, false);
        if (useExtendedBoluses != previousValue && TreatmentsPlugin.getPlugin().isInHistoryExtendedBoluslInProgress()) {
            extendedBolusStop();
        }
    }

    private void updateFakeFromPref() {
        boolean previousValue = fakeESPconnection;
        fakeESPconnection = SP.getBoolean(R.string.key_medtronicESP_fake, false);
        if (fakeESPconnection != previousValue && !fakeESPconnection) {
            if (!fakeESPconnection) {
                connectESP();
            } else {
                disconnectESP();
            }
        }
    }

    private void updatePassFromPref() {
        MedtronicPump pump = MedtronicPump.getInstance();
        String new_password = SP.getString(R.string.key_medtronicESP_password, "");
        if (!new_password.equals(pump.pump_password)) {
            pump.pump_password = new_password;
        }
    }

    private void updateNSFromPref() {
        uploadCommandsToNS = SP.getBoolean(R.string.key_medtronicESP_uploadNS, false);
    }
}