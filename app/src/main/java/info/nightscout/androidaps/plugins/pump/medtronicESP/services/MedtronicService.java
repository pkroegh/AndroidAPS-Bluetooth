package info.nightscout.androidaps.plugins.pump.medtronicESP.services;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Binder;

import com.squareup.otto.Subscribe;

import java.io.IOException;
import java.util.Set;
import java.util.UUID;

import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.R;
import info.nightscout.androidaps.events.EventPreferenceChange;
import info.nightscout.androidaps.plugins.pump.medtronicESP.MedtronicPump;
import info.nightscout.androidaps.plugins.pump.medtronicESP.events.EventESPStatusUpdate;
import info.nightscout.androidaps.plugins.pump.medtronicESP.utils.ConnectionUtil;
import info.nightscout.androidaps.plugins.treatments.TreatmentsPlugin;
import info.nightscout.androidaps.utils.SP;
import info.nightscout.androidaps.utils.ToastUtils;
/*
 *   Created by ldaug99 on 2019-02-17
 */

public class MedtronicService extends AbstractMedtronicService {
    private boolean runThread = true;

    private String mDevName;
    private BluetoothSocket mRfCommSocket;
    private BluetoothDevice mBTDevice;

    //private AbstractIOThread mSerialIOThread;

    private BluetoothWorkerThread blWorkerThread;

    private final UUID SPP_UUID = UUID.fromString("00001101-0000-1000-8000-00805f9b34fb");
    
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
        MedtronicPump pump = MedtronicPump.getInstance();
        if (pump.isFakingConnection) return;
        pump.isDeviceSleeping = false;
        pump.loopHandshake = true;
        startThread();
        MainApp.bus().post(new EventESPStatusUpdate());
    }

    public void disconnectESP() {
        MedtronicPump pump = MedtronicPump.getInstance();
        stopThread();
        pump.isDeviceSleeping = false;
        pump.loopHandshake = false;
        pump.isReadyForMessage = false;
        pump.failedToReconnect = false;
        MainApp.bus().post(new EventESPStatusUpdate());
    }

    private void startThread() {
        MedtronicPump pump = MedtronicPump.getInstance();
        runThread = true;
        startConnectThread();
    }

    private void stopThread() {
        MedtronicPump pump = MedtronicPump.getInstance();
        runThread = false;
        disconnectThread();
    }

    private void disconnectThread() {
        if (blWorkerThread != null) blWorkerThread.disconnect();
    }

    public boolean getRunThread() {
        return runThread;
    }

    public void setRunThread(boolean state) {
        runThread = state;
    }

    /* Pump actions */
    public void bolus(double bolus) {
        MedtronicPump pump = MedtronicPump.getInstance();
        if (pump.isFakingConnection) return;
        pump.bolusToDeliver = bolus;
        pump.deliverBolus = true;
    }

    public void tempBasalStop() {
        MedtronicPump pump = MedtronicPump.getInstance();
        if (pump.isFakingConnection) return;
        pump.tempAction = 2;
        pump.newTempAction = true;
    }

    public void tempBasal(double absoluteRate, int durationInMinutes) {
        MedtronicPump pump = MedtronicPump.getInstance();
        if (pump.isFakingConnection) return;
        pump.tempBasal = absoluteRate;
        pump.tempBasalDuration = durationInMinutes;
        pump.tempAction = 1;
        pump.newTempAction = true;
    }

    public void extendedBolus(double insulin, int durationInHalfHours) {  // TODO implement this
        MedtronicPump pump = MedtronicPump.getInstance();
        if (pump.isFakingConnection) return;

    }

    public void extendedBolusStop() {  // TODO implement this
        MedtronicPump pump = MedtronicPump.getInstance();
        if (pump.isFakingConnection) return;

    }

    /* Broadcast listeners, for BL connection and message reception */
    private void registerLocalBroadcastReceiver() {
        MainApp.instance().getApplicationContext().registerReceiver(BluetoothReceiver, new IntentFilter(BluetoothDevice.ACTION_ACL_CONNECTED));
        MainApp.instance().getApplicationContext().registerReceiver(BluetoothReceiver, new IntentFilter(BluetoothDevice.ACTION_ACL_DISCONNECTED));
        //LocalBroadcastManager.getInstance(MainApp.instance().getApplicationContext()).registerReceiver(BluetoothMessage, new IntentFilter(MedtronicPump.NEW_BT_MESSAGE));
    }

    private void unregisterLocalBroadcastReceiver() {
        MainApp.instance().getApplicationContext().unregisterReceiver(BluetoothReceiver);
        //MainApp.instance().getApplicationContext().unregisterReceiver(BluetoothMessage);
        //MainApp.instance().getApplicationContext().unregisterReceiver(BluetoothReceiver);
    }

    private BroadcastReceiver BluetoothReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
            String action = intent.getAction();
            if (BluetoothDevice.ACTION_ACL_DISCONNECTED.equals(action)) {
                log.debug("Device was disconnected: " + device.getName()); //Device was disconnected
                if (mBTDevice != null && mBTDevice.getName() != null && mBTDevice.getName().equals(device.getName())) {
                    if (blWorkerThread != null) {
                        blWorkerThread.disconnect();
                    }
                    if (!MedtronicPump.getInstance().isDeviceSleeping) {
                        //TODO device disconnected without receiving sleep signal!
                    }
                }
            } else if (BluetoothDevice.ACTION_ACL_CONNECTED.equals(action)){
                log.debug("Connected to: " + device.getName());
            }
        }
    };

    /*
    // Handle messages from pump to AndroidAPS
    private BroadcastReceiver BluetoothMessage = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            //MedtronicPump.getInstance().isReadyForMessage = false;
            String message = intent.getStringExtra("message");
            log.debug("receiver", "Got message: " + message);
            //messageHandler(message);
        }
    };
    */
    
    /* Reconnect to pump on wake and send next message when awake and connected */
    private void startConnectThread() {
        Thread connectThread = new Thread("connectThread") {
            
            public void run() {
                while (runThread) {
                    maintainConnection();
                }
            }

            private void maintainConnection() {
                if (isTimeToConnect()) { // Pump is not connected, but it's time to reconnect after sleep
                    getBTSocketForSelectedPump();
                    if (mRfCommSocket == null || mBTDevice == null) return; // Device or mRfCommSocket not found
                    tryConnect();
                    spawnIOThread();
                }
                isPumpTimedOut(); // Check if pump is timed out
            }

            private boolean isTimeToConnect(){
                MedtronicPump pump = MedtronicPump.getInstance();
                return pump.loopHandshake || (pump.isDeviceSleeping &&
                        ConnectionUtil.isTimeDifferenceLarger(pump.lastConnection,pump.wakeInterval));
            }

            private void getBTSocketForSelectedPump() {
                mDevName = SP.getString(MainApp.gs(R.string.key_medtronicESP_bt_name), null);
                if (mDevName != null) {
                    MedtronicPump.getInstance().mDevName = mDevName;
                    BluetoothAdapter bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
                    if (bluetoothAdapter != null) {
                        Set<BluetoothDevice> bondedDevices = bluetoothAdapter.getBondedDevices();
                        for (BluetoothDevice device : bondedDevices) {
                            if (mDevName.equals(device.getName())) {
                                mBTDevice = device;
                                try {
                                    mRfCommSocket = mBTDevice.createRfcommSocketToServiceRecord(SPP_UUID);
                                } catch (IOException e) {
                                    log.error("Error creating socket: ", e);
                                }
                                break;
                            }
                        }
                    } else {
                        ToastUtils.showToastInUiThread(MainApp.instance().getApplicationContext(), MainApp.gs(R.string.nobtadapter));
                    }
                }
            }

            private void tryConnect() {
                try {
                    mRfCommSocket.connect();
                    log.debug("mRfCommSocket connected");
                } catch (IOException e) {
                    if (e.getMessage().contains("socket closed")) {
                        log.error("Unhandled exception", e);
                    }
                }
            }

            private void spawnIOThread() {
                if (isBTConnected()) {
                    MedtronicPump pump = MedtronicPump.getInstance();
                    if (blWorkerThread != null) {
                        blWorkerThread.disconnect();
                        log.debug("Thread already defined, disconnecting");
                    }
                    //sleepThread(200L); // Sleep thread to make sure old thread is closed
                    if (pump.pump_password == null) updatePassFromPref();
                    blWorkerThread = new BluetoothWorkerThread(mRfCommSocket, pump.pump_password);
                    pump.lastConnection = System.currentTimeMillis();
                    log.debug("Pump connected");
                    //sleepThread(1000L);
                }
            }

            private boolean isBTConnected() {
                return mRfCommSocket != null && mRfCommSocket.isConnected();
            }

            private void isPumpTimedOut(){
                MedtronicPump pump = MedtronicPump.getInstance();
                if (!pump.loopHandshake && ConnectionUtil.isTimeDifferenceLarger(pump.lastConnection,
                        pump.wakeInterval*2)) {
                    pump.isDeviceSleeping = false;
                    pump.loopHandshake = true;
                    pump.failedToReconnect = true;
                }
            }

            private void sleepThread(long millis) {
                try {
                    Thread.sleep(millis);
                } catch (InterruptedException e) {
                    log.error("connectThread interrupt exception", e);
                }
            }
        };
        connectThread.setDaemon(true);
        connectThread.start();
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

    private void updateExtBolusFromPref() {
        MedtronicPump pump = MedtronicPump.getInstance();
        boolean previousValue = pump.isUsingExtendedBolus;
        pump.isUsingExtendedBolus = SP.getBoolean(R.string.key_medtronicESP_useextended, false);
        if (pump.isUsingExtendedBolus != previousValue &&
                TreatmentsPlugin.getPlugin().isInHistoryExtendedBoluslInProgress()) {
            extendedBolusStop();
        }
    }

    private void updateFakeFromPref() {
        MedtronicPump pump = MedtronicPump.getInstance();
        boolean previousValue = pump.isFakingConnection;
        pump.isFakingConnection = SP.getBoolean(R.string.key_medtronicESP_fake, false);
        if (pump.isFakingConnection != previousValue) {
            if (!pump.isFakingConnection) {
                connectESP();
            } else {
                disconnectESP();
            }
        }
    }

    private void updatePassFromPref() {
        MedtronicPump pump = MedtronicPump.getInstance();
        String new_password = SP.getString(R.string.key_medtronicESP_password, null);
        if (new_password != null && !new_password.equals(pump.pump_password)) {
            pump.pump_password = new_password;
        }
    }

    private void updateNSFromPref() {
        MedtronicPump.getInstance().isUploadingToNS =
                SP.getBoolean(R.string.key_medtronicESP_uploadNS, false);
    }
}