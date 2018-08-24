package info.nightscout.androidaps.plugins.PumpBluetooth;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.support.annotation.Nullable;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.crashlytics.android.Crashlytics;
import com.squareup.otto.Subscribe;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.R;
import info.nightscout.androidaps.events.EventPumpStatusChanged;
import info.nightscout.androidaps.plugins.ConfigBuilder.ConfigBuilderPlugin;
import info.nightscout.androidaps.plugins.PumpBluetooth.services.BluetoothService;
import info.nightscout.androidaps.plugins.Common.SubscriberFragment;
import info.nightscout.androidaps.plugins.PumpBluetooth.events.EventBluetoothPumpUpdateGui;
import info.nightscout.androidaps.plugins.PumpVirtual.VirtualPumpFragment;
import info.nightscout.androidaps.plugins.PumpVirtual.VirtualPumpPlugin;
import info.nightscout.androidaps.plugins.PumpVirtual.events.EventVirtualPumpUpdateGui;

public class BluetoothPumpFragment extends SubscriberFragment {
    protected Logger log = LoggerFactory.getLogger(BluetoothPumpFragment.class);







    private TextView basaBasalRateView;

    private TextView mBluetoothStatus;
    private TextView mServiceStatus;
    private TextView mReadBuffer;
    //private TextView mSetInsulin;
    //private TextView mSetCarbs;

    private TextView tempBasalView;

    //Interaction with layout
    public Button mServiceON;
    public Button mServiceOFF;

    private ListView mDevicesListView;

    public Button mListPairedDevicesBtn;
    public Button mDiscoverBtn;

    public Button mWriteBtn;

    //Update GUI handler
    private static Handler sLoopHandler = new Handler();
    private static Runnable sRefreshLoop = null;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (sRefreshLoop == null) {
            sRefreshLoop = new Runnable() {
                @Override
                public void run() {
                    updateGUI();
                    sLoopHandler.postDelayed(sRefreshLoop, 60 * 1000L);
                }
            };
            sLoopHandler.postDelayed(sRefreshLoop, 60 * 1000L);
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.bluetoothpump_fragment, container, false);

        /*

        try{
            //Connection between layout element and layout page
            mServiceON = view.findViewById(R.id.start_service);
            mServiceOFF = view.findViewById(R.id.stop_service);

            mBluetoothStatus = view.findViewById(R.id.bluetoothStatus);
            mServiceStatus = view.findViewById(R.id.serviceStatus);
            mReadBuffer = view.findViewById(R.id.readBuffer);
            //basaBasalRateView = view.findViewById(R.id.setInsulin);
            //mSetCarbs = view.findViewById(R.id.setCarbs);

            mDiscoverBtn = view.findViewById(R.id.discover);
            mListPairedDevicesBtn = view.findViewById(R.id.PairedBtn);

            mWriteBtn = view.findViewById(R.id.writeBtn);

            mDevicesListView = view.findViewById(R.id.devicesListView);


            tempBasalView = view.findViewById(R.id.bluetooth_tempbasal);

            //Click listeners
            mServiceON.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    BluetoothPumpPlugin bluetoothPump = BluetoothPumpPlugin.getPlugin();
                    bluetoothPump.reviveService();
                }
            });

            mDiscoverBtn.setOnClickListener(new View.OnClickListener(){
                @Override
                public void onClick(View v){
                    BluetoothPumpPlugin bluetoothPump = BluetoothPumpPlugin.getPlugin();
                    bluetoothPump.setDiscovery();
                }
            });

            mWriteBtn.setOnClickListener(new View.OnClickListener(){
                @Override
                public void onClick(View v){
                    Context context = MainApp.instance().getApplicationContext();
                    BluetoothPumpPlugin bluetoothPump = BluetoothPumpPlugin.getPlugin();
                    if(bluetoothPump.mServiceBound){
                        if(bluetoothPump.sExecutionService.mConnectedThread != null){
                            bluetoothPump.sExecutionService.mConnectedThread.write("Hello there!");
                        } else {
                            Toast.makeText(context, "Not connected to a device", Toast.LENGTH_SHORT).show();
                        }
                    } else {
                        Toast.makeText(context, "Service not bound", Toast.LENGTH_SHORT).show();
                    }
                }
            });

            return view;
        } catch (Exception e) {
            Crashlytics.logException(e);
        }

        */

        return null;
    }



    @Subscribe
    public void onStatusEvent(final EventPumpStatusChanged ev) {
        updateGUI();
    }

    @Subscribe
    public void onStatusEvent(final EventBluetoothPumpUpdateGui ev) {
        updateGUI();
    }

    @Override
    protected void updateGUI() {
        Activity activity = getActivity();
        if (activity != null)
            activity.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    Log.d("BluetoothPumpFragment", "Updating Bluetooth GUI");
                    BluetoothPumpPlugin bluetoothPump = BluetoothPumpPlugin.getPlugin();
                    if (bluetoothPump.mServiceStarted) {
                        if (bluetoothPump.sExecutionService.mBluetoothAdapter != null) {
                            mDevicesListView.setAdapter(bluetoothPump.sExecutionService.mBTArrayAdapter); // assign model to view
                            mDevicesListView.setOnItemClickListener(bluetoothPump.sExecutionService.mDeviceClickListener);
                        }
                    } else if (!bluetoothPump.mServiceStarted){
                        mDevicesListView.setAdapter(null);
                    }
                    if (bluetoothPump.mServiceBound){
                        //mBluetoothStatus.setText(bluetoothPump.sExecutionService.mBluetoothStatus);
                        //ReadBuffer.setText(bluetoothPump.sExecutionService.mReadBuffer);
                        //mSetInsulin.setText();
                        //mSetCarbs.setText();
                    } else {
                        mBluetoothStatus.setText(null);
                        mReadBuffer.setText(null);
                        //mSetInsulin.setText(null);
                        //mSetCarbs.setText(null);
                    }
                    mServiceStatus.setText(bluetoothPump.mServiceStatus);

                    if (ConfigBuilderPlugin.getActivePump().isFakingTempsByExtendedBoluses()) {
                        if (MainApp.getConfigBuilder().isInHistoryRealTempBasalInProgress()) {
                            tempBasalView.setText(MainApp.getConfigBuilder().getRealTempBasalFromHistory(System.currentTimeMillis()).toStringFull());
                        } else {
                            tempBasalView.setText("");
                        }
                    } else {
                        // v2 plugin
                        if (MainApp.getConfigBuilder().isTempBasalInProgress()) {
                            tempBasalView.setText(MainApp.getConfigBuilder().getTempBasalFromHistory(System.currentTimeMillis()).toStringFull());
                        } else {
                            tempBasalView.setText("");
                        }
                    }
                 }
            });
    }
}
