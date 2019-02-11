package info.nightscout.androidaps.plugins.PumpBluetooth.services;

import android.annotation.SuppressLint;
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
import android.widget.Toast;
import android.os.Handler;
import android.os.Message;

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
import info.nightscout.androidaps.events.EventAppExit;
import info.nightscout.androidaps.events.EventPreferenceChange;
import info.nightscout.androidaps.events.EventPumpStatusChanged;
import info.nightscout.androidaps.interfaces.Constraint;
import info.nightscout.androidaps.plugins.PumpBluetooth.BluetoothPumpPlugin;
import info.nightscout.androidaps.plugins.PumpBluetooth.events.EventBluetoothPumpUpdateGui;
import info.nightscout.utils.HardLimits;
import info.nightscout.utils.SP;
import info.nightscout.utils.ToastUtils;

public class BluetoothService extends Service {
    protected Logger log = LoggerFactory.getLogger(BluetoothService.class);

    protected IBinder mBinder = new BluetoothService.ServiceBinder();

    public static final int MESSAGE_READ=0;

    public ArrayAdapter<String> mBTArrayAdapter;

    public static final UUID BTMODULEUUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB"); // "random" unique identifier

    public BluetoothAdapter mBluetoothAdapter; //Bluetooth adapter connection
    public info.nightscout.androidaps.plugins.PumpBluetooth.services.SerialConnectedThread mConnectedThread; // bluetooth background worker thread to send and receive data
    public BluetoothSocket mBTSocket = null; // bi-directional client-to-client data path
    protected BluetoothDevice mBTDevice;
    public String mDevName;

    private Boolean mKeepDeviceConnected = true; //When true, device should always be connected
    protected Boolean mConnectionInProgress = false;
    protected Boolean mConnectionFailed = false;
    private boolean mConfirmed;

    private static String GOT_OK = "BluetoothService.GOT_OK";

    //protected Treatment mBolusingTreatment = null;

    @Override
    public void onCreate() {
        super.onCreate();
        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter(); //Check bluetooth adapter availability
        if (mBluetoothAdapter == null) {
            MainApp.bus().post(new EventBluetoothPumpUpdateGui());
            return;
        }
        mBTArrayAdapter = new ArrayAdapter<>(this,android.R.layout.simple_list_item_1);
        activateBluetooth();
        registerLocalBroadcastReceiver();
        registerBus();
    }

    private void activateBluetooth(){
        if (mBluetoothAdapter.isEnabled()) { //Confirms that bluetooth is enabled
        } else {
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
            if (BluetoothDevice.ACTION_ACL_DISCONNECTED.equals(action)) {
                log.debug("Device was disconnected: " + device.getName());//Device was disconnected
                BluetoothPumpPlugin bluetoothPump = BluetoothPumpPlugin.getPlugin();
                bluetoothPump.pumpNotConnected(true);
                if (mKeepDeviceConnected) { //Connection dropped, reconnect!
                    MainApp.bus().post(new EventBluetoothPumpUpdateGui());
                    if (mConnectionInProgress){return;}
                    connect();
                }
            } else if (BluetoothDevice.ACTION_ACL_CONNECTED.equals(action)){
                log.debug("Successfully connected to: " + device.getName());
                MainApp.bus().post(new EventBluetoothPumpUpdateGui());
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
        return mBinder;
    }

    @Override
    public void onRebind(Intent intent) {
        super.onRebind(intent);
    }

    @Override
    public boolean onUnbind(Intent intent) {
        return true;
    }

    @Override
    public void onDestroy() {
        unregisterReceiver(BluetoothReceiver);
        if (mConnectedThread != null){
            mConnectedThread.disconnect();
        }
        super.onDestroy();
    }

    /*
    @SuppressLint("HandlerLeak")
    public Handler mHandler=new Handler()
    {
        @Override
        public void handleMessage(Message msg_type) {
            super.handleMessage(msg_type);

            switch (msg_type.what){
                case MESSAGE_READ:

                    byte[] readbuf=(byte[])msg_type.obj;
                    String string_recieved=new String(readbuf);

                    //do some task based on recieved string

                    log.debug("Got string from BluetoothDevice");
                    log.debug("Content " + string_recieved);

                    break;

                case MESSAGE_WRITE:

                    if(msg_type.obj!=null){
                        ConnectedThread connectedThread=new ConnectedThread((BluetoothSocket)msg_type.obj);
                        connectedThread.write(bluetooth_message.getBytes());

                    }
                    break;

                case CONNECTED:
                    Toast.makeText(getApplicationContext(),"Connected",Toast.LENGTH_SHORT).show();
                    break;

                case CONNECTING:
                    Toast.makeText(getApplicationContext(),"Connecting...",Toast.LENGTH_SHORT).show();
                    break;

                case NO_SOCKET_FOUND:
                    Toast.makeText(getApplicationContext(),"No socket found",Toast.LENGTH_SHORT).show();
                    break;

            }
        }
    };
*/











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
        if (mConnectedThread == null){
            log.error("mConnectedThread is null on confirmedMessage at BluetoothService");
            return false;
        }
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
            if (BluetoothService.GOT_OK.equals(action)){
                log.debug("Got conformation from Bluetooth device");
                mConfirmed = true;
            }
        }
    };

    public class ServiceBinder extends Binder {
        public BluetoothService getService() {
            return BluetoothService.this;
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY;
    }

    public void connect() {
        new Thread(new Runnable() {
            @Override
            public void run() {
                mConnectionInProgress = true;
                mConnectionFailed = false;
                getBTSocketForSelectedPump();
                if (mBTSocket == null || mBTDevice == null) {
                    mConnectionInProgress = false;
                    return; // Device not found
                }
                try {
                    mBTSocket.connect();
                } catch (IOException e) {
                    mConnectionFailed = true;
                    if (e.getMessage().contains("socket closed")) {
                        log.error("Unhandled exception", e);
                    }
                    try {
                        mBTSocket.close();
                    } catch (IOException e2) {
                        log.error("Socket creating failed");
                    }
                }
                if (isConnected()) {
                    if (mConnectedThread != null) {
                        mConnectedThread.disconnect();
                    }
                }
                if (!mConnectionFailed) {
                    mConnectedThread = new info.nightscout.androidaps.plugins.PumpBluetooth.services.SerialConnectedThread(mBTSocket);
                    if (mConnectedThread.getState() == Thread.State.NEW){
                        mConnectedThread.start();
                    }
                    MainApp.bus().post(new EventPumpStatusChanged(EventPumpStatusChanged.CONNECTED, 0));
                    MainApp.bus().post(new EventBluetoothPumpUpdateGui());
                } else {
                    log.debug("Couldn't connect to client");
                    MainApp.bus().post(new EventBluetoothPumpUpdateGui());
                    SystemClock.sleep(5000);
                    connect();
                }
                mConnectionInProgress = false;
                confirmedMessage("Ping");
            }
        }).start();
    }

    protected void getBTSocketForSelectedPump() {
        mDevName = SP.getString(MainApp.sResources.getString(R.string.key_bluetooth_bt_name), "");
        log.debug("Connecting to device: ", mDevName);
        BluetoothAdapter bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if (bluetoothAdapter != null) {
            Set<BluetoothDevice> bondedDevices = bluetoothAdapter.getBondedDevices();
            for (BluetoothDevice device : bondedDevices) {
                if (mDevName.equals(device.getName())) {
                    mBTDevice = device;
                    log.debug("Found device: ", device, ". Connecting...");
                    try {
                        mBTSocket = createBluetoothSocket(device);
                    } catch (IOException e) {
                        mConnectionFailed = true;
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
        if (mConnectedThread != null) {
            mConnectedThread.disconnect();
        }
    }

    public void stopConnecting() {
        if (mConnectedThread != null) {
            mConnectedThread.disconnect();
        }
    }

    @Subscribe
    public void onStatusEvent(final EventPreferenceChange pch) {
        if (mConnectedThread != null) {
            //mConnectedThread.disconnect();
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
        insulin = MainApp.getConstraintChecker().applyBolusConstraints(new Constraint<>(insulin)).value();
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

    @Subscribe
    public void onStatusEvent(EventAppExit event) {
        log.debug("EventAppExit received");
        if (mConnectedThread != null){mConnectedThread.disconnect();}
        MainApp.instance().getApplicationContext().unregisterReceiver(BluetoothReceiver);
        stopSelf();
        log.debug("EventAppExit finished");
    }
}