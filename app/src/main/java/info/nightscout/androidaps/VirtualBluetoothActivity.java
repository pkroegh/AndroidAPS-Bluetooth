package info.nightscout.androidaps;

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.IBinder;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import info.nightscout.androidaps.Services.BluetoothService;
import info.nightscout.androidaps.Services.BluetoothService.MyBinder;

public class VirtualBluetoothActivity extends AppCompatActivity {
    //Debug tag - Used in log
    private static final String TAG = "BluetoothActivity";

    public TextView mBluetoothStatus;
    public TextView mReadBuffer;

    //Interaction with layout
    public Button mServiceON;
    public Button mServiceOFF;

    public ListView mDevicesListView;

    public Button mListPairedDevicesBtn;
    public Button mDiscoverBtn;

    public Button mWriteBtn;

    BluetoothService mBoundService;
    boolean mServiceBound = false;
    boolean mServiceStatus = false;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_virtualbluetooth);

        //Connection between layout element and layout page
        mServiceON = findViewById(R.id.start_service);
        mServiceOFF = findViewById(R.id.stop_service);

        mBluetoothStatus = findViewById(R.id.bluetoothStatus);
        mReadBuffer = findViewById(R.id.readBuffer);

        mDiscoverBtn = findViewById(R.id.discover);
        mListPairedDevicesBtn = findViewById(R.id.PairedBtn);

        mWriteBtn = findViewById(R.id.writeBtn);

        mDevicesListView = findViewById(R.id.devicesListView);


        // Ask for location permission if not already allowed
        if(ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED)
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_COARSE_LOCATION}, 1);

        //Click listeners
        mServiceOFF.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                killService();
            }
        });

        mServiceON.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                reviveService();
            }
        });

        mListPairedDevicesBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v){
                listDevices(v);
            }
        });

        mDiscoverBtn.setOnClickListener(new View.OnClickListener(){
            @Override
            public void onClick(View v){
                setDiscovery(v);
            }
        });

        mWriteBtn.setOnClickListener(new View.OnClickListener(){
            @Override
            public void onClick(View v){
                if(mServiceBound){
                    if(mBoundService.mConnectedThread != null){
                        mBoundService.mConnectedThread.write("Hello there!");
                    } else {
                        Toast.makeText(getApplicationContext(), "Not connected to a device", Toast.LENGTH_SHORT).show();
                    }
                } else {
                    Toast.makeText(getApplicationContext(), "Service not bound", Toast.LENGTH_SHORT).show();
                }
            }
        });

    }

    @Override
    protected void onStart() {
        super.onStart();
        reviveService();
        registerReceiver(mUIchanged, new IntentFilter(
                BluetoothService.UI_UPDATED));
    }

    @Override
    protected void onStop() {
        super.onStop();
        unbindService();
        unregisterReceiver(mUIchanged);
    }

    public ServiceConnection mServiceConnection = new ServiceConnection() {

        @Override
        public void onServiceDisconnected(ComponentName name) {
            mServiceBound = false;
        }

        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            MyBinder myBinder = (MyBinder) service;
            mBoundService = myBinder.getService();
            mServiceBound = true;
        }
    };

    //Starts the service and binds it
    private void reviveService() {
        if (!mServiceStatus) {
            Log.d(TAG, "Starting service");
            Intent intent = new Intent(this, BluetoothService.class);
            startService(intent);
            mServiceStatus = true;
        } else {
            Log.d(TAG, "Service already started");
        }
        bindSerice();
    }

    //Stops service
    private void killService() {
        if (mServiceStatus) {
            Log.d(TAG, "Stopping service");
            Intent intent = new Intent(this, BluetoothService.class);
            stopService(intent);
            unbindService();
            mServiceStatus = false;
            mBluetoothStatus.setText("Disconnected");
            mReadBuffer.setText(null);
        } else {
            Log.d(TAG, "Service not started");
        }
    }

    //Unbinds service from activity
    private void unbindService() {
        if (mServiceBound) {
            unbindService(mServiceConnection);
            mServiceBound = false;
        }
    }

    //Binds service
    private void bindSerice() {
        if (!mServiceBound) {
            Intent intent = new Intent(this, BluetoothService.class);
            bindService(intent, mServiceConnection, Context.BIND_AUTO_CREATE);
            mServiceBound = true;
        }
    }

    private void listDevices(View v) {
        if (mServiceStatus) {
            mBoundService.listPairedDevices(v);
        } else {
            Toast.makeText(this, "Service not started", Toast.LENGTH_SHORT).show();
        }
    }

    private void setDiscovery(View v) {
        if (mServiceStatus) {
            mBoundService.discover(v);
            mDevicesListView.setAdapter(mBoundService.mBTArrayAdapter); // assign model to view
            mDevicesListView.setOnItemClickListener(mBoundService.mDeviceClickListener);
        } else {
            Toast.makeText(this, "Service not started", Toast.LENGTH_SHORT).show();
        }
    }

    private final BroadcastReceiver mUIchanged = new BroadcastReceiver() {
        public void onReceive(Context context, Intent intent) {
            Log.d(TAG, "UI updating");
            if (intent.getAction().equals(BluetoothService.UI_UPDATED)) {
                updateUI();
                Log.d(TAG, "UI updated");
            }
        }
    };

    private void updateUI() {
        mBluetoothStatus.setText(mBoundService.mBluetoothStatus);
        mReadBuffer.setText(mBoundService.mReadBuffer);
    }
}