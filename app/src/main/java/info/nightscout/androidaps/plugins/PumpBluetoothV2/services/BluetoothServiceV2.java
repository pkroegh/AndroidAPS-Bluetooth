package info.nightscout.androidaps.plugins.PumpBluetoothV2.services;

import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Binder;
import android.os.IBinder;
import android.os.SystemClock;
import android.widget.ArrayAdapter;

import com.squareup.otto.Subscribe;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.lang.reflect.Method;
import java.util.Set;
import java.util.UUID;

import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.R;
import info.nightscout.androidaps.data.Profile;
import info.nightscout.androidaps.data.PumpEnactResult;
import info.nightscout.androidaps.db.Treatment;
import info.nightscout.androidaps.events.EventAppExit;
import info.nightscout.androidaps.events.EventPreferenceChange;
import info.nightscout.androidaps.events.EventPumpStatusChanged;
import info.nightscout.androidaps.plugins.ConfigBuilder.ConfigBuilderPlugin;
import info.nightscout.androidaps.plugins.Overview.Dialogs.BolusProgressDialog;
import info.nightscout.androidaps.plugins.Overview.events.EventOverviewBolusProgress;
import info.nightscout.androidaps.plugins.PumpBluetooth.BluetoothPump;
import info.nightscout.androidaps.plugins.PumpBluetooth.events.EventBluetoothPumpStatusChanged;
import info.nightscout.androidaps.plugins.PumpBluetooth.services.BluetoothService;
import info.nightscout.androidaps.plugins.PumpBluetooth.services.SerialConnectedThread;
import info.nightscout.androidaps.queue.Callback;
import info.nightscout.utils.HardLimits;
import info.nightscout.utils.SP;
import info.nightscout.utils.ToastUtils;

public class BluetoothServiceV2 extends Service {
    protected Logger log = LoggerFactory.getLogger(BluetoothService.class);

    protected IBinder mBinder = new BluetoothServiceV2.MyBinder();

    public ArrayAdapter<String> mBTArrayAdapter;

    public static final UUID BTMODULEUUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB"); // "random" unique identifier

    public BluetoothAdapter mBluetoothAdapter; //Bluetooth adapter connection
    public SerialConnectedThread mConnectedThread; // bluetooth background worker thread to send and receive data
    public BluetoothSocket mBTSocket = null; // bi-directional client-to-client data path
    protected BluetoothDevice mBTDevice;
    public String mDevName;

    protected BluetoothPump pump = BluetoothPump.getInstance();

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
        MainApp.instance().getApplicationContext().registerReceiver(BluetoothReceiver, new IntentFilter(BluetoothDevice.ACTION_ACL_CONNECTED));
        MainApp.instance().getApplicationContext().registerReceiver(BluetoothReceiver, new IntentFilter(BluetoothDevice.ACTION_ACL_DISCONNECTED));
        MainApp.instance().getApplicationContext().registerReceiver(BluetoothReceiver, new IntentFilter(BluetoothDevice.ACTION_FOUND));
    }

    protected BroadcastReceiver BluetoothReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
            String action = intent.getAction();
            /*
            if (BluetoothDevice.ACTION_ACL_DISCONNECTED.equals(action)) {
                log.debug("Device was disconnected: " + device.getName());//Device was disconnected
                if (mKeepDeviceConnected) { //Connection dropped, reconnect!
                    log.debug("Reconnecting to device: " + device.getName());
                    MainApp.bus().post(new EventBluetoothPumpStatusChanged().EventPassStatus(EventBluetoothPumpStatusChanged.DROPPED));
                    final String address = device.getAddress();
                    if (mConnectedThread != null){mConnectedThread.dicsonnect();}
                    if (mConnectionInProgress){return;}
                    new Thread(){
                        public void run() {
                            log.debug("Attempting to reconnect to device: " + address);
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
                                mConnectedThread = new SerialConnectedThread(mBTSocket);
                                mConnectedThread.start();
                                MainApp.bus().post(new EventBluetoothPumpStatusChanged().EventPassStatus(EventBluetoothPumpStatusChanged.CONNECTED));
                                mConnectionInProgress = false;
                            } else {
                                MainApp.bus().post(new EventBluetoothPumpStatusChanged().EventPassStatus(EventBluetoothPumpStatusChanged.FAILED));
                                mConnectionInProgress = false;
                                SystemClock.sleep(5000);
                                run();
                            }
                        }
                    }.start();
                    //Retry more then once!
                } else {
                    if (mConnectedThread != null){
                        mConnectedThread.disconnect();
                    }
                    MainApp.bus().post(new EventBluetoothPumpStatusChanged().EventPassStatus(EventBluetoothPumpStatusChanged.DISCONNECTED));
                }
            } else
            */
            if (BluetoothDevice.ACTION_ACL_CONNECTED.equals(action)){
                log.debug("Successfully connected to: " + device.getName());
                MainApp.bus().post(new EventBluetoothPumpStatusChanged().EventPassStatus(EventBluetoothPumpStatusChanged.CONNECTED));
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
        unregisterReceiver(BluetoothReceiver);
        if (mConnectedThread != null){
            mConnectedThread.disconnect();
        }
        super.onDestroy();
    }

    private void bluetoothMessage(byte buffer[], int size){ //Handle inbound bluetooth messages
        String readMessage = null;
        try {
            readMessage = new String(buffer, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        }
        if (readMessage != null){
            log.debug("Got message from Bluetooth: " + readMessage);
            if (readMessage.contains("OK")){
                log.debug("Pump confirmed message");
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

    public boolean confirmedMessage(String message){
        mConfirmed = false;
        //MainApp.instance().getApplicationContext().registerReceiver(confirmTransmit, new IntentFilter(BluetoothService.GOT_OK));
        try {
            log.debug("Writing to Bluetooth: " + message);
            mConnectedThread.sendMessage(message + "\n");
        } catch (Exception e) {
            log.error("Unhandled exception", e);
            return false;
        }

        return true;

        /*
        new Thread(new Runnable() { //Spawn new thread to listen for confirmation
            private String myParam;
            public Runnable init(String myParam) {
                this.myParam = myParam;
                return this;
            }
            @Override
            public void run() {
                while(!mConfirmed){
                    SystemClock.sleep(1000);
                    //mConnectedThread.sendMessage(myParam + "\n");
                }
                cancel();
            }
            public void cancel() {
                //unregisterReceiver(confirmTransmit);
            }
        }.init(message)).start();
        SystemClock.sleep(1000);
        if (mConfirmed){
            mConfirmed = false;
            return true;
        } else {
            log.warn("Failed to get conformation from pump!");
            return false;
        }
        */
    }

    protected BroadcastReceiver confirmTransmit = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (BluetoothServiceV2.GOT_OK.equals(action)){
                log.debug("Got conformation from Bluetooth device");
                mConfirmed = true;
            }
        }
    };

    public BluetoothServiceV2() {}

    public class MyBinder extends Binder {
        public BluetoothServiceV2 getService() {
            return BluetoothServiceV2.this;
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY;
    }

    public void connect() {

        /*

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
*/
        /*
        if (mDanaRPump.password != -1 && mDanaRPump.password != SP.getInt(R.string.key_danar_password, -1)) {
            ToastUtils.showToastInUiThread(MainApp.instance().getApplicationContext(), MainApp.sResources.getString(R.string.wrongpumppassword), R.raw.error);
            return;
        }
        */

        if (mConnectionInProgress)
            return;

        new Thread(new Runnable() {
            @Override
            public void run() {
                mConnectionInProgress = true;
                getBTSocketForSelectedPump();
                if (mBTSocket == null || mBTDevice == null) {
                    mConnectionInProgress = false;
                    return; // Device not found
                }
                try {
                    mBTSocket.connect();
                } catch (IOException e) {
                    //log.error("Unhandled exception", e);
                    if (e.getMessage().contains("socket closed")) {
                        log.error("Unhandled exception", e);
                    }
                }
                if (isConnected()) {
                    if (mConnectedThread != null) {
                        mConnectedThread.disconnect();
                    }
                    mConnectedThread = new SerialConnectedThread(mBTSocket);
                    //mConnectedThread.start();
                    MainApp.bus().post(new EventPumpStatusChanged(EventPumpStatusChanged.CONNECTED, 0));
                }
                mConnectionInProgress = false;
            }
        }).start();

        //mKeepDeviceConnected = true;

    }

    protected void getBTSocketForSelectedPump() {
        mDevName = SP.getString(MainApp.sResources.getString(R.string.key_bluetooth_bt_name), "");
        BluetoothAdapter bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        log.debug("Connecting to device: ", mDevName);
        if (bluetoothAdapter != null) {
            Set<BluetoothDevice> bondedDevices = bluetoothAdapter.getBondedDevices();
            for (BluetoothDevice device : bondedDevices) {
                if (mDevName.equals(device.getName())) {
                    mBTDevice = device;
                    log.debug("Found device: ", device, ". Connecting...");
                    try {
                        mBTSocket = createBluetoothSocket(device);
                    } catch (IOException e) {
                        log.error("Error creating socket: ", e);
                    }
                    break;
                }
            }
        } else {
            ToastUtils.showToastInUiThread(MainApp.instance().getApplicationContext(), MainApp.sResources.getString(R.string.nobtadapter));
        }
        if (mBTDevice == null) {
            ToastUtils.showToastInUiThread(MainApp.instance().getApplicationContext(), MainApp.sResources.getString(R.string.devicenotfound));
        }
    }

    private BluetoothSocket createBluetoothSocket(BluetoothDevice device) throws IOException {
        try {
            final Method m = device.getClass().getMethod("createInsecureRfcommSocketToServiceRecord", UUID.class);
            return (BluetoothSocket) m.invoke(device, BTMODULEUUID);
        } catch (Exception e) {
            log.error("Could not create Insecure RFComm Connection: " + e);
        }
        return  device.createRfcommSocketToServiceRecord(BTMODULEUUID);
    }

    public boolean isConnected() {
        return mBTSocket != null && mBTSocket.isConnected();
    }

    public boolean isConnecting() {
        return mConnectionInProgress;
    }

    public void disconnect() {
        //if (mConnectedThread != null) {
        //    mConnectedThread.disconnect();
        //}
    }

    public void stopConnecting() {
        //if (mConnectedThread != null) {
        //    mConnectedThread.disconnect();
        //}
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
            mConnectedThread.disconnect();
        }
    }

    public void getPumpStatus() {
        try {
            if (pump.isNewPump) {
                pump.isNewPump = false;
                /*
                mConnectedThread.sendMessage(checkValue);
                if (!checkValue.received) {
                    return;
                }
                */
            }
            log.debug("Getting pump status");
            confirmedMessage("getPumpStatus");
        } catch (Exception e) {
            log.error("Unhandled exception", e);
        }
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
        */

        if (!isConnected()) return false;
        if (BolusProgressDialog.stopPressed) return false;

        mBolusingTreatment = t;

        /*
        int preferencesSpeed = SP.getInt(R.string.key_danars_bolusspeed, 0);
        MessageBase start;
        if (preferencesSpeed == 0)
            start = new MsgBolusStart(amount);
        else
            start = new MsgBolusStartWithSpeed(amount, preferencesSpeed);
        MsgBolusStop stop = new MsgBolusStop(amount, t);
        */

        if (carbs > 0) {
            confirmedMessage("carbs: " + carbs + " time: " + carbtime);
            //mSerialIOThread.sendMessage(new MsgSetCarbsEntry(carbtime, carbs));
        }

        //MsgBolusProgress progress = new MsgBolusProgress(amount, t); // initialize static variables
        long bolusStart = System.currentTimeMillis();

        /*
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
        */

        EventOverviewBolusProgress bolusingEvent = EventOverviewBolusProgress.getInstance();
        bolusingEvent.t = t;
        bolusingEvent.percent = 99;

        mBolusingTreatment = null;

        int speed = 12;
        /*
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
        */
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
                        if (pump.lastBolusTime.getTime() > System.currentTimeMillis() - 60 * 1000L) { // last bolus max 1 min old
                            t.insulin = pump.lastBolusAmount;
                            log.debug("Used bolus amount from history: " + pump.lastBolusAmount);
                        } else {
                            log.debug("Bolus amount in history too old: " + pump.lastBolusTime.toLocaleString());
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
        return true;
    }

    public boolean carbsEntry(int amount) {
        if (!isConnected()) return false;
        confirmedMessage("carbsEntry: " + amount);

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
        if (!isConnected()) return false;
        MainApp.bus().post(new EventPumpStatusChanged(MainApp.sResources.getString(R.string.updatingbasalrates)));
        double[] basal = BluetoothPump.buildBluetootPumpProfileRecord(profile);

        log.debug("updateBasalsInPump got basal: " + basal);
        confirmedMessage("Basal: " + basal);
        pump.lastSettingsRead = 0;
        /*
        MsgSetBasalProfile msgSet = new MsgSetBasalProfile((byte) 0, basal);
        mSerialIOThread.sendMessage(msgSet);
        MsgSetActivateBasalProfile msgActivate = new MsgSetActivateBasalProfile((byte) 0);
        mSerialIOThread.sendMessage(msgActivate);
        mDanaRPump.lastSettingsRead = 0; // force read full settings
        */

        getPumpStatus();
        MainApp.bus().post(new EventPumpStatusChanged(EventPumpStatusChanged.DISCONNECTING));
        return true;
    }

    @Subscribe
    public void onStatusEvent(EventAppExit event) {
        log.debug("EventAppExit received");
        if (mConnectedThread != null){mConnectedThread.disconnect();}
        MainApp.instance().getApplicationContext().unregisterReceiver(BluetoothReceiver);
        stopSelf();
        log.debug("EventAppExit finished");
    }
}