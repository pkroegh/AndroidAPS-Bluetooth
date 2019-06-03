package info.nightscout.androidaps.plugins.pump.medtronicESP.services;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import android.bluetooth.BluetoothSocket;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import info.nightscout.androidaps.logging.L;
import info.nightscout.androidaps.plugins.pump.medtronicESP.comm.MessageHandler;

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

    private boolean runThread = true;

    private String pumpPassword = "";

    private byte[] mReadBuff = new byte[0];

    IOThread(BluetoothSocket rfcommSocket, String password) {
        super();
        mRfCommSocket = rfcommSocket;
        trySetupIOStream(); // Setup input/output streams
        pumpPassword = password;
        //context.registerReceiver(newSendMessage, new IntentFilter(MedtronicPump.NEW_BT_MESSAGE), null, this);

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

    @Override
    public final void run() {
        while (runThread) {
            tryReadBluetoothStream();
        }
    }

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
                    log.debug("Send message to be processed");
                    MessageHandler.handleMessage(message);
                    //broadcastMessage();
                }
            }
        } catch (Exception e) {
            if (!e.getMessage().contains("bt socket closed")) {
                log.error("Thread exception: ", e);
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

    /*
    private void broadcastMessage(String message) {
        Intent intent = new Intent(MedtronicPump.NEW_BT_MESSAGE);
        intent.putExtra("message", message);
        LocalBroadcastManager.getInstance(MainApp.instance().getApplicationContext()).sendBroadcast(intent);
    }
    */

    /* Handle messages from pump to AndroidAPS */
    /*
    private BroadcastReceiver newSendMessage = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String message = intent.getStringExtra("message");
            log.debug("Got message to send: " + message);
            sendMessage(message);
        }
    };
    */

    protected synchronized void sendMessage(String message) {
        if (message != null) {
            message = pumpPassword + ":" + message;
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


}