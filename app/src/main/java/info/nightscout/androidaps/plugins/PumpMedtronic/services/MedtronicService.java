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
import info.nightscout.androidaps.plugins.PumpMedtronic.MedtronicPump;
import info.nightscout.androidaps.plugins.PumpMedtronic.events.EventESPStatusUpdate;
import info.nightscout.utils.SP;

/*
 *   Created by ldaug99 on 2019-02-17
 */

public class MedtronicService extends AbstractMedtronicService {
    private static final long minToMillisec = 60000;

    //private int missedWakes = 0;
    private int queuedMessages = 0;
    private boolean runThread = false;
    private byte[] mWriteBuff = new byte[0];

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

    private void updateESPWakeIntervalFromPref() {
        MedtronicPump.getInstance().wakeInterval = SP.getInt(R.string.key_medtronicESP_wakeinterval, 1);
    }

    private void ESPAwake(String message) {
        MedtronicPump pump = MedtronicPump.getInstance();
        log.debug("Message is: " + message);
        updateESPWakeIntervalFromPref();
        Integer ESPWakeInterval = Integer.valueOf(message.substring(MedtronicPump.ANDROID_WAKE.length(),MedtronicPump.ANDROID_WAKE.length()+1));
        if (!Objects.equals(ESPWakeInterval, pump.wakeInterval) && 0 < pump.wakeInterval && pump.wakeInterval < 9) {
            String replay = MedtronicPump.ANDROID_WAKE + String.valueOf(pump.wakeInterval);
            queueMessage(replay);
        }
        if (pump.isNewPump) {
            queueMessage(MedtronicPump.ANDROID_PING);
            pump.isNewPump = false;
        }
        if (pump.mDeviceSleeping) {
            queueMessage(MedtronicPump.ANDROID_PING);
        }
        pump.mDeviceSleeping = false;
        pump.readyForNextMessage = true;
        MainApp.bus().post(new EventESPStatusUpdate());
    }

    private void handleMessage(String message) {
        MedtronicPump pump = MedtronicPump.getInstance();
        if (message.contains(MedtronicPump.ESP_BATT)) {
            pump.batteryRemaining = Integer.valueOf(message.substring(MedtronicPump.ESP_BATT.length()+1,MedtronicPump.ESP_BATT.length()+4));
        } else if (message.contains(MedtronicPump.ESP_TEMP)) {
            Float ESPtempBasal = Float.valueOf(message.substring(MedtronicPump.ESP_TEMP.length()+1,MedtronicPump.ESP_TEMP.length()+5));
            Integer ESPtempDuration= Integer.valueOf(message.substring(MedtronicPump.ESP_TEMP.length()+5+":0=".length(),MedtronicPump.ESP_TEMP.length()+5+":0=".length()+2));
            //if (!Objects.equals(ESPtempBasal, pump.tempBasal)) {
                //String replay = MedtronicPump.ANDROID_TEMP + "=" + pump.tempBasal;
                //queueMessage(replay);
            //} else {
                pump.tempBasal = ESPtempBasal;
                pump.tempBasalDuration = ESPtempDuration;
            //}
        } else if (message.contains(MedtronicPump.ESP_SLEEP)) {
            pump.lastConnection = System.currentTimeMillis();
            pump.readyForNextMessage = false;
            pump.mDeviceSleeping = true;
            MainApp.bus().post(new EventESPStatusUpdate());
            return;
        }
        pump.readyForNextMessage = true;
    }

    public synchronized void queueMessage(String message) {
        message = message + "\r";
        byte[] messageBytes = message.getBytes();
        //removeDuplicateInOutputBuffer(messageBytes[0]);
        byte[] newWriteBuff = new byte[mWriteBuff.length + messageBytes.length];
        System.arraycopy(mWriteBuff, 0, newWriteBuff, 0, mWriteBuff.length);
        System.arraycopy(messageBytes, 0, newWriteBuff, mWriteBuff.length, messageBytes.length);
        mWriteBuff = newWriteBuff;
        queuedMessages++;
    }

    private synchronized void sendNextMessage() {
        MedtronicPump pump = MedtronicPump.getInstance();
        if (queuedMessages > 0) {
            mSerialIOThread.sendMessage(cutMessageFromBuffer());
        } else {
            mSerialIOThread.sendMessage(MedtronicPump.ANDROID_SLEEP + "\r");
            pump.readyForNextMessage = false;
            pump.mDeviceSleeping = true;
        }
    }

    private String cutMessageFromBuffer() {
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

    /*
    private synchronized void removeDuplicateInOutputBuffer(byte command_identifier) {
        MedtronicPump pump = MedtronicPump.getInstance();
        byte[] commandBuffer = "PTWS".getBytes();
        for (int start_index = 0; start_index < mWriteBuff.length; start_index++) {
            if (mWriteBuff[start_index] == command_identifier){
                for (int end_index = start_index + 1; end_index < mWriteBuff.length; end_index++) {
                    for (int index = 0; index < commandBuffer.length; index++) {
                        if (mWriteBuff[end_index] == commandBuffer[index]) {

                        }
                    }
                    if (mWriteBuff[end_index] == )
                }
                while ()
            }
        }

    }
    */

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
        if (threadNotNull()) {
            mSerialIOThread.disconnect();

        }
    }
}
