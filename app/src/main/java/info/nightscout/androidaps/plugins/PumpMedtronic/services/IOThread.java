package info.nightscout.androidaps.plugins.PumpMedtronic.services;

import android.bluetooth.BluetoothSocket;
import android.content.Intent;
import android.os.SystemClock;
import android.support.v4.content.LocalBroadcastManager;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.logging.L;
import info.nightscout.androidaps.plugins.PumpMedtronic.MedtronicPump;

/*
 *   Modified version of DanaR SerialIOThread by mike
 *
 *   Modified by ldaug99 on 2019-02-17
 */

public class IOThread extends AbstractIOThread {
    private static Logger log = LoggerFactory.getLogger(L.PUMPBTCOMM);

    private InputStream mInputStream = null;
    private OutputStream mOutputStream = null;
    private BluetoothSocket mRfCommSocket;

    public boolean mKeepRunning = true;
    private byte[] mReadBuff = new byte[0];

    public IOThread(BluetoothSocket rfcommSocket) {
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
                byte[] newData = new byte[Math.max(1024, availableBytes)];
                int gotBytes = mInputStream.read(newData);
                appendToBuffer(newData, gotBytes);
                while (peakForCarrageReturn()) {
                    SystemClock.sleep(10);
                    byte[] extractedBuff = cutMessageFromBuffer();
                    if (extractedBuff != null) {
                        String stringMessage = new String(extractedBuff, StandardCharsets.UTF_8);
                        log.debug("Got string from BluetoothDevice with content: " + stringMessage);
                        Intent intent = new Intent("NEW_BLUETOOTH_MESSAGE");
                        intent.putExtra("message", stringMessage);
                        LocalBroadcastManager.getInstance(MainApp.instance().getApplicationContext()).sendBroadcast(intent);
                    }
                }
            }
        } catch (Exception e) {
            if (!e.getMessage().contains("bt socket closed")) {
                log.error("Thread exception: ", e);
            }
        }
    }

    private boolean peakForCarrageReturn() {
        for(int index = 0; index < mReadBuff.length; index++){
            if(mReadBuff[index] == 13){ //Newline received, message end reached
                return true;
            }
        }
        return false;
    }

    private void appendToBuffer(byte[] newData, int gotBytes) {
        byte[] newReadBuff = new byte[mReadBuff.length + gotBytes];
        System.arraycopy(mReadBuff, 0, newReadBuff, 0, mReadBuff.length);
        System.arraycopy(newData, 0, newReadBuff, mReadBuff.length, gotBytes);
        mReadBuff = newReadBuff;
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
    }

    public void disconnect() {
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
}