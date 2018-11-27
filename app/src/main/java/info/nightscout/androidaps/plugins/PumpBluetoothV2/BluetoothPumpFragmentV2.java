package info.nightscout.androidaps.plugins.PumpBluetoothV2;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.os.Bundle;
import android.os.Handler;
import android.support.annotation.Nullable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import com.crashlytics.android.Crashlytics;
import com.squareup.otto.Subscribe;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.R;
import info.nightscout.androidaps.plugins.Common.SubscriberFragment;
import info.nightscout.androidaps.plugins.PumpBluetoothV2.events.EventBluetoothPumpV2UpdateGui;

public class BluetoothPumpFragmentV2 extends SubscriberFragment {
    private static Logger log = LoggerFactory.getLogger(BluetoothPumpFragmentV2.class);

    TextView basaBasalRateView;
    TextView tempBasalView;
    TextView extendedBolusView;
    TextView batteryView;
    TextView reservoirView;

    private Button bBluetoothConnect;

    private TextView vPumpName;
    private TextView vBluetoothStatus;
    private TextView vThreadStatus;

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
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        try {
            View view = inflater.inflate(R.layout.bluetoothpumpv2_fragment, container, false);

            vPumpName = view.findViewById(R.id.bluetoothpump_client);
            vBluetoothStatus = view.findViewById(R.id.bluetoothpump_bluetoothstatus);
            vThreadStatus = view.findViewById(R.id.bluetoothpump_threadstatus);

            basaBasalRateView = view.findViewById(R.id.virtualpump_basabasalrate);
            tempBasalView = view.findViewById(R.id.virtualpump_tempbasal);
            extendedBolusView = view.findViewById(R.id.virtualpump_extendedbolus);
            batteryView = view.findViewById(R.id.virtualpump_battery);
            reservoirView = view.findViewById(R.id.virtualpump_reservoir);

            //Connection between layout element and layout page (Button elements)
            bBluetoothConnect = view.findViewById(R.id.service_connect);

            //Click listeners
            bBluetoothConnect.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    BluetoothPumpPluginV2 bluetoothPump = BluetoothPumpPluginV2.getPlugin();
                    if(bluetoothPump.isConnected()){
                        Toast.makeText(MainApp.instance().getApplicationContext(),"Already connected",Toast.LENGTH_SHORT).show();
                    } else if (bluetoothPump.isConnecting()) {
                        Toast.makeText(MainApp.instance().getApplicationContext(),"Connecting...",Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(MainApp.instance().getApplicationContext(),"Starting...",Toast.LENGTH_SHORT).show();
                        bluetoothPump.createService();
                        bluetoothPump.connect("default");
                    }
                }
            });

            return view;
        } catch (Exception e) {
            Crashlytics.logException(e);
        }

        return null;
    }

    @Subscribe
    public void onStatusEvent(final EventBluetoothPumpV2UpdateGui ev) {
        updateGUI();
    }

    @Override
    protected void updateGUI() {
        Activity activity = getActivity();
        if (activity != null && basaBasalRateView != null)
            activity.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    BluetoothPumpPluginV2 bluetoothPump = BluetoothPumpPluginV2.getPlugin();
                    bluetoothPump.loadIgnoreStatus();
                    //Bluetooth service and bluetooth related GUI
                    if (bluetoothPump.ignorePump){
                        vPumpName.setText(R.string.ignorepump_faking);
                        vBluetoothStatus.setText(R.string.ignorepump_faking);
                        vThreadStatus.setText(R.string.ignorepump_faking);
                    } else if (BluetoothAdapter.getDefaultAdapter() != null){
                        if (bluetoothPump.sExecutionService != null) {
                            if (bluetoothPump.sExecutionService.mBluetoothAdapter != null) {
                                vPumpName.setText(bluetoothPump.sExecutionService.mDevName);
                                if (bluetoothPump.sExecutionService != null && bluetoothPump.sExecutionService.isConnecting()) {
                                    vBluetoothStatus.setText(R.string.bluetoothstatus_connecting);
                                } else if (bluetoothPump.sExecutionService.isConnected()) {
                                    vBluetoothStatus.setText(R.string.bluetoothstatus_connected);
                                } else if (bluetoothPump.sExecutionService.mBTSocket != null && bluetoothPump.sExecutionService.mBTSocket.isConnected()) {
                                    vBluetoothStatus.setText(R.string.bluetoothstatus_connected);
                                } else {
                                    vBluetoothStatus.setText(R.string.bluetoothstatus_disconnected);
                                }
                            }
                            if (bluetoothPump.sExecutionService.mConnectedThread != null) {
                                vThreadStatus.setText(R.string.bluetooththread_running);
                            } else {
                                vThreadStatus.setText(R.string.bluetooththread_stopped);
                            }
                        }
                    } else {
                        vBluetoothStatus.setText(R.string.bluetoothstatus_invalid);
                    }

                    basaBasalRateView.setText(bluetoothPump.getBaseBasalRate() + "U");
                    if (MainApp.getConfigBuilder().isTempBasalInProgress()) {
                        tempBasalView.setText(MainApp.getConfigBuilder().getTempBasalFromHistory(System.currentTimeMillis()).toStringFull());
                    } else {
                        tempBasalView.setText("");
                    }
                    if (MainApp.getConfigBuilder().isInHistoryExtendedBoluslInProgress()) {
                        extendedBolusView.setText(MainApp.getConfigBuilder().getExtendedBolusFromHistory(System.currentTimeMillis()).toString());
                    } else {
                        extendedBolusView.setText("");
                    }
                    batteryView.setText(BluetoothPumpPluginV2.batteryPercent + "%");
                    reservoirView.setText(BluetoothPumpPluginV2.reservoirInUnits + "U");
                }
            });
    }
}

