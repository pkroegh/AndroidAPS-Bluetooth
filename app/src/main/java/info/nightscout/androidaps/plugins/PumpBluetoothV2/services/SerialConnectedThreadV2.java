package info.nightscout.androidaps.plugins.PumpBluetoothV2.services;

import android.bluetooth.BluetoothSocket;
import android.os.SystemClock;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Arrays;

import info.nightscout.androidaps.plugins.PumpBluetooth.BluetoothPump;
import info.nightscout.utils.NSUpload;

public class SerialConnectedThreadV2 extends Thread{
    private static Logger log = LoggerFactory.getLogger(SerialConnectedThreadV2.class);

    protected BluetoothPump pump = BluetoothPump.getInstance();

    private InputStream mInputStream = null;
    private OutputStream mOutputStream = null;
    private BluetoothSocket mRfCommSocket;

    private byte[] mReadBuff = new byte[0];

    private boolean mKeepRunning = true;
    private boolean mWaitForConformation = false;

    public SerialConnectedThreadV2(BluetoothSocket rfcommSocket) {
        super();

        mRfCommSocket = rfcommSocket;
        try {
            mOutputStream = mRfCommSocket.getOutputStream();
            mInputStream = mRfCommSocket.getInputStream();
        } catch (IOException e) {
            log.error("Unhandled exception", e);
        }
        this.start();
    }

    @Override
    public final void run() {
        try {
            while (mKeepRunning) {
                int availableBytes = mInputStream.available();
                // Ask for 1024 byte (or more if available)
                byte[] newData = new byte[Math.max(1024, availableBytes)];
                int gotBytes = mInputStream.read(newData);
                // When we are here there is some new data available
                appendToBuffer(newData, gotBytes);

                // process all messages we already got
                while (mReadBuff.length > 3) { // 3rd byte is packet size. continue only if we an determine packet size
                    SystemClock.sleep(10);
                    byte[] extractedBuff = cutMessageFromBuffer();
                    if (extractedBuff == null){

                    } else {
                        String stringMessage = new String(extractedBuff, "UTF-8");

                        if (stringMessage.contains("OK")){

                        } else if (stringMessage.contains("pumpStatus")){
                            long now = System.currentTimeMillis();
                            log.debug("Got message back from pump!");
                            /*
                            pump.dailyTotalUnits = intFromBuff(bytes, 0, 3) / 750d;
                            pump.isExtendedInProgress = intFromBuff(bytes, 3, 1) == 1;
                            pump.extendedBolusMinutes = intFromBuff(bytes, 4, 2);
                            pump.extendedBolusAmount = intFromBuff(bytes, 6, 2) / 100d;
                            Double lastBolusAmount = intFromBuff(bytes, 13, 2) / 100d;
                            if (lastBolusAmount != 0d) {
                                pump.lastBolusTime = dateTimeFromBuff(bytes, 8);
                                pump.lastBolusAmount = lastBolusAmount;
                            }
                            pump.iob = intFromBuff(bytes, 15, 2) / 100d;

                            pump.pumpSuspended = intFromBuff(bytes, 0, 1) == 1;
                            pump.calculatorEnabled = intFromBuff(bytes, 1, 1) == 1;
                            pump.dailyTotalUnits = intFromBuff(bytes, 2, 3) / 750d;
                            pump.maxDailyTotalUnits = intFromBuff(bytes, 5, 2) / 100;
                            pump.reservoirRemainingUnits = intFromBuff(bytes, 7, 3) / 750d;
                            pump.bolusBlocked = intFromBuff(bytes, 10, 1) == 1;
                            pump.currentBasal = intFromBuff(bytes, 11, 2) / 100d;
                            pump.batteryRemaining = intFromBuff(bytes, 20, 1);
                            */

                            pump.lastConnection = now;

                            NSUpload.uploadDeviceStatus();
                        } else {

                        }
                    }
                }
            }
        } catch (Exception e) {
            if (e.getMessage().contains("bt socket closed"))
                log.error("Thread exception: ", e);
            mKeepRunning = false;
        }
        disconnect();
    }

    void appendToBuffer(byte[] newData, int gotBytes) {
        // add newData to mReadBuff
        byte[] newReadBuff = new byte[mReadBuff.length + gotBytes];
        System.arraycopy(mReadBuff, 0, newReadBuff, 0, mReadBuff.length);
        System.arraycopy(newData, 0, newReadBuff, mReadBuff.length, gotBytes);
        mReadBuff = newReadBuff;
    }

    byte[] cutMessageFromBuffer() {
        if(mReadBuff != null && mReadBuff.length > 2){
            for(int index = 0; index < mReadBuff.length; index++){
                if(mReadBuff[index] == 13 && mReadBuff[index + 1] == 10){ //Newline received, message end reached
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

    public synchronized void sendMessage(String message) {
        if (!mRfCommSocket.isConnected()) {
            log.error("Socket not connected on sendMessage");
            return;
        }

        byte[] messageBytes = message.getBytes();
        log.debug("Write to output: " + message);

        try {
            mOutputStream.write(messageBytes);
        } catch (Exception e) {
            log.error("sendMessage write exception: ", e);
        }

        if(mWaitForConformation){
            log.error("Unable to confirm");
            /*
            synchronized (message) {
                try {
                    message.wait(5000);
                } catch (InterruptedException e) {
                    log.error("sendMessage InterruptedException", e);
                }
            }

            SystemClock.sleep(200);
            if (!message.received) {
                log.warn("Reply not received " + message.getMessageName());
                if (message.getCommand() == 0xF0F1) {
                    DanaRPump.getInstance().isNewPump = false;
                    log.debug("Old firmware detected");
                }
            }
            */
        }
    }

    public void disconnect() {
        mKeepRunning = false;
        try {
            mInputStream.close();
        } catch (Exception e) {}
        try {
            mOutputStream.close();
        } catch (Exception e) {}
        try {
            mRfCommSocket.close();
        } catch (Exception e) {}
        try {
            System.runFinalization();
        } catch (Exception e) {}
        log.debug("Stopping SerialConnectedThread");
    }

}
