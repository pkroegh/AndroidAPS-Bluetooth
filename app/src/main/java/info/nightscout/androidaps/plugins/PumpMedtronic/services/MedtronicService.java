package info.nightscout.androidaps.plugins.PumpMedtronic.services;

import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Binder;
import android.os.SystemClock;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;

import java.io.IOException;
import java.util.Arrays;
import java.util.Objects;

import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.R;
import info.nightscout.androidaps.events.EventPumpStatusChanged;
import info.nightscout.androidaps.plugins.PumpDanaR.DanaRPump;
import info.nightscout.androidaps.plugins.PumpDanaR.SerialIOThread;
import info.nightscout.androidaps.plugins.PumpDanaR.services.DanaRExecutionService;
import info.nightscout.androidaps.plugins.PumpMedtronic.MedtronicPump;
import info.nightscout.utils.SP;

/*
 *   Created by ldaug99 on 2019-02-17
 */

public class MedtronicService extends AbstractMedtronicService {
    public Integer batteryPercent = 0;
    public Integer reservoirInUnits = 0;

    private Integer missedWakes = 0;
    private Integer queuedMessages = 0;
    private Integer wakeInterval = 0;
    private Boolean mConnectingAfterSleep = false;
    private long connectTime = 0;
    public Boolean runThread = true;

    public long lastDataTime = 0;
    protected Boolean mDeviceSleeping = false;

    private static final long minToMillisec = 60000;

    private boolean readyForNextMessage = false;

    private byte[] mWriteBuff = new byte[0];

    public MedtronicService() {
        mBinder = new MedtronicService.LocalBinder();
        registerBus();
        registerLocalBroadcastReceiver();
        updateESPWakeIntervalFromPref();
        maintainConnection();
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

    private void registerLocalBroadcastReceiver() {
        MainApp.instance().getApplicationContext().registerReceiver(BluetoothReceiver, new IntentFilter(BluetoothDevice.ACTION_ACL_CONNECTED));
        MainApp.instance().getApplicationContext().registerReceiver(BluetoothReceiver, new IntentFilter(BluetoothDevice.ACTION_ACL_DISCONNECTED));
        LocalBroadcastManager.getInstance(MainApp.instance().getApplicationContext()).registerReceiver(BluetoothMessage, new IntentFilter("NEW_BLUETOOTH_MESSAGE"));
    }

    private void unregisterLocalBroadcastReceiver() {
        MainApp.instance().getApplicationContext().unregisterReceiver(BluetoothMessage);
        MainApp.instance().getApplicationContext().unregisterReceiver(BluetoothReceiver);
    }

    protected BroadcastReceiver BluetoothReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
            String action = intent.getAction();
            if (BluetoothDevice.ACTION_ACL_DISCONNECTED.equals(action)) {
                log.debug("Device was disconnected: " + device.getName());//Device was disconnected
                if (mBTDevice != null && mBTDevice.getName() != null && mBTDevice.getName().equals(device.getName())) {
                    if (mSerialIOThread != null) {
                        mSerialIOThread.disconnect();
                    }
                }
                if (!mDeviceSleeping) {
                    //TODO device disconnected without receiving sleep signal!
                }
            } else if (BluetoothDevice.ACTION_ACL_CONNECTED.equals(action)){
                log.debug("Connected to: " + device.getName());

            }
        }
    };

    private BroadcastReceiver BluetoothMessage = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            readyForNextMessage = false;
            String message = intent.getStringExtra("message");
            Log.d("receiver", "Got message: " + message);
            if (message.contains(MedtronicPump.getInstance().ESP_WAKE)) {
                ESPAwake(message);
            } else {
                handleMessage(message);
            }
        }
    };

    private void updateESPWakeIntervalFromPref() {
        wakeInterval = SP.getInt(R.string.key_medtronicESP_wakeinterval, 1);
    }

    private void ESPAwake(String message) {
        updateESPWakeIntervalFromPref();
        Integer ESPWakeInterval = Integer.valueOf(message.substring(2,3));
        if (!Objects.equals(ESPWakeInterval, wakeInterval) && 0 < wakeInterval && wakeInterval < 9) {
            String replay = MedtronicPump.getInstance().ANDROID_WAKE+ "=" + wakeInterval + ";";
            queueMessage(replay);
        }
        if (mDeviceFirstConnect || MedtronicPump.getInstance().isNewPump) {
            MedtronicPump.getInstance().isNewPump = false;
            mDeviceFirstConnect = false;
        }
        queueMessage(MedtronicPump.getInstance().ANDROID_PING);
        mDeviceSleeping = false;
        readyForNextMessage = true;
    }

    private void handleMessage(String message) {
        log.debug("Got message: " + message);

        if (message.contains(MedtronicPump.getInstance().ESP_BATT)) {

        }


        readyForNextMessage = true;
    }

    public synchronized void queueMessage(String message) {
        message = message + "\r";
        byte[] messageBytes = message.getBytes();
        byte[] newWriteBuff = new byte[mWriteBuff.length + messageBytes.length];
        System.arraycopy(mWriteBuff, 0, newWriteBuff, 0, mWriteBuff.length);
        System.arraycopy(messageBytes, 0, newWriteBuff, mWriteBuff.length, messageBytes.length);
        mWriteBuff = newWriteBuff;
        queuedMessages++;
    }

    private synchronized void sendNextMessage() {
        if (queuedMessages > 0) {
            mSerialIOThread.sendMessage(cutMessageFromBuffer());
        } else {
            mSerialIOThread.sendMessage("S");
            readyForNextMessage = false;
        }
    }

    String cutMessageFromBuffer() {
        if(mWriteBuff != null && mWriteBuff.length > 0){
            for(int index = 0; index < mWriteBuff.length; index++){
                if(mWriteBuff[index] == 13){ //Newline received, message end reached
                    byte[] nextWriteMessage = Arrays.copyOfRange(mWriteBuff, 0,index + 1);
                    String nextMessageString = new String(nextWriteMessage);
                    mWriteBuff = Arrays.copyOfRange(mWriteBuff, index + 1,mWriteBuff.length);
                    queuedMessages--;
                    return nextMessageString;
                }
            }
            return null;
        } else {
            return null;
        }
    }

    private void maintainConnection() {
        Thread thread = new Thread("maintainConnectionThread") {
            public void run(){
                while (runThread) {
                    if(!isBTConnected()) {
                        getBTSocketForSelectedPump();
                        if (mRfcommSocket == null || mBTDevice == null) {
                            return; // Device not found
                        }
                        try {
                            mRfcommSocket.connect();
                        } catch (IOException e) {
                            //log.error("Unhandled exception", e);
                            if (e.getMessage().contains("socket closed")) {
                                log.error("Unhandled exception", e);
                            }
                        }
                        if (isBTConnected()) {
                            if (mSerialIOThread != null) {
                                mSerialIOThread.disconnect();
                            }
                            mSerialIOThread = new IOThread(mRfcommSocket);
                            ((IOThread) mSerialIOThread).mKeepRunning = true;
                            if(!mConnectingAfterSleep) {
                                connectTime = System.currentTimeMillis();
                                mConnectingAfterSleep = true;
                            }
                        }
                    } else if (readyForNextMessage) {
                        readyForNextMessage = false;
                        sendNextMessage();
                    }
                }
            }
        };
        thread.start();
    }

    public void killThread() {
        runThread = false;
    }

    private boolean threadNotNull() {
        if (mSerialIOThread != null) {
            return true;
        } else {
            return false;
        }
    }

    private void disconnectThread() {
        if (threadNotNull()) {
            mSerialIOThread.disconnect();
        }
    }
}
