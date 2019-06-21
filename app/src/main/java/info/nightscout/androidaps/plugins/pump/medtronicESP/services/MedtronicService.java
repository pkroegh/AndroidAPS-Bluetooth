package info.nightscout.androidaps.plugins.pump.medtronicESP.services;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Binder;
import android.os.Handler;
import android.os.ParcelUuid;

import com.squareup.otto.Subscribe;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.Vector;

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
    private boolean mRunConnectThread = true;

    private BluetoothWorkerThread blWorkerThread;

    /*
    private static final UUID ESP_UUID = UUID.fromString(MedtronicPump.ESP_UUID_SERVICE);
    private static final List<ScanFilter> scanFilter =
            Arrays.asList(new ScanFilter.Builder().setServiceUuid(new ParcelUuid(ESP_UUID)).build());
    private static final ScanSettings scanSettings =
            new ScanSettings.Builder().setScanMode(ScanSettings.CALLBACK_TYPE_FIRST_MATCH).build();
    */

    public MedtronicService() {
        mBinder = new MedtronicService.LocalBinder();
        registerBus();
        connectESP();
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
        if (MedtronicPump.getInstance().isFakingConnection) return;
        if (!isPasswordSet()) return;
        resetPumpInstance();
        startThread();
        MainApp.bus().post(new EventESPStatusUpdate());
    }

    public void disconnectESP() {
        stopThread();
        MainApp.bus().post(new EventESPStatusUpdate());
    }

    void killService() {} //TODO add feature

    public boolean getRunThread() {
        return mRunConnectThread;
    }

    private boolean isPasswordSet() {
        updatePreferences();
        if (MedtronicPump.getInstance().pump_password == null) {
            ToastUtils.showToastInUiThread(MainApp.instance().getApplicationContext(),
                    MainApp.gs(R.string.medtronicESP_noPassOrDevice));
            return false;
        }
        return true;
    }

    private void resetPumpInstance() {
        MedtronicPump pump = MedtronicPump.getInstance();
        pump.isConnecting = false;
        pump.isConnected = false;
        pump.isSleeping = false;
        pump.isReadyForMessage = false;
    }

    private void startThread() {
        mRunConnectThread = true;
        startConnectThread();
    }

    private void stopThread() {
        mRunConnectThread = false;
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
    
    /* Reconnect to pump on wake and send next message when awake and connected */
    private void startConnectThread() {
        Thread connectThread = new Thread("connectThread") {
            BluetoothAdapter mBluetoothAdapter;
            BluetoothLeScanner bleScanner;
            BluetoothDevice bleDevice;

            boolean mScanRunning = false;

            public void run() {
                initializeThread();
                while (mRunConnectThread) {
                    maintainConnection();
                }
                terminateThread();
            }

            private void initializeThread() {
                log.debug("Starting connectThread");
                if (!getBluetoothAdapter()) {
                    log.error("Unable to obtain bluetooth adapter, stopping.");
                    mRunConnectThread = false;
                }
            }

            private boolean getBluetoothAdapter() {
                mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
                if (mBluetoothAdapter != null) {
                    return true;
                }
                ToastUtils.showToastInUiThread(MainApp.instance().getApplicationContext(),
                        MainApp.gs(R.string.nobtadapter));
                return false;
            }

            private void maintainConnection() {
                if (isTimeToConnect() && !mScanRunning) { // It's time to reconnect after sleep
                    startScanForDevice();
                }
                //isPumpTimedOut(); // Check if pump is timed out
            }

            private boolean isTimeToConnect(){
                MedtronicPump pump = MedtronicPump.getInstance();
                if (!pump.isConnected && !pump.isConnecting) {
                    return ConnectionUtil.isTimeDifferenceLarger(pump.lastMessageTime,
                            pump.wakeInterval);
                }
                return false;
            }

            private void startScanForDevice() {
                if (!mScanRunning) {
                    mScanRunning = true;
                    bleScanner = mBluetoothAdapter.getBluetoothLeScanner();
                    if (bleScanner != null) {
                        log.debug("Starting ble scan");
                        //bleScanner.startScan(scanFilter, scanSettings, mLeScanCallback);
                        bleScanner.startScan(mLeScanCallback);
                    }
                }
            }

            private ScanCallback mLeScanCallback = new ScanCallback() {
                @Override
                public void onScanResult(int callbackType, ScanResult result) {
                    MedtronicPump pump = MedtronicPump.getInstance();
                    if (!pump.isConnecting) {
                        BluetoothDevice device = result.getDevice();
                        //log.debug("Found device: " + result.toString());
                        if (device != null) {
                            String name = "Unknown device";
                            if (device.getName() != null && device.getName().length() > 0) {
                                name = device.getName();
                            }
                            log.debug("Found device with address: " + device.getAddress() +
                                    " and name: " + name);
                            /*
                            ParcelUuid[] uuids = device.getUuids();
                            log.debug("Device has uuids: " + uuids);
                            if (uuids != null) {
                                for (ParcelUuid parcelUUID : uuids) {
                                    String stringUUID = parcelUUID.getUuid().toString();
                                    log.debug(device.getAddress() + "has UUID: " + stringUUID);
                                    if (MedtronicPump.ESP_UUID_SERVICE.equals(stringUUID)) {
                                        log.debug(device.getAddress() + "is Medtronic pump.");
                                    }
                                }
                            }
                            */
                            if (pump.mDevName.equals(name)) {
                                pump.isConnecting = true;
                                log.debug("Device matches pump name");
                                bleDevice = device;
                                spawnBluetoothWorker();
                            }
                        }
                        //super.onScanResult(callbackType, result);
                    }
                }

                public void onScanFailed(int errorCode) {
                    if (errorCode == ScanCallback.SCAN_FAILED_ALREADY_STARTED) {
                        stopScanForDevice();
                    } else {
                        log.error("Failed to start BLE scan with error code: ", errorCode);
                    }
                }
            };

            private void spawnBluetoothWorker() {
                if (blWorkerThread != null)
                    blWorkerThread.disconnect();
                if (bleDevice != null) {
                    log.debug("Creating BluetoothWorkerThread");
                    blWorkerThread = new BluetoothWorkerThread(bleDevice);
                }
            }

            private void stopScanForDevice() {
                if (mScanRunning) {
                    mScanRunning = false;
                    if (bleScanner != null) {
                        log.debug("Stopping ble scan");
                        bleScanner.flushPendingScanResults(mLeScanCallback);
                        bleScanner.stopScan(mLeScanCallback);
                        //bleScanner = null;
                    }
                }
            }

            private void terminateThread() {
                stopScanForDevice();
                if (blWorkerThread != null) {
                    blWorkerThread.disconnect();
                }
                blWorkerThread = null;
                log.debug("Stopping connectThread");
                try {
                    System.runFinalization();
                } catch (Exception e) {
                    log.error("Thread exception: " + e);
                }
            }

            /*
            private void isPumpTimedOut(){
                MedtronicPump pump = MedtronicPump.getInstance();
                if (ConnectionUtil.isTimeDifferenceLarger(pump.lastConnection,
                        pump.wakeInterval*2)) {
                    //pump.isDeviceSleeping = false;
                    pump.failedToReconnect = true;
                    stopScanForDevice();
                }
            }
            */
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
        //updateDeviceNameFromPref();
        updateWakeIntervalFromPref();
        updateExtBolusFromPref();
        updateFakeFromPref();
        updatePassFromPref();
        updateNSFromPref();
    }

    /*
    private void updateDeviceNameFromPref() {
        MedtronicPump pump = MedtronicPump.getInstance();
        String mDevName = SP.getString(MainApp.gs(R.string.key_medtronicESP_bt_name), null);
        if (mDevName != null && !mDevName.equals(pump.mDevName)) {
            pump.mDevName = mDevName;
        }
    }
    */

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
            if (blWorkerThread != null) {
                blWorkerThread.updatePumpPassword();
            }
        }
    }

    private void updateNSFromPref() {
        MedtronicPump.getInstance().isUploadingToNS =
                SP.getBoolean(R.string.key_medtronicESP_uploadNS, false);
    }
}