package info.nightscout.androidaps.plugins.PumpBluetooth;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.os.Bundle;
import android.os.Handler;
import android.support.annotation.Nullable;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import com.crashlytics.android.Crashlytics;
import com.squareup.otto.Subscribe;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.R;
import info.nightscout.androidaps.events.EventPumpStatusChanged;
import info.nightscout.androidaps.plugins.Common.SubscriberFragment;
import info.nightscout.androidaps.plugins.ConfigBuilder.ConfigBuilderPlugin;
import info.nightscout.androidaps.plugins.PumpBluetooth.events.EventBluetoothPumpUpdateGui;
import info.nightscout.androidaps.plugins.PumpBluetooth.services.BluetoothService;
import info.nightscout.utils.DecimalFormatter;
import info.nightscout.utils.SetWarnColor;

public class BluetoothPumpFragment extends SubscriberFragment {
    protected Logger log = LoggerFactory.getLogger(BluetoothPumpFragment.class);

    private TextView vPumpName;
    private TextView vBluetoothStatus;
    private TextView vServiceStatus;
    private TextView vCarbs;
    private TextView tBaseBasalRate;
    private TextView tTempBasal;
    private TextView tPumpBattery;
    private TextView tPumpReservoir;

    private Button bReviveService;

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
        try{
            //Connection between layout element and layout page (TextView elements)
            vPumpName = view.findViewById(R.id.bluetoothpump_client);
            vBluetoothStatus = view.findViewById(R.id.bluetoothpump_bluetoothstatus);
            vServiceStatus = view.findViewById(R.id.bluetoothpump_servicestatus);
            vCarbs = view.findViewById(R.id.bluetoothpump_carbs);
            tBaseBasalRate = view.findViewById(R.id.bluetoothpump_basabasalrate);
            tTempBasal = view.findViewById(R.id.bluetoothpump_tempbasal);
            tPumpBattery = view.findViewById(R.id.bluetoothpump_battery);
            tPumpReservoir = view.findViewById(R.id.bluetoothpump_reservoir);

            //Connection between layout element and layout page (Button elements)
            bReviveService = view.findViewById(R.id.service_revive);

            //Click listeners
            bReviveService.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    BluetoothPumpPlugin bluetoothPump = BluetoothPumpPlugin.getPlugin();
                    bluetoothPump.reviveService();
                }
            });

            return view;
        } catch (Exception e) {
            Crashlytics.logException(e);
        }
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
                    BluetoothPump pump = BluetoothPump.getInstance();
                    Log.d("BluetoothPumpFragment", "Updating Bluetooth GUI");
                    BluetoothPumpPlugin bluetoothPump = BluetoothPumpPlugin.getPlugin();
                    //Bluetooth service and bluetooth related GUI

                    if (BluetoothAdapter.getDefaultAdapter() != null){
                        if (bluetoothPump.sExecutionService != null) {
                            if(bluetoothPump.sExecutionService.mBluetoothAdapter != null) {
                                vServiceStatus.setText(MainApp.sResources.getString(R.string.bluetoothservice_bound));
                                vPumpName.setText(bluetoothPump.sExecutionService.mDevName);
                                if (bluetoothPump.sExecutionService.isConnecting()) {
                                    vBluetoothStatus.setText(MainApp.sResources.getString(R.string.bluetoothstatus_connecting));
                                } else if (bluetoothPump.sExecutionService.isConnected()) {
                                    vBluetoothStatus.setText(MainApp.sResources.getString(R.string.bluetoothstatus_connected));
                                } else if (bluetoothPump.sExecutionService.mBTSocket.isConnected()) {
                                    vBluetoothStatus.setText(MainApp.sResources.getString(R.string.bluetoothstatus_connected));
                                } else {
                                    vBluetoothStatus.setText(MainApp.sResources.getString(R.string.bluetoothstatus_disconnected));
                                }
                            }
                        } else {
                            if (bluetoothPump.isServiceRunning(BluetoothService.class)) {
                                vServiceStatus.setText(MainApp.sResources.getString(R.string.bluetoothservice_running));
                            } else {
                                vServiceStatus.setText(MainApp.sResources.getString(R.string.bluetoothservice_stopped));
                            }
                        }
                    } else {
                        vBluetoothStatus.setText(MainApp.sResources.getString(R.string.bluetoothstatus_invalid));
                        vServiceStatus.setText(MainApp.sResources.getString(R.string.bluetoothservice_invalid));
                    }
                    //Treatment related

                    tBaseBasalRate.setText("( " + (pump.activeProfile + 1) + " )  " + DecimalFormatter.to2Decimal(ConfigBuilderPlugin.getActivePump().getBaseBasalRate()) + " U/h");

                    if (ConfigBuilderPlugin.getActivePump().isFakingTempsByExtendedBoluses()) {
                        if (MainApp.getConfigBuilder().isInHistoryRealTempBasalInProgress()) {
                            tTempBasal.setText(MainApp.getConfigBuilder().getRealTempBasalFromHistory(System.currentTimeMillis()).toStringFull());
                        } else {
                            tTempBasal.setText("");
                        }
                    } else {
                        // v2 plugin
                        if (MainApp.getConfigBuilder().isTempBasalInProgress()) {
                            tTempBasal.setText(MainApp.getConfigBuilder().getTempBasalFromHistory(System.currentTimeMillis()).toStringFull());
                        } else {
                            tTempBasal.setText("");
                        }
                    }

                    tPumpReservoir.setText(DecimalFormatter.to0Decimal(pump.reservoirRemainingUnits) + " / 300 U");
                    SetWarnColor.setColorInverse(tPumpReservoir, pump.reservoirRemainingUnits, 50d, 20d);
                    tPumpBattery.setText("{fa-battery-" + (pump.batteryRemaining / 25) + "}");
                    SetWarnColor.setColorInverse(tPumpBattery, pump.batteryRemaining, 51d, 26d);


                    /*
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
                    */
                 }
            });
    }
}
