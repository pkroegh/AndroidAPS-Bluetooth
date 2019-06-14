package info.nightscout.androidaps.plugins.pump.medtronicESP.services;

import android.bluetooth.BluetoothSocket;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Objects;

import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.logging.L;
import info.nightscout.androidaps.plugins.pump.medtronicESP.MedtronicPump;
import info.nightscout.androidaps.plugins.pump.medtronicESP.events.EventESPStatusUpdate;

public class BluetoothWorkerThread extends Thread {
    private static Logger log = LoggerFactory.getLogger(L.PUMPBTCOMM);

    private InputStream mInputStream = null;
    private OutputStream mOutputStream = null;
    private BluetoothSocket mRfCommSocket;

    private boolean runThread = true;

    private int actionState = 0;
    private int sendMessageAttempts = 0;

    private String password = "";

    private byte[] mReadBuff = new byte[0];

    BluetoothWorkerThread(BluetoothSocket mRfCommSocket, String password) {
        super();
        this.password = password;
        this.mRfCommSocket = mRfCommSocket;
        trySetupIOStream(); // Setup input/output streams
        //this.setDaemon(true);
        this.start();
    }

    private void trySetupIOStream() {
        try {
            mOutputStream = mRfCommSocket.getOutputStream();
            mInputStream = mRfCommSocket.getInputStream();
        } catch (IOException e) {
            log.error("Unhandled exception", e);
        }
    }

    protected synchronized void sendMessage(String message) {
        if (message != null) {
            message = password + ":" + message;
            byte[] messageBytes = message.getBytes();
            log.debug("sendMessage Write to output: " + message);
            try {
                mOutputStream.write(messageBytes);
            } catch (Exception e) {
                log.error("sendMessage write exception: ", e);
            }
        }
    }

    protected void disconnect() {
        runThread = false;
        try {
            mInputStream.close();
        } catch (Exception e) {log.error("Thread exception: ", e);}
        try {
            mOutputStream.close();
        } catch (Exception e) {log.error("Thread exception: ", e);}
        try {
            mRfCommSocket.close();
        } catch (Exception e) {log.error("Thread exception: ", e);}
        try {
            System.runFinalization();
        } catch (Exception e) {log.error("Thread exception: ", e);}
        log.debug("Stopping OThread");
    }

    @Override
    public final void run() {
        while (runThread) {
            tryReadBluetoothStream();
        }
        disconnect();
    }

    /* Read messages from bluetooth input */
    private void tryReadBluetoothStream() {
        try {
            int availableBytes = mInputStream.available();
            byte[] newData = new byte[Math.max(1024, availableBytes)];
            int gotBytes = mInputStream.read(newData);
            appendToBuffer(newData, gotBytes);
            while (peakForCarrageReturn()) {
                byte[] extractedBuff = cutMessageFromBuffer();
                if (extractedBuff != null) {
                    String message = new String(extractedBuff, StandardCharsets.UTF_8);
                    log.debug("Got message: " + message);
                    handleMessage(message);
                }
            }
        } catch (Exception e) {
            log.error("Thread exception: ", e);
            if (e.getMessage().contains("bt socket closed")) {
                MedtronicPump pump = MedtronicPump.getInstance();
                pump.isDeviceSleeping = false;
                pump.loopHandshake = true;
                pump.failedToReconnect = true;
                disconnect();
            }
        }
    }

    private void appendToBuffer(byte[] newData, int gotBytes) {
        byte[] newReadBuff = new byte[mReadBuff.length + gotBytes];
        System.arraycopy(mReadBuff, 0, newReadBuff, 0, mReadBuff.length);
        System.arraycopy(newData, 0, newReadBuff, mReadBuff.length, gotBytes);
        mReadBuff = newReadBuff;
    }

    private boolean peakForCarrageReturn() {
        for(int index = 0; index < mReadBuff.length; index++){
            if (mReadBuff[index] == 13){ //Newline received, message end reached
                return true;
            }
        }
        return false;
    }

    private byte[] cutMessageFromBuffer() {
        if(mReadBuff != null && mReadBuff.length > 0){
            for(int index = 0; index < mReadBuff.length; index++){
                if(mReadBuff[index] == 13){ //Newline received, message end reached
                    byte[] newMessageBuff = Arrays.copyOfRange(mReadBuff, 0,index + 1);
                    mReadBuff = Arrays.copyOfRange(mReadBuff, index + 1,mReadBuff.length);
                    return newMessageBuff;
                }
            }
            return null;
        } else {
            return null;
        }
    }

    /* Handle inbound bluetooth messages */
    private synchronized void handleMessage(String message) {
        log.debug("Got message from IOThread: " + message);
        char action = message.charAt(0);
        log.debug("messageHandler on char: " + action);
        switch (action) {
            case MedtronicPump.ESP_WAKE: // ESP is handshaking
                log.debug("messageHandler on gotWake");
                gotWake(message);
                break;
            case MedtronicPump.ESP_BATTERY: // ESP battery status
                log.debug("messageHandler on gotBatteryStatus");
                gotBatteryStatus(message);
                break;
            case MedtronicPump.ESP_BOLUS: // ESP bolus status
                log.debug("messageHandler on gotBolusStatus");
                gotBolusStatus(message);
                break;
            case MedtronicPump.ESP_TEMP: // Current ESP temp status
                log.debug("messageHandler on gotTempStatus");
                gotTempStatus(message);
                break;
            case MedtronicPump.ESP_SLEEP: // ESP confirmed sleep
                log.debug("messageHandler on gotSleepOk");
                gotSleepOk();
                break;
            default:
                log.debug("messageHandler: Failed to interpret command");
        }
        performNextAction();
    }

    private synchronized void gotWake(String message) {
        MedtronicPump pump = MedtronicPump.getInstance();
        confirmWakeInterval(message);
        if (pump.loopHandshake) {
            pump.loopHandshake = false;
        }
        pump.isDeviceSleeping = false;
        pump.isReadyForMessage = true; // Message processed, ready to continue
        MainApp.bus().post(new EventESPStatusUpdate()); // Update fragment, with new pump status



        //pump.runCommandThread = true;
    }

    private synchronized void confirmWakeInterval(String message) { // Check if pump wake interval matches preferences
        MedtronicPump pump = MedtronicPump.getInstance();
        Integer ESPWakeInterval = Integer.valueOf(message.substring(
                Character.toString(MedtronicPump.ANDROID_WAKE).length()+1,
                Character.toString(MedtronicPump.ANDROID_WAKE).length()+2));
        pump.isWakeOk = Objects.equals(ESPWakeInterval, pump.wakeInterval);
    }

    private synchronized void gotBatteryStatus(String message) { // Pump send battery status
        MedtronicPump pump = MedtronicPump.getInstance();
        pump.batteryRemaining = Integer.valueOf(message.substring(
                Character.toString(MedtronicPump.ESP_BATTERY).length() + 1,
                Character.toString(MedtronicPump.ESP_BATTERY).length() + 4)); // TODO is this necessary??
        uploadToNS(MedtronicPump.BT_COMM_CONFIRMED, message);
        pump.isReadyForMessage = true; // Message processed, ready to continue
    }

    private synchronized void gotBolusStatus(String message) {
        MedtronicPump pump = MedtronicPump.getInstance(); // TODO implement this



        pump.isBolusConfirmed = true;
        dbCommandConfirmed(MedtronicPump.ANDROID_BOLUS); // Marks command as confirmed
        uploadToNS(MedtronicPump.BT_COMM_CONFIRMED, message);
        pump.isReadyForMessage = true; // Message processed, ready to continue
    }

    private synchronized void gotTempStatus(String message) { // Confirm pump temp basal status matches simulated status
        MedtronicPump pump = MedtronicPump.getInstance(); // TODO implement this


        //pump.isTempInProgress

        pump.isTempActionConfirmed = true;
        dbCommandConfirmed(MedtronicPump.ANDROID_TEMP); // Marks command as confirmed
        uploadToNS(MedtronicPump.BT_COMM_CONFIRMED, message);
        pump.isReadyForMessage = true; // Message processed, ready to continue
    }

    private synchronized void gotSleepOk() { // Pump confirmed sleep command, pump is sleeping
        MedtronicPump pump = MedtronicPump.getInstance();
        pump.lastConnection = System.currentTimeMillis();
        pump.isSleepConfirmed = true;
        dbCommandConfirmed(MedtronicPump.ANDROID_SLEEP); // Marks command as confirmed
        uploadToNS(MedtronicPump.BT_COMM_CONFIRMED, Character.toString(MedtronicPump.ANDROID_SLEEP));
        MainApp.bus().post(new EventESPStatusUpdate()); // Update fragment, with new pump status
    }

    /* Check pump settings and send commands */
    private synchronized void performNextAction() {
        if (sendMessageAttempts < 20) {
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
        } else {
            MedtronicPump pump = MedtronicPump.getInstance();
            pump.loopHandshake = true;
        }
    }

    private synchronized void checkWake() {
        MedtronicPump pump = MedtronicPump.getInstance();
        if (!pump.isDeviceSleeping) {
            if (!pump.isWakeOk) {
                sendCommand(MedtronicPump.ANDROID_WAKE);
                //sleepThread(50L);
            } else {
                sendCommand(MedtronicPump.ANDROID_PING);
                //sleepThread(50L);
                resetWake();
                actionState = 1;
            }
        }
    }

    private synchronized void checkBolus() {
        MedtronicPump pump = MedtronicPump.getInstance();
        if (pump.isReadyForMessage) {
            if (pump.isBolusSendt) {
                if (pump.isBolusConfirmed) { // Check if bolus command is confirmed
                    resetBolus();
                    actionState = 2; // Bolus send and confirmed by pump, proceed
                } else { // Bolus command not confirmed, resend
                    sendCommand(MedtronicPump.ANDROID_BOLUS);
                    //sleepThread(60000L);
                }
            } else if (pump.deliverBolus) { // Check if there is any bolus to be delivered
                sendCommand(MedtronicPump.ANDROID_BOLUS);
                //sleepThread(60000L);
            } else { // No bolus to deliver, proceed
                actionState = 2;
            }
        }
    }

    private synchronized void checkTemp() {
        MedtronicPump pump = MedtronicPump.getInstance();
        if (pump.isReadyForMessage) {
            if (pump.isTempActionSendt) {
                if (pump.isTempActionConfirmed) {
                    resetTemp();
                    actionState = 3; // Bolus send and confirmed by pump, proceed
                } else { // Temp action not confirmed, resend
                    sendCommand(MedtronicPump.ANDROID_TEMP);
                    //sleepThread(60000L);
                }
            } else if (pump.newTempAction) { // Check if there is any bolus to be delivered
                sendCommand(MedtronicPump.ANDROID_TEMP);
                //sleepThread(60000L);
            } else { // No temp action to be send, proceed
                actionState = 3;
            }
        }
    }

    private synchronized void checkSleep() {
        MedtronicPump pump = MedtronicPump.getInstance();
        if (pump.isReadyForMessage) {
            if (pump.isSleepSendt) {
                if (pump.isSleepConfirmed) {
                    resetSleep();
                    //sleepThread(50L);
                    //pump.runCommandThread = false;
                    actionState = 0;
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
    }

    private void resetWake() {
        sendMessageAttempts = 0;
    }

    private void resetBolus() {
        MedtronicPump pump = MedtronicPump.getInstance();
        pump.deliverBolus = false; // Reset bolus state
        pump.isBolusSendt = false;
        pump.isBolusConfirmed = false;
        sendMessageAttempts = 0;
    }

    private void resetTemp() {
        MedtronicPump pump = MedtronicPump.getInstance();
        pump.newTempAction = false;
        pump.tempAction = 0;
        pump.isTempActionSendt = false;
        pump.isTempActionConfirmed = false;
        sendMessageAttempts = 0;
    }

    private void resetSleep() {
        MedtronicPump pump = MedtronicPump.getInstance();
        pump.isSleepSendt = false;
        pump.isSleepConfirmed = false;
        pump.isReadyForMessage = false;
        pump.isDeviceSleeping = true;
        sendMessageAttempts = 0;
    }

    private void sendCommand(char action) {
        String message = "";
        switch(action) {
            case MedtronicPump.ANDROID_WAKE:
                message = sendWake();
                break;
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

    private String sendWake() {
        sendMessageAttempts += 1;
        return (MedtronicPump.ANDROID_WAKE + "=" + MedtronicPump.getInstance().wakeInterval);
    }

    private String sendPing() {
        sendMessageAttempts += 1;
        return Character.toString(MedtronicPump.ANDROID_PING);
    }

    private String sendBolus() {
        sendMessageAttempts += 1;
        MedtronicPump pump = MedtronicPump.getInstance();
        pump.isBolusSendt = true;
        return (MedtronicPump.ANDROID_BOLUS + "=" + pump.bolusToDeliver);
    }

    private String sendTempAction() {
        sendMessageAttempts += 1;
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
        return (MedtronicPump.ANDROID_TEMP + "=" + pump.tempBasal + "0=" + pump.tempBasalDuration);
    }

    private String cancelTempESP() {
        MedtronicPump.getInstance().isTempActionSendt = true;
        return (MedtronicPump.ANDROID_TEMP + "=null");
    }

    private String sendSleep() {
        sendMessageAttempts += 1;
        MedtronicPump.getInstance().isSleepSendt = true;
        return (Character.toString(MedtronicPump.ANDROID_SLEEP));
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
