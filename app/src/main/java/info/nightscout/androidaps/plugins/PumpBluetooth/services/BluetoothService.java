package info.nightscout.androidaps.plugins.PumpBluetooth.services;

import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Bitmap;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import android.os.SystemClock;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.squareup.otto.Subscribe;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.lang.reflect.Method;
import java.util.Date;
import java.util.Set;
import java.util.UUID;

import info.nightscout.androidaps.Config;
import info.nightscout.androidaps.Constants;
import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.R;
import info.nightscout.androidaps.Services.Intents;
import info.nightscout.androidaps.data.Profile;
import info.nightscout.androidaps.data.PumpEnactResult;
import info.nightscout.androidaps.db.Treatment;
import info.nightscout.androidaps.events.EventAppExit;
import info.nightscout.androidaps.events.EventInitializationChanged;
import info.nightscout.androidaps.events.EventPreferenceChange;
import info.nightscout.androidaps.events.EventPumpStatusChanged;
import info.nightscout.androidaps.plugins.ConfigBuilder.ConfigBuilderPlugin;
import info.nightscout.androidaps.plugins.Overview.Dialogs.BolusProgressDialog;
import info.nightscout.androidaps.plugins.Overview.events.EventNewNotification;
import info.nightscout.androidaps.plugins.Overview.events.EventOverviewBolusProgress;
import info.nightscout.androidaps.plugins.Overview.notifications.Notification;
import info.nightscout.androidaps.plugins.PumpBluetooth.BluetoothPumpFragment;
import info.nightscout.androidaps.plugins.PumpBluetooth.BluetoothPumpPlugin;
import info.nightscout.androidaps.plugins.PumpBluetooth.events.EventBluetoothPumpStatusChanged;
import info.nightscout.androidaps.plugins.PumpBluetooth.events.EventBluetoothPumpUpdateGui;



import info.nightscout.androidaps.plugins.PumpDanaR.DanaRPlugin;
import info.nightscout.androidaps.plugins.PumpDanaR.DanaRPump;
import info.nightscout.androidaps.plugins.PumpDanaR.comm.MsgBolusProgress;
import info.nightscout.androidaps.plugins.PumpDanaR.comm.MsgBolusStart;
import info.nightscout.androidaps.plugins.PumpDanaR.comm.MsgBolusStartWithSpeed;
import info.nightscout.androidaps.plugins.PumpDanaR.comm.MsgBolusStop;
import info.nightscout.androidaps.plugins.PumpDanaR.comm.MsgCheckValue;
import info.nightscout.androidaps.plugins.PumpDanaR.comm.MsgHistoryAlarm;
import info.nightscout.androidaps.plugins.PumpDanaR.comm.MsgHistoryBasalHour;
import info.nightscout.androidaps.plugins.PumpDanaR.comm.MsgHistoryBolus;
import info.nightscout.androidaps.plugins.PumpDanaR.comm.MsgHistoryCarbo;
import info.nightscout.androidaps.plugins.PumpDanaR.comm.MsgHistoryDailyInsulin;
import info.nightscout.androidaps.plugins.PumpDanaR.comm.MsgHistoryDone;
import info.nightscout.androidaps.plugins.PumpDanaR.comm.MsgHistoryError;
import info.nightscout.androidaps.plugins.PumpDanaR.comm.MsgHistoryGlucose;
import info.nightscout.androidaps.plugins.PumpDanaR.comm.MsgHistoryRefill;
import info.nightscout.androidaps.plugins.PumpDanaR.comm.MsgHistorySuspend;
import info.nightscout.androidaps.plugins.PumpDanaR.comm.MsgPCCommStart;
import info.nightscout.androidaps.plugins.PumpDanaR.comm.MsgPCCommStop;
import info.nightscout.androidaps.plugins.PumpDanaR.comm.MsgSetActivateBasalProfile;
import info.nightscout.androidaps.plugins.PumpDanaR.comm.MsgSetBasalProfile;
import info.nightscout.androidaps.plugins.PumpDanaR.comm.MsgSetCarbsEntry;
import info.nightscout.androidaps.plugins.PumpDanaR.comm.MsgSetExtendedBolusStart;
import info.nightscout.androidaps.plugins.PumpDanaR.comm.MsgSetExtendedBolusStop;
import info.nightscout.androidaps.plugins.PumpDanaR.comm.MsgSetTempBasalStart;
import info.nightscout.androidaps.plugins.PumpDanaR.comm.MsgSetTempBasalStop;
import info.nightscout.androidaps.plugins.PumpDanaR.comm.MsgSetTime;
import info.nightscout.androidaps.plugins.PumpDanaR.comm.MsgSettingActiveProfile;
import info.nightscout.androidaps.plugins.PumpDanaR.comm.MsgSettingBasal;
import info.nightscout.androidaps.plugins.PumpDanaR.comm.MsgSettingGlucose;
import info.nightscout.androidaps.plugins.PumpDanaR.comm.MsgSettingMaxValues;
import info.nightscout.androidaps.plugins.PumpDanaR.comm.MsgSettingMeal;
import info.nightscout.androidaps.plugins.PumpDanaR.comm.MsgSettingProfileRatios;
import info.nightscout.androidaps.plugins.PumpDanaR.comm.MsgSettingProfileRatiosAll;
import info.nightscout.androidaps.plugins.PumpDanaR.comm.MsgSettingPumpTime;
import info.nightscout.androidaps.plugins.PumpDanaR.comm.MsgSettingShippingInfo;
import info.nightscout.androidaps.plugins.PumpDanaR.comm.MsgStatus;
import info.nightscout.androidaps.plugins.PumpDanaR.comm.MsgStatusBasic;
import info.nightscout.androidaps.plugins.PumpDanaR.comm.MsgStatusBolusExtended;
import info.nightscout.androidaps.plugins.PumpDanaR.comm.MsgStatusTempBasal;
import info.nightscout.androidaps.plugins.PumpDanaR.comm.RecordTypes;
import info.nightscout.androidaps.plugins.PumpDanaR.events.EventDanaRNewStatus;
import info.nightscout.androidaps.plugins.PumpDanaR.services.AbstractSerialIOThread;
import info.nightscout.androidaps.queue.Callback;
import info.nightscout.utils.HardLimits;
import info.nightscout.utils.NSUpload;
import info.nightscout.utils.SP;
import info.nightscout.utils.ToastUtils;

public class BluetoothService extends Service {
    protected Logger log = LoggerFactory.getLogger(BluetoothService.class);

    protected IBinder mBinder = new BluetoothService.MyBinder();

    public ArrayAdapter<String> mBTArrayAdapter;

    public static final UUID BTMODULEUUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB"); // "random" unique identifier

    public BluetoothAdapter mBluetoothAdapter; //Bluetooth adapter connection
    public ConnectedThread mConnectedThread; // bluetooth background worker thread to send and receive data
    public BluetoothSocket mBTSocket = null; // bi-directional client-to-client data path

    public boolean mKeepDeviceConnected = false; //When true, device should always be connected
    protected Boolean mConnectionInProgress = false;
    private boolean mConfirmed;

    private static String GOT_OK = "BluetoothService.GOT_OK";



    protected Treatment mBolusingTreatment = null;




    @Override
    public void onCreate() {
        super.onCreate();
        log.debug("Service is at: onCreate");

        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter(); //Check bluetooth adapter availability
        if (mBluetoothAdapter == null) {
            log.error("No default Bluetooth adapter. Device likely does not support bluetooth.");
            MainApp.bus().post(new EventBluetoothPumpStatusChanged().EventPassStatus(EventBluetoothPumpStatusChanged.INVALID));
            return;
        }
        mBTArrayAdapter = new ArrayAdapter<>(this,android.R.layout.simple_list_item_1);

        activateBluetooth();
        registerLocalBroadcastReceiver();
        registerBus();
    }

    private void activateBluetooth(){
        if (mBluetoothAdapter.isEnabled()) { //Confirms that bluetooth is enabled
            log.debug("Bluetooth Adapter is already enabled.");
        } else {
            log.debug("Bluetooth adapter not enabled. Enabling.");
            mBluetoothAdapter.enable(); //Starting Bluetooth
        }
    }

    private void registerLocalBroadcastReceiver() {
        MainApp.instance().getApplicationContext().registerReceiver(receiver, new IntentFilter(BluetoothDevice.ACTION_ACL_CONNECTED));
        MainApp.instance().getApplicationContext().registerReceiver(receiver, new IntentFilter(BluetoothDevice.ACTION_ACL_DISCONNECTED));
        MainApp.instance().getApplicationContext().registerReceiver(receiver, new IntentFilter(BluetoothDevice.ACTION_FOUND));
    }

    protected BroadcastReceiver receiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
            String action = intent.getAction();
            if (BluetoothDevice.ACTION_FOUND.equals(action)){
                mBTArrayAdapter.add(device.getName() + "\n" + device.getAddress()); // add the name to the list
                mBTArrayAdapter.notifyDataSetChanged();
                log.debug("Device found: " + device.getName() + "; MAC " + device.getAddress());
                MainApp.bus().post(new EventBluetoothPumpStatusChanged().EventPassStatus(EventBluetoothPumpStatusChanged.DISCOVERING));
            }else if (BluetoothDevice.ACTION_ACL_DISCONNECTED.equals(action)) {
                log.debug("Device was disconnected " + device.getName());//Device was disconnected
                if (mKeepDeviceConnected) { //Connection dropped, reconnect!
                    MainApp.bus().post(new EventBluetoothPumpStatusChanged().EventPassStatus(EventBluetoothPumpStatusChanged.DROPPED));
                    final String address = device.getAddress();
                    if (mConnectedThread != null){mConnectedThread.cancel();}
                    if (mConnectionInProgress){return;}
                    new Thread(){
                        public void run() {
                            mConnectionInProgress = true;
                            boolean fail = false;
                            BluetoothDevice device = mBluetoothAdapter.getRemoteDevice(address);
                            try {
                                mBTSocket = createBluetoothSocket(device);
                            } catch (IOException e) {
                                fail = true;
                                log.error("Socket creating failed");
                            }
                            try { // Establish the Bluetooth socket connection.
                                mBTSocket.connect();
                            } catch (IOException e) {
                                try {
                                    fail = true;
                                    mBTSocket.close();
                                } catch (IOException e2) {
                                    //insert code to deal with this
                                    log.error("Socket creating failed");
                                }
                            }
                            if (fail == false) {
                                mConnectedThread = new ConnectedThread(mBTSocket);
                                mConnectedThread.start();
                                mConnectionInProgress = false;
                            } else {
                                MainApp.bus().post(new EventBluetoothPumpStatusChanged().EventPassStatus(EventBluetoothPumpStatusChanged.FAILED));
                                mConnectionInProgress = false;
                            }
                        }
                    }.start();
                    //Retry more then once!



                } else {
                    if (mConnectedThread != null){
                        mConnectedThread.cancel();
                    }
                    MainApp.bus().post(new EventBluetoothPumpStatusChanged().EventPassStatus(EventBluetoothPumpStatusChanged.DISCONNECTED));
                }
            } else if (BluetoothDevice.ACTION_ACL_CONNECTED.equals(action)){
                log.debug("Successfully Connected to: " + device.getName());//Device was disconnected
                MainApp.bus().post(new EventBluetoothPumpStatusChanged().EventBluetoothPumpStatusChanged(EventBluetoothPumpStatusChanged.CONNECTED,device.getName()));
            }
        }
    };

    private void registerBus() {
        try {
            MainApp.bus().unregister(this);
        } catch (RuntimeException x) {
            // Ignore
        }
        MainApp.bus().register(this);
    }

    @Override
    public IBinder onBind(Intent intent) {
        log.debug("Service is at: onBind");
        return mBinder;
    }

    @Override
    public void onRebind(Intent intent) {
        log.debug("Service is at: onRebind");
        super.onRebind(intent);
    }

    @Override
    public boolean onUnbind(Intent intent) {
        log.debug("Service is at: onUnbind");
        return true;
    }

    @Override
    public void onDestroy() {
        log.debug("Service is at: onDestroy");
        unregisterReceiver(receiver);
        if (mConnectedThread != null){
            mConnectedThread.cancel();
        }
        super.onDestroy();
    }

    public void discover(){
        // Check if the device is already discovering
        if (mBluetoothAdapter.isDiscovering()){
            log.debug("Stopping discovery");
            mBluetoothAdapter.cancelDiscovery();
            if (mBTSocket.isConnected()) {
                MainApp.bus().post(new EventBluetoothPumpStatusChanged().EventBluetoothPumpStatusChanged(EventBluetoothPumpStatusChanged.CONNECTED,mBTSocket.getRemoteDevice().toString()));
            } else {
                MainApp.bus().post(new EventBluetoothPumpStatusChanged().EventPassStatus(EventBluetoothPumpStatusChanged.DISCONNECTED));
            }
        } else {
            if (mBluetoothAdapter.isEnabled()) {
                log.debug("Starting discovery");
                mBTArrayAdapter.clear(); // clear items
                mBluetoothAdapter.startDiscovery();
                MainApp.bus().post(new EventBluetoothPumpStatusChanged().EventPassStatus(EventBluetoothPumpStatusChanged.DISCOVERING));
            } else {
                MainApp.bus().post(new EventBluetoothPumpStatusChanged().EventPassStatus(EventBluetoothPumpStatusChanged.INVALID));
            }
        }
    }

    public AdapterView.OnItemClickListener mDeviceClickListener = new AdapterView.OnItemClickListener() {
        public void onItemClick(AdapterView<?> av, View v, int arg2, long arg3) {
            if(!mBluetoothAdapter.isEnabled()) {
                MainApp.bus().post(new EventBluetoothPumpStatusChanged().EventPassStatus(EventBluetoothPumpStatusChanged.INVALID));
                return;
            }
            String info = ((TextView) v).getText().toString(); // Get the device MAC address, which is the last 17 chars in the View
            final String address = info.substring(info.length() - 17);
            // Spawn a new thread to avoid blocking the GUI one
            mBluetoothAdapter.cancelDiscovery();
            if (mConnectionInProgress)
                return;
            new Thread() {
                public void run() {
                    mConnectionInProgress = true;
                    boolean fail = false;
                    BluetoothDevice device = mBluetoothAdapter.getRemoteDevice(address);
                    try {
                        mBTSocket = createBluetoothSocket(device);
                    } catch (IOException e) {
                        fail = true;
                        log.error("Socket creating failed");
                    }
                    try { // Establish the Bluetooth socket connection.
                        mBTSocket.connect();
                    } catch (IOException e) {
                        try {
                            fail = true;
                            mBTSocket.close();
                        } catch (IOException e2) {
                            //insert code to deal with this
                            log.error("Socket creating failed");
                        }
                    }
                    if (fail == false) {
                        mConnectedThread = new ConnectedThread(mBTSocket);
                        mConnectedThread.start();
                        mConnectionInProgress = false;
                    } else {
                        MainApp.bus().post(new EventBluetoothPumpStatusChanged().EventPassStatus(EventBluetoothPumpStatusChanged.FAILED));
                        mConnectionInProgress = false;
                    }
                }
            }.start();
        }
    };

    private BluetoothSocket createBluetoothSocket(BluetoothDevice device) throws IOException {
        try {
            final Method m = device.getClass().getMethod("createInsecureRfcommSocketToServiceRecord", UUID.class);
            return (BluetoothSocket) m.invoke(device, BTMODULEUUID);
        } catch (Exception e) {
            log.error("Could not create Insecure RFComm Connection: " + e);
        }
        return  device.createRfcommSocketToServiceRecord(BTMODULEUUID);
    }

    public class ConnectedThread extends Thread {
        private final BluetoothSocket mmSocket;
        private final InputStream mmInStream;
        private final OutputStream mmOutStream;

        public ConnectedThread(BluetoothSocket socket) {
            mmSocket = socket;
            InputStream tmpIn = null;
            OutputStream tmpOut = null;

            // Get the input and output streams, using temp objects because
            // member streams are final
            try {
                tmpIn = socket.getInputStream();
                tmpOut = socket.getOutputStream();
            } catch (IOException e) { }

            mmInStream = tmpIn;
            mmOutStream = tmpOut;
        }

        public void run() {
            byte[] buffer = new byte[1024];  // buffer store for the stream
            int bytes; // bytes returned from read()
            // Keep listening to the InputStream until an exception occurs
            while (true) {
                try {
                    // Read from the InputStream
                    bytes = mmInStream.available();
                    if(bytes != 0) {
                        buffer = new byte[1024];
                        SystemClock.sleep(100); //pause and wait for rest of data. Adjust this depending on your sending speed.
                        bytes = mmInStream.available(); // how many bytes are ready to be read?
                        bytes = mmInStream.read(buffer, 0, bytes); // record how many bytes we actually read
                        bluetoothMessage(buffer, bytes);
                    }
                } catch (IOException e) {
                    e.printStackTrace();

                    break;
                }
            }
        }

        /* Call this from the main activity to send data to the remote device */
        public synchronized void write(String input) {
            if (!mmSocket.isConnected()) {
                log.error("Socket not connected on Bluetooth write");
                return;
            }
            byte[] bytes = input.getBytes(); //converts entered String into bytes
            try {
                mmOutStream.write(bytes);
            } catch (Exception e) {
                log.error("Service write exception: ", e);
            }
        }

        /* Call this from the main activity to shutdown the connection */
        public void cancel() {
            try {
                mmSocket.close();
            } catch (IOException e) {

            }
        }
    }

    private void bluetoothMessage(byte buffer[], int size){ //Handle inbound bluetooth messages
        mConnectedThread.run(); //Restart listener
        String readMessage = null;
        try {
            readMessage = new String(buffer, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        }
        if (readMessage != null){
            log.debug("Got message from Bluetooth: " + readMessage);
            if (readMessage.contains("OK")){
                Intent intent = new Intent();
                intent.setAction(GOT_OK);
                sendBroadcast(intent);
            } else if (readMessage.contains("what2")){






            } else {





            }
        } else {
            log.debug("Failed to read message from Bluetooth");
        }
    }

    private boolean confirmedMessage(String message){
        mConfirmed = false;
        MainApp.instance().getApplicationContext().registerReceiver(confirmTransmit, new IntentFilter(BluetoothService.GOT_OK));
        try {
            log.debug("Writing to Bluetooth: " + message);
            mConnectedThread.write(message);
        } catch (Exception e) {
            log.error("Unhandled exception", e);
        }
        new Thread(new Runnable() { //Spawn new thread to listen for confirmation
            private String myParam;
            public Runnable init(String myParam) {
                this.myParam = myParam;
                return this;
            }
            @Override
            public void run() {
                while(!mConfirmed){
                    SystemClock.sleep(10);
                    mConnectedThread.write(myParam);
                }
                cancel();
            }
            public void cancel() {
                unregisterReceiver(confirmTransmit);
            }
        }.init(message)).start();
        SystemClock.sleep(400);
        if (mConfirmed){
            mConfirmed = false;
            return true;
        } else {
            log.warn("Failed to get conformation from pump!");
            return false;
        }
    }

    protected BroadcastReceiver confirmTransmit = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (BluetoothService.GOT_OK.equals(action)){
                log.debug("Got conformation from Bluetooth device");
                mConfirmed = true;
            }
        }
    };

    public BluetoothService() {}

    public class MyBinder extends Binder {
        public BluetoothService getService() {
            return BluetoothService.this;
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY;
    }

    public boolean isConnected() {
        return mBTSocket != null && mBTSocket.isConnected();
    }

    public boolean isConnecting() {
        return mConnectionInProgress;
    }

    public void disconnect() {
        if (mConnectedThread != null) {
            mConnectedThread.cancel();
        }
    }

    public void stopConnecting() {
        if (mConnectedThread != null) {
            mConnectedThread.cancel();
        }
    }

    public boolean bolusStop() {
        String message = "stopBolus";
        log.debug("Pump action: " + message);
        return (mBTSocket.isConnected() && confirmedMessage("EnactPumpResult|" + message));
    }

    public PumpEnactResult loadHistory(byte type) {
        PumpEnactResult result = new PumpEnactResult();
        if (!isConnected()) return result;


        /*


        MessageBase msg = null;
        switch (type) {
            case RecordTypes.RECORD_TYPE_ALARM:
                msg = new MsgHistoryAlarm();
                break;
            case RecordTypes.RECORD_TYPE_BASALHOUR:
                msg = new MsgHistoryBasalHour();
                break;
            case RecordTypes.RECORD_TYPE_BOLUS:
                msg = new MsgHistoryBolus();
                break;
            case RecordTypes.RECORD_TYPE_CARBO:
                msg = new MsgHistoryCarbo();
                break;
            case RecordTypes.RECORD_TYPE_DAILY:
                msg = new MsgHistoryDailyInsulin();
                break;
            case RecordTypes.RECORD_TYPE_ERROR:
                msg = new MsgHistoryError();
                break;
            case RecordTypes.RECORD_TYPE_GLUCOSE:
                msg = new MsgHistoryGlucose();
                break;
            case RecordTypes.RECORD_TYPE_REFILL:
                msg = new MsgHistoryRefill();
                break;
            case RecordTypes.RECORD_TYPE_SUSPEND:
                msg = new MsgHistorySuspend();
                break;
        }
        MsgHistoryDone done = new MsgHistoryDone();
        mSerialIOThread.sendMessage(new MsgPCCommStart());
        SystemClock.sleep(400);
        mSerialIOThread.sendMessage(msg);
        while (!done.received && mBTSocket.isConnected()) {
            SystemClock.sleep(100);
        }
        SystemClock.sleep(200);
        mSerialIOThread.sendMessage(new MsgPCCommStop());

        */


        result.success = true;
        result.comment = "OK";
        return result;
    }

    @Subscribe
    public void onStatusEvent(final EventPreferenceChange pch) {
        if (mConnectedThread != null) {
            mConnectedThread.cancel();
        }
    }

    public void getPumpStatus() {

        /*

        try {
            MainApp.bus().post(new EventPumpStatusChanged(MainApp.sResources.getString(R.string.gettingpumpstatus)));
            MsgStatus statusMsg = new MsgStatus();
            MsgStatusBasic statusBasicMsg = new MsgStatusBasic();
            MsgStatusTempBasal tempStatusMsg = new MsgStatusTempBasal();
            MsgStatusBolusExtended exStatusMsg = new MsgStatusBolusExtended();
            MsgCheckValue checkValue = new MsgCheckValue();

            if (mDanaRPump.isNewPump) {
                mSerialIOThread.sendMessage(checkValue);
                if (!checkValue.received) {
                    return;
                }
            }

            mSerialIOThread.sendMessage(statusMsg);
            mSerialIOThread.sendMessage(statusBasicMsg);
            MainApp.bus().post(new EventPumpStatusChanged(MainApp.sResources.getString(R.string.gettingtempbasalstatus)));
            mSerialIOThread.sendMessage(tempStatusMsg);
            MainApp.bus().post(new EventPumpStatusChanged(MainApp.sResources.getString(R.string.gettingextendedbolusstatus)));
            mSerialIOThread.sendMessage(exStatusMsg);
            MainApp.bus().post(new EventPumpStatusChanged(MainApp.sResources.getString(R.string.gettingbolusstatus)));

            long now = System.currentTimeMillis();
            if (mDanaRPump.lastSettingsRead + 60 * 60 * 1000L < now || !MainApp.getSpecificPlugin(DanaRPlugin.class).isInitialized()) {
                MainApp.bus().post(new EventPumpStatusChanged(MainApp.sResources.getString(R.string.gettingpumpsettings)));
                mSerialIOThread.sendMessage(new MsgSettingShippingInfo());
                mSerialIOThread.sendMessage(new MsgSettingActiveProfile());
                mSerialIOThread.sendMessage(new MsgSettingMeal());
                mSerialIOThread.sendMessage(new MsgSettingBasal());
                //0x3201
                mSerialIOThread.sendMessage(new MsgSettingMaxValues());
                mSerialIOThread.sendMessage(new MsgSettingGlucose());
                mSerialIOThread.sendMessage(new MsgSettingActiveProfile());
                mSerialIOThread.sendMessage(new MsgSettingProfileRatios());
                mSerialIOThread.sendMessage(new MsgSettingProfileRatiosAll());
                MainApp.bus().post(new EventPumpStatusChanged(MainApp.sResources.getString(R.string.gettingpumptime)));
                mSerialIOThread.sendMessage(new MsgSettingPumpTime());
                long timeDiff = (mDanaRPump.pumpTime.getTime() - System.currentTimeMillis()) / 1000L;
                log.debug("Pump time difference: " + timeDiff + " seconds");
                if (Math.abs(timeDiff) > 10) {
                    mSerialIOThread.sendMessage(new MsgSetTime(new Date()));
                    mSerialIOThread.sendMessage(new MsgSettingPumpTime());
                    timeDiff = (mDanaRPump.pumpTime.getTime() - System.currentTimeMillis()) / 1000L;
                    log.debug("Pump time difference: " + timeDiff + " seconds");
                }
                mDanaRPump.lastSettingsRead = now;
            }

            mDanaRPump.lastConnection = now;
            MainApp.bus().post(new EventDanaRNewStatus());
            MainApp.bus().post(new EventInitializationChanged());
            NSUpload.uploadDeviceStatus();
            if (mDanaRPump.dailyTotalUnits > mDanaRPump.maxDailyTotalUnits * Constants.dailyLimitWarning) {
                log.debug("Approaching daily limit: " + mDanaRPump.dailyTotalUnits + "/" + mDanaRPump.maxDailyTotalUnits);
                Notification reportFail = new Notification(Notification.APPROACHING_DAILY_LIMIT, MainApp.sResources.getString(R.string.approachingdailylimit), Notification.URGENT);
                MainApp.bus().post(new EventNewNotification(reportFail));
                NSUpload.uploadError(MainApp.sResources.getString(R.string.approachingdailylimit) + ": " + mDanaRPump.dailyTotalUnits + "/" + mDanaRPump.maxDailyTotalUnits + "U");
            }
        } catch (Exception e) {
            log.error("Unhandled exception", e);
        }

        */
    }

    public boolean tempBasal(int percent, int durationInHours) {
        //HARDCODED LIMITS
        if (percent < 0) percent = 0;
        if (percent > 200) percent = 200;
        if (durationInHours < 1) durationInHours = 1;
        if (durationInHours > 24) durationInHours = 24;
        String message = "tempBasal|" + "percent=" + Integer.toString(percent) + "|durationInHours=" + Integer.toString(durationInHours);
        log.debug("Pump action: " + message);
        return (mBTSocket.isConnected() && confirmedMessage("EnactPumpResult|" + message));
    }

    public boolean tempBasalStop() {
        String message = "stopTempBasal";
        log.debug("Pump action: " + message);
        return (mBTSocket.isConnected() && confirmedMessage("EnactPumpResult|" + message));
    }

    public boolean extendedBolus(double insulin, int durationInHalfHours) {
        // HARDCODED LIMITS
        if (durationInHalfHours < 1) durationInHalfHours = 1;
        if (durationInHalfHours > 16) durationInHalfHours = 16;
        insulin = MainApp.getConfigBuilder().applyBolusConstraints(insulin);
        if (insulin < 0d) insulin = 0d;
        if (insulin > HardLimits.maxBolus()) insulin = HardLimits.maxBolus();
        String message = "extendedBolus|" + "insulin=" + Double.toString((((int) (insulin * 100)) / 100d)) + "U|durationInHours=" + Integer.toString(durationInHalfHours);
        log.debug("Pump action: " + message);
        return (mBTSocket.isConnected() && confirmedMessage("EnactPumpResult|" + message));
    }

    public boolean extendedBolusStop() {
        String message = "stopextendedBolus";
        log.debug("Pump action: " + message);
        return (mBTSocket.isConnected() && confirmedMessage("EnactPumpResult|" + message));
    }

    public PumpEnactResult loadEvents() {
        return null;
    }

    public boolean bolus(double amount, int carbs, long carbtime, final Treatment t) {

        /*

        // HARDCODED LIMIT
        amount = MainApp.getConfigBuilder().applyBolusConstraints(amount);
        if (amount < 0) amount = 0d;
        if (amount > HardLimits.maxBolus()) amount = HardLimits.maxBolus();

        String message = "bolus|" + "insulin=" + Double.toString((((int) (amount * 100)) / 100d)) + "U|durationInHours=" + Integer.toString(durationInHalfHours);
        log.debug("Pump action: " + message);
        return (mBTSocket.isConnected() && confirmedMessage("EnactPumpResult|" + message));




        if (!isConnected()) return false;
        if (BolusProgressDialog.stopPressed) return false;

        mBolusingTreatment = t;
        int preferencesSpeed = SP.getInt(R.string.key_danars_bolusspeed, 0);
        MessageBase start;
        if (preferencesSpeed == 0)
            start = new MsgBolusStart(amount);
        else
            start = new MsgBolusStartWithSpeed(amount, preferencesSpeed);
        MsgBolusStop stop = new MsgBolusStop(amount, t);

        if (carbs > 0) {
            mSerialIOThread.sendMessage(new MsgSetCarbsEntry(carbtime, carbs));
        }

        MsgBolusProgress progress = new MsgBolusProgress(amount, t); // initialize static variables
        long bolusStart = System.currentTimeMillis();

        if (!stop.stopped) {
            mSerialIOThread.sendMessage(start);
        } else {
            t.insulin = 0d;
            return false;
        }
        while (!stop.stopped && !start.failed) {
            SystemClock.sleep(100);
            if ((System.currentTimeMillis() - progress.lastReceive) > 15 * 1000L) { // if i didn't receive status for more than 15 sec expecting broken comm
                stop.stopped = true;
                stop.forced = true;
                log.debug("Communication stopped");
            }
        }
        SystemClock.sleep(300);

        EventOverviewBolusProgress bolusingEvent = EventOverviewBolusProgress.getInstance();
        bolusingEvent.t = t;
        bolusingEvent.percent = 99;

        mBolusingTreatment = null;

        int speed = 12;
        switch (preferencesSpeed) {
            case 0:
                speed = 12;
                break;
            case 1:
                speed = 30;
                break;
            case 2:
                speed = 60;
                break;
        }
        // try to find real amount if bolusing was interrupted or comm failed
        if (t.insulin != amount) {
            //disconnect("bolusingInterrupted");
            long bolusDurationInMSec = (long) (amount * speed * 1000);
            long expectedEnd = bolusStart + bolusDurationInMSec + 3000;

            while (System.currentTimeMillis() < expectedEnd) {
                long waitTime = expectedEnd - System.currentTimeMillis();
                bolusingEvent.status = String.format(MainApp.sResources.getString(R.string.waitingforestimatedbolusend), waitTime / 1000);
                MainApp.bus().post(bolusingEvent);
                SystemClock.sleep(1000);
            }

            final Object o = new Object();
            synchronized(o) {
                ConfigBuilderPlugin.getCommandQueue().independentConnect("bolusingInterrupted", new Callback() {
                    @Override
                    public void run() {
                        if (mDanaRPump.lastBolusTime.getTime() > System.currentTimeMillis() - 60 * 1000L) { // last bolus max 1 min old
                            t.insulin = mDanaRPump.lastBolusAmount;
                            log.debug("Used bolus amount from history: " + mDanaRPump.lastBolusAmount);
                        } else {
                            log.debug("Bolus amount in history too old: " + mDanaRPump.lastBolusTime.toLocaleString());
                        }
                        synchronized (o) {
                            o.notify();
                        }
                    }
                });
                try {
                    o.wait();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        } else {
            ConfigBuilderPlugin.getCommandQueue().readStatus("bolusOK", null);
        }


        */

        return true;
    }

    public boolean carbsEntry(int amount) {


        /*
        if (!isConnected()) return false;
        MsgSetCarbsEntry msg = new MsgSetCarbsEntry(System.currentTimeMillis(), amount);
        mSerialIOThread.sendMessage(msg);

       */


        return true;

    }

    public boolean highTempBasal(int percent) {
        return false;
    }

    public boolean updateBasalsInPump(final Profile profile) {



        /*

        if (!isConnected()) return false;
        MainApp.bus().post(new EventPumpStatusChanged(MainApp.sResources.getString(R.string.updatingbasalrates)));
        double[] basal = DanaRPump.buildDanaRProfileRecord(profile);
        MsgSetBasalProfile msgSet = new MsgSetBasalProfile((byte) 0, basal);
        mSerialIOThread.sendMessage(msgSet);
        MsgSetActivateBasalProfile msgActivate = new MsgSetActivateBasalProfile((byte) 0);
        mSerialIOThread.sendMessage(msgActivate);
        mDanaRPump.lastSettingsRead = 0; // force read full settings
        getPumpStatus();
        MainApp.bus().post(new EventPumpStatusChanged(EventPumpStatusChanged.DISCONNECTING));

        */


        return true;
    }

    @Subscribe
    public void onStatusEvent(EventAppExit event) {
        log.debug("EventAppExit received");
        if (mConnectedThread != null){mConnectedThread.cancel();}
        MainApp.instance().getApplicationContext().unregisterReceiver(receiver);
        stopSelf();
        log.debug("EventAppExit finished");
    }
}