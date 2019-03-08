package info.nightscout.androidaps.plugins.pump.medtronicESP.services;

import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Binder;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;

import java.io.IOException;
import java.util.Arrays;
import java.util.Objects;

import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.R;
import info.nightscout.androidaps.plugins.pump.medtronicESP.MedtronicPump;
import info.nightscout.androidaps.plugins.pump.medtronicESP.events.EventESPStatusUpdate;
import info.nightscout.androidaps.utils.SP;

/*
 *   Created by ldaug99 on 2019-02-17
 */

public class MedtronicService extends AbstractMedtronicService {
    private static final long minToMillisec = 60000;

    //private int missedWakes = 0;
    private boolean runThread = false;

    public MedtronicService() {
        mBinder = new MedtronicService.LocalBinder();
        registerBus();
        registerLocalBroadcastReceiver();
        updateESPWakeIntervalFromPref();
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

    public void connectESP() {
        MedtronicPump.getInstance().mantainingConnection = true;
        runThread = true;
        maintainConnection();
        MainApp.bus().post(new EventESPStatusUpdate());
    }

    public void disconnectESP() {
        MedtronicPump.reset();
        runThread = false;
        disconnectThread();
        MainApp.bus().post(new EventESPStatusUpdate());
    }

    public void cancleTempBasal() {
        MedtronicPump pump = MedtronicPump.getInstance();
        pump.newTemp = false;
        pump.cancelTemp = true;
    }

    public void setTempBasalRate(Double absoluteRate, Integer durationInMinutes) {
        MedtronicPump pump = MedtronicPump.getInstance();
        pump.tempBasal = absoluteRate;
        pump.tempBasalDuration = durationInMinutes;
        pump.newTemp = true;
    }

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
                log.debug("Device was disconnected: " + device.getName());//Device was disconnected
                if (mBTDevice != null && mBTDevice.getName() != null && mBTDevice.getName().equals(device.getName())) {
                    if (mSerialIOThread != null) {
                        mSerialIOThread.disconnect();
                    }
                    if (MedtronicPump.getInstance().mDeviceSleeping) {
                        //TODO device disconnected without receiving sleep signal!
                    }
                }
            } else if (BluetoothDevice.ACTION_ACL_CONNECTED.equals(action)){
                log.debug("Connected to: " + device.getName());
            }
        }
    };

    private BroadcastReceiver BluetoothMessage = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            MedtronicPump.getInstance().readyForNextMessage = false;
            String message = intent.getStringExtra("message");
            Log.d("receiver", "Got message: " + message);
            if (message.contains(MedtronicPump.ESP_WAKE)) {
                ESPAwake(message);
            } else {
                handleMessage(message);
            }
        }
    };

    private void ESPAwake(String message) {
        MedtronicPump pump = MedtronicPump.getInstance();
        log.debug("Message is: " + message);
        getWakeIntervalESP(message);
        if (pump.isNewPump) {
            pingESP();
            pump.isNewPump = false;
        }
        if (pump.mDeviceSleeping) {
            pingESP();
        }
        pump.mDeviceSleeping = false;
        pump.readyForNextMessage = true;
        MainApp.bus().post(new EventESPStatusUpdate());
    }

    private void handleMessage(String message) {
        MedtronicPump pump = MedtronicPump.getInstance();
        if (message.contains(MedtronicPump.ESP_BATT)) {
            pump.batteryRemaining = Integer.valueOf(message.substring(MedtronicPump.ESP_BATT.length()+1, MedtronicPump.ESP_BATT.length()+4));
        } else if (message.contains(MedtronicPump.ESP_SLEEP)) {
            pump.lastConnection = System.currentTimeMillis();
            pump.readyForNextMessage = false;
            pump.mDeviceSleeping = true;
            MainApp.bus().post(new EventESPStatusUpdate());
            return;
        }
        pump.readyForNextMessage = true;
    }

    private void sleepESP() {
        MedtronicPump pump = MedtronicPump.getInstance();
        if (!pump.mDeviceSleeping) {
            mSerialIOThread.sendMessage(MedtronicPump.ANDROID_SLEEP + "\r");
            pump.readyForNextMessage = false;
            pump.mDeviceSleeping = true;
        }
    }

    private void pingESP() {
        mSerialIOThread.sendMessage(MedtronicPump.ANDROID_PING + "\r");
    }

    private void getWakeIntervalESP(String message) {
        MedtronicPump pump = MedtronicPump.getInstance();
        updateESPWakeIntervalFromPref();
        Integer ESPWakeInterval = Integer.valueOf(message.substring(MedtronicPump.ANDROID_WAKE.length(), MedtronicPump.ANDROID_WAKE.length()+1));
        if (!Objects.equals(ESPWakeInterval, pump.wakeInterval)) {
            message = MedtronicPump.ANDROID_WAKE + String.valueOf(pump.wakeInterval) + "\r";
            mSerialIOThread.sendMessage(message);
        }
    }

    private void getTempForESP() {
        MedtronicPump pump = MedtronicPump.getInstance();
        if (pump.newTemp) {
            String message = MedtronicPump.ANDROID_TEMP + "=" + pump.tempBasal + "0=" + pump.tempBasalDuration + '\r';
            mSerialIOThread.sendMessage(message);
            pump.newTemp = false;
        }
    }

    private void cancelTempESP() {
        MedtronicPump pump = MedtronicPump.getInstance();
        if (pump.cancelTemp) {
            String message = MedtronicPump.ANDROID_TEMP + "=null" + '\r';
            mSerialIOThread.sendMessage(message);
            pump.cancelTemp = false;
            pump.isTempBasalInProgress = false;
        }
    }

    private void manageTempESP() {
        MedtronicPump pump = MedtronicPump.getInstance();
        if (pump.newTemp && !pump.cancelTemp) {
            getTempForESP();
        } else if (pump.cancelTemp) {
            cancelTempESP();
        }
    }

    private synchronized void sendNextMessage() {
        manageTempESP();
        sleepESP();
    }

    private void maintainConnection() {
        Thread thread = new Thread("maintainConnectionThread") {
            MedtronicPump pump = MedtronicPump.getInstance();
            public void run(){
                while (runThread) {
                    if(reconnectAfterSleep() && !isBTConnected()) {
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
                    } else if (pump.readyForNextMessage) {
                        pump.readyForNextMessage = false;
                        sendNextMessage();
                    }
                    resetOnFault();
                }
            }
        };
        thread.start();
    }

    private boolean reconnectAfterSleep(){
        MedtronicPump pump = MedtronicPump.getInstance();
        if (!pump.isNewPump) {
            return pump.mDeviceSleeping && (System.currentTimeMillis() - pump.lastConnection) >= (pump.wakeInterval * minToMillisec);
        }
        return true;
    }

    private void resetOnFault(){
        MedtronicPump pump = MedtronicPump.getInstance();
        if (!pump.isNewPump && (System.currentTimeMillis() - pump.lastConnection) >= (pump.wakeInterval * minToMillisec * 2)) {
            MedtronicPump.reset();
        }
    }

    public boolean isMaintainingConnection() {
        return runThread;
    }

    private boolean threadNotNull() {
        return mSerialIOThread != null;
    }

    private void disconnectThread() {
        if (threadNotNull()) mSerialIOThread.disconnect();
    }

    private void updateESPWakeIntervalFromPref() {
        MedtronicPump.getInstance().wakeInterval = SP.getInt(R.string.key_medtronicESP_wakeinterval, 1);
    }
}
