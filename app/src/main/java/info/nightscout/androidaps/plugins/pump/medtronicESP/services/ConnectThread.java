package info.nightscout.androidaps.plugins.pump.medtronicESP.services;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.os.ParcelUuid;
import android.os.SystemClock;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.R;
import info.nightscout.androidaps.plugins.pump.medtronicESP.MedtronicPump;
import info.nightscout.androidaps.plugins.pump.medtronicESP.utils.ConnectionUtil;
import info.nightscout.androidaps.utils.ToastUtils;


/*
 * Created by ldaubg99 on 2019-06-22
 */

public class ConnectThread extends Thread {
    //private static Logger log = LoggerFactory.getLogger(L.PUMPBTCOMM);
    private Logger log = LoggerFactory.getLogger("Medtronic");

    private boolean mRunConnectThread = true;

    BluetoothAdapter mBluetoothAdapter;
    BluetoothLeScanner bleScanner;
    BluetoothDevice bleDevice;

    private BluetoothWorkerThread mBLEWorkerThread;

    private static final UUID ESP_UUID = UUID.fromString(MedtronicPump.ESP_UUID_SERVICE);
    private static final List<ScanFilter> scanFilter =
            Arrays.asList(new ScanFilter.Builder().setServiceUuid(new ParcelUuid(ESP_UUID)).build());
    private static final ScanSettings scanSettings =
            new ScanSettings.Builder().setScanMode(ScanSettings.CALLBACK_TYPE_FIRST_MATCH).build();

    boolean mScanRunning = false;

    ConnectThread() {
        super();
        log.debug("Initializing ConnectThread");
        this.setDaemon(true);
        this.start();
    }

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
        if (pump.failedToReconnect && !pump.isConnecting) {
            return true;
        } else if (!pump.isConnected && !pump.isConnecting) {
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
                log.debug("Starting BLE scan");
                bleScanner.startScan(scanFilter, scanSettings, mLeScanCallback);
                //bleScanner.startScan(mLeScanCallback);
            }
        }
    }

    private ScanCallback mLeScanCallback = new ScanCallback() {
        @Override
        public void onScanResult(int callbackType, ScanResult result) {
            MedtronicPump pump = MedtronicPump.getInstance();
            if (!pump.isConnecting || !pump.isConnected) {
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
                        //bleDevice = device;
                        spawnBluetoothWorker(device);
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

    private synchronized void spawnBluetoothWorker(BluetoothDevice device) {
        if (device != null) {
            if (mBLEWorkerThread != null) {
                if (!mBLEWorkerThread.getRunConnectThread()) {
                    mBLEWorkerThread.setRunConnectThread(false);
                    try {
                        Thread.sleep(200);
                    } catch (InterruptedException e) {
                        log.error("ConnectThread got interrupted with error: " + e);
                    }
                    log.debug("Creating BluetoothWorkerThread");
                    mBLEWorkerThread = new BluetoothWorkerThread(device);
                }
            } else {
                log.debug("Creating BluetoothWorkerThread");
                mBLEWorkerThread = new BluetoothWorkerThread(device);
            }
            stopScanForDevice();
        }
    }

    private void stopScanForDevice() {
        if (mScanRunning) {
            mScanRunning = false;
            if (bleScanner != null) {
                log.debug("Stopping BLE scan");
                bleScanner.stopScan(mLeScanCallback);
                bleScanner.flushPendingScanResults(mLeScanCallback);
                //bleScanner = null;
            }
        }
    }

    private void terminateThread() {
        stopScanForDevice();
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
}