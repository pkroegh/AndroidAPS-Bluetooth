package info.nightscout.androidaps.plugins.pump.medtronicESP.services;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.os.ParcelUuid;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.R;
import info.nightscout.androidaps.plugins.general.overview.events.EventNewNotification;
import info.nightscout.androidaps.plugins.general.overview.notifications.Notification;
import info.nightscout.androidaps.plugins.pump.medtronicESP.MedtronicPump;
import info.nightscout.androidaps.plugins.pump.medtronicESP.events.EventStatusChanged;
import info.nightscout.androidaps.plugins.pump.medtronicESP.utils.ConnUtil;
import info.nightscout.androidaps.plugins.pump.medtronicESP.utils.TimeUtil;
import info.nightscout.androidaps.utils.ToastUtils;


/*
 * Created by ldaubg99 on 2019-06-22
 */

public class ConnectThread extends Thread {
    //private static Logger log = LoggerFactory.getLogger(L.PUMPBTCOMM);
    private Logger log = LoggerFactory.getLogger("Medtronic");

    private boolean mRunConnectThread = true;

    private BluetoothAdapter mBluetoothAdapter;
    private BluetoothLeScanner bleScanner;

    private BluetoothWorkerThread mBLEWorkerThread;

    private static final UUID ESP_UUID = UUID.fromString(MedtronicPump.ESP_UUID_SERVICE);
    private static final List<ScanFilter> scanFilter =
            Arrays.asList(new ScanFilter.Builder().setServiceUuid(new ParcelUuid(ESP_UUID)).build());
    private static final ScanSettings scanSettings =
            new ScanSettings.Builder().setScanMode(ScanSettings.CALLBACK_TYPE_FIRST_MATCH).build();

    ConnectThread() {
        super();
        log.debug("Initializing ConnectThread");
        //MainApp.instance().getApplicationContext().registerReceiver(processUuids, new IntentFilter(BluetoothDevice.ACTION_UUID));
        this.setDaemon(true);
        this.start();
    }

    /*
    private BroadcastReceiver processUuids = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (BluetoothDevice.ACTION_UUID.equals(intent.getAction())) {
                log.debug("Got ACTION_UUID intent");
                Parcelable[] uuids = intent.getParcelableArrayExtra(BluetoothDevice.EXTRA_UUID);
                if (uuids != null) {
                    log.debug("Processing ACTION_UUID call");
                    for (Parcelable parcelable : uuids) {
                        ParcelUuid parcelUuid = (ParcelUuid) parcelable;
                        String stringUUID = parcelUuid.getUuid().toString();
                        log.debug("Device has UUID: " + stringUUID);
                        if (MedtronicPump.ESP_UUID_SERVICE.equals(stringUUID)) {
                            BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                            log.debug(device.getAddress() + "has UUID: " + stringUUID);
                            log.debug(device.getAddress() + "is Medtronic pump.");
                            MedtronicPump.getInstance().setConnecting(true);
                            spawnBluetoothWorker(device);
                            MainApp.bus().post(new EventUpdateGUI()); // Update fragment, with new pump status
                        }
                    }
                }
            }
        }
    };
    */

    public boolean getRunConnectThread() {
        return mRunConnectThread;
    }

    public void setRunConnectThread(boolean runThread) {
        mRunConnectThread = runThread;
    }

    public void run() {
        initializeThread();
        while (mRunConnectThread) {
            maintainConnection();
        }
        terminateThread();
    }

    private void initializeThread() {
        log.debug("Starting connectThread");
        getBluetoothAdapter();
    }

    private void getBluetoothAdapter() {
        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if (mBluetoothAdapter == null) {
            ToastUtils.showToastInUiThread(MainApp.instance().getApplicationContext(),
                    MainApp.gs(R.string.nobtadapter));
            MedtronicPump.getInstance().fatalError = true;
            mRunConnectThread = false;
            ConnUtil.hardwareError();
            log.error("Unable to obtain bluetooth adapter");
        }
    }

    private void maintainConnection() {
        if (!MedtronicPump.isPasswordSet()) {
            return;
        }
        MedtronicPump pump = MedtronicPump.getInstance();
        if (pump.fatalError) {
            mRunConnectThread = false;
            return;
        }
        if (pump.sleepStartTime == 0 || isWakeIntervalPassed()) {
            // It's time to connect to device.
            if (pump.sleepStartTime != 0) {
                pump.sleepStartTime = 0;
            }
            if (pump.connectPhase < 1 && !pump.isScanning) {
                pump.connectPhase = 1;
                // Scan has not been started and device has not been found, start scan.
                startScanForDevice();
            }
            if (isScanTimedOut()) {
                // Scan has been running for 5 min, but no device was found. Alarm user.
                mRunConnectThread = false;
                ConnUtil.bleError();
                log.error("Scanned for 5 minutes without finding device.");
            }
        } else {
            // Device is sleeping, update fragment and sleep thread.
            MainApp.bus().post(new EventStatusChanged()); // Updated fragment.
            sleepThread(2000);
        }
    }

    private boolean isWakeIntervalPassed() {
        MedtronicPump pump = MedtronicPump.getInstance();
        return TimeUtil.isTimeDiffLargerMin(pump.sleepStartTime,
                pump.wakeInterval);
    }

    private boolean isScanTimedOut() {
        MedtronicPump pump = MedtronicPump.getInstance();
        return TimeUtil.isTimeDiffLargerMin(pump.scanStartTime,
                pump.scanAlarmThreshold);
    }

    private void startScanForDevice() {
        MedtronicPump pump = MedtronicPump.getInstance();
        if (!pump.isScanning) { // If scan is not already started, start scan.
            pump.isScanning = true;
            bleScanner = mBluetoothAdapter.getBluetoothLeScanner();
            if (bleScanner != null) {
                log.debug("Starting BLE scan");
                bleScanner.startScan(scanFilter, scanSettings, mLeScanCallback);
                MainApp.bus().post(new EventStatusChanged(MedtronicPump.EVENT_SCAN)); // Update fragment, with new pump status.
            } else {
                MedtronicPump.getInstance().fatalError = true;
                mRunConnectThread = false;
                ConnUtil.hardwareError();
                log.error("Couldn't get BLE scanner");
            }
        }
    }

    private ScanCallback mLeScanCallback = new ScanCallback() {
        @Override
        public void onScanResult(int callbackType, ScanResult result) {
            MedtronicPump pump = MedtronicPump.getInstance();
            if (!pump.isDeviceFound) {
                BluetoothDevice device = result.getDevice();
                if (device != null) {
                    String name = "Unknown device";
                    if (device.getName() != null && device.getName().length() > 0) {
                        name = device.getName();
                    }
                    log.debug("Found device with address: " + device.getAddress() +
                            " and name: " + name);
                    /*
                    device.fetchUuidsWithSdp();
                    ParcelUuid[] uuids = device.getUuids();
                    log.debug("Device has uuids: " + uuids[0]);
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
                    if (MedtronicPump.deviceName.equals(name)) {
                        pump.isDeviceFound = true;
                        log.debug("Device matches pump name");
                        spawnBluetoothWorker(device);
                        MainApp.bus().post(new EventStatusChanged(MedtronicPump.EVENT_CONNECTING)); // Update fragment, with new pump status.
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
                /*
                MedtronicPump.getInstance().fatalError = true;
                mRunConnectThread = false;
                ConnUtil.hardwareError();
                log.error("Couldn't get BLE scanner");
                 */
            }
        }
    };

    private synchronized void spawnBluetoothWorker(BluetoothDevice device) {
        if (device != null) {
            if (mBLEWorkerThread != null) {
                mBLEWorkerThread.setRunConnectThread(false);
                sleepThread(200);
            }
            log.debug("Creating BluetoothWorkerThread");
            mBLEWorkerThread = new BluetoothWorkerThread(device);
            stopScanForDevice();
        }
    }

    private void stopScanForDevice() {
        MedtronicPump pump = MedtronicPump.getInstance();
        if (pump.isScanning) {
            pump.isScanning = false;
            if (bleScanner != null) {
                log.debug("Stopping BLE scan");
                bleScanner.stopScan(mLeScanCallback);
                bleScanner.flushPendingScanResults(mLeScanCallback);
            } else {
                MedtronicPump.getInstance().fatalError = true;
                mRunConnectThread = false;
                ConnUtil.hardwareError();
                log.error("Couldn't get BLE scanner");
            }
        }
    }

    private void sleepThread(long time) {
        try {
            Thread.sleep(time);
        } catch (InterruptedException e) {
            log.error("ConnectThread got interrupted with error: " + e);
        }
    }

    private void terminateThread() {
        stopScanForDevice();
        //MainApp.instance().getApplicationContext().unregisterReceiver(processUuids);
        if (mBLEWorkerThread != null) {
            mBLEWorkerThread.disconnect();
        }
        mBLEWorkerThread = null;
        log.debug("Stopping connectThread");
        try {
            System.runFinalization();
        } catch (Exception e) {
            log.error("Thread exception: " + e);
        }
    }
}