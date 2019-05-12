package info.nightscout.androidaps.plugins.pump.medtronicESP;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
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
import info.nightscout.androidaps.events.EventTempBasalChange;
import info.nightscout.androidaps.logging.L;
import info.nightscout.androidaps.plugins.common.SubscriberFragment;
import info.nightscout.androidaps.plugins.pump.medtronicESP.events.EventESPStatusUpdate;
import info.nightscout.androidaps.utils.DateUtil;

/*
 *   Created by ldaug99 on 2019-02-17
 */

public class MedtronicFragment extends SubscriberFragment {
    private static Logger log = LoggerFactory.getLogger(L.PUMP);

    private static final long minToMillisec = 60000;

    TextView vServiceStatus;
    TextView vPumpName;
    TextView vESPStatus;
    TextView vWakeTime;
    TextView vLastConnect;

    TextView basaBasalRateView;
    TextView tempBasalView;
    TextView batteryView;

    Button bConnect;

    private Handler loopHandler = new Handler();
    private Runnable refreshLoop = new Runnable() {
        @Override
        public void run() {
            updateGUI();
            loopHandler.postDelayed(refreshLoop, 30 * 1000L);
        }
    };

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        loopHandler.postDelayed(refreshLoop, 30 * 1000L);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        loopHandler.removeCallbacks(refreshLoop);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        try {
            View view = inflater.inflate(R.layout.medtronic_fragment, container, false);

            vServiceStatus = view.findViewById(R.id.medtronicESP_service);
            vPumpName = view.findViewById(R.id.medtronicESP_client);
            vESPStatus = view.findViewById(R.id.medtronicESP_status);
            vWakeTime = view.findViewById(R.id.medtronicESP_wake);
            vLastConnect = view.findViewById(R.id.medtronicESP_lastconnect);

            basaBasalRateView = view.findViewById(R.id.medtronicESP_basabasalrate);
            tempBasalView = view.findViewById(R.id.medtronicESP_tempbasal);
            batteryView = view.findViewById(R.id.medtronicESP_battery);

            bConnect = view.findViewById(R.id.medtronicESP_button_connect);

            //Click listeners
            bConnect.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    MedtronicPlugin medtronic = MedtronicPlugin.getPlugin();
                    MedtronicPump pump = MedtronicPump.getInstance();
                    if (!medtronic.serviceNotNull()) {
                        log.error("Service not running on click");
                        return;
                    }
                    if (pump.mantainingConnection) { //Reset pump
                        medtronic.sMedtronicService.disconnectESP();
                        bConnect.setText(MainApp.gs(R.string.medtronicESP_button_label_connect));
                    } else if (!pump.mantainingConnection) { //Start connecting to pump
                        medtronic.sMedtronicService.connectESP();
                        bConnect.setText(MainApp.gs(R.string.medtronicESP_button_label_reset));
                    }
                    updateGUI();
                }
            });

            return view;
        } catch (Exception e) {
            Crashlytics.logException(e);
        }

        return null;
    }

    /*
    @Subscribe
    public void onStatusEvent(final EventBluetoothPumpUpdateGui ev) {
        updateGUI();
    }
    */

    @Subscribe
    public void onStatusEvent(final EventESPStatusUpdate s) {
        updateGUI();
    }

    @Subscribe
    public void onStatusEvent(final EventTempBasalChange s) {
        updateGUI();
    }

    @Override
    protected void updateGUI() {
        Activity activity = getActivity();
        if (activity != null) {
            activity.runOnUiThread(() -> {
                        synchronized (MedtronicFragment.this) {
                            //if (!isBound()) return;
                            MedtronicPlugin medtronic = MedtronicPlugin.getPlugin();
                            if (!medtronic.serviceNotNull()) {
                                vServiceStatus.setText(MainApp.gs(R.string.medtronicESP_service_null));
                                return;
                            } else {
                                vServiceStatus.setText(MainApp.gs(R.string.medtronicESP_service_running));
                            }
                            if (medtronic.fakeESPconnection) {
                                vPumpName.setText(MainApp.gs(R.string.medtronicESP_faking));
                                vESPStatus.setText(MainApp.gs(R.string.medtronicESP_faking));
                                vWakeTime.setText(MainApp.gs(R.string.medtronicESP_faking));
                                vLastConnect.setText(MainApp.gs(R.string.medtronicESP_faking));
                                basaBasalRateView.setText(MainApp.gs(R.string.medtronicESP_faking));
                                tempBasalView.setText(MainApp.gs(R.string.medtronicESP_faking));
                                batteryView.setText(MainApp.gs(R.string.medtronicESP_faking));
                                return;
                            }
                            MedtronicPump pump = MedtronicPump.getInstance();
                            vPumpName.setText(pump.mDevName);
                            if (!medtronic.sMedtronicService.isMaintainingConnection()) {
                                resetInterface();
                                return;
                            }
                            if (pump.isNewPump) {
                                vESPStatus.setText(MainApp.gs(R.string.medtronicESP_ESPfirstConnect));
                            } else if (pump.mDeviceSleeping) {
                                vESPStatus.setText(MainApp.gs(R.string.medtronicESP_ESPsleeping));
                            } else {
                                vESPStatus.setText(MainApp.gs(R.string.medtronicESP_ESPactive));
                            }
                            if (pump.wakeInterval != 0) {
                                vWakeTime.setText(String.valueOf(getTimeToNextWake() + " s"));
                            } else {
                                vWakeTime.setText(MainApp.gs(R.string.medtronicESP_ESPfirstConnect));
                            }
                            if (pump.lastConnection != 0) {
                                Long agoMsec = System.currentTimeMillis() - pump.lastConnection;
                                int agoMin = (int) (agoMsec / 60d / 1000d);
                                vLastConnect.setText(DateUtil.timeString(pump.lastConnection) + " (" + String.format(MainApp.gs(R.string.minago), agoMin) + ")");
                            }
                            basaBasalRateView.setText(String.valueOf(pump.baseBasal));
                            tempBasalView.setText(String.valueOf(pump.tempBasal));
                            batteryView.setText(String.valueOf(pump.batteryRemaining));
                        }
                    }
            );
        }
    }

    private double getTimeToNextWake() {
        MedtronicPump pump = MedtronicPump.getInstance();
        return (((pump.wakeInterval * minToMillisec) - (System.currentTimeMillis() - pump.lastConnection)) * 0.001);
    }

    private void resetInterface() {
        vPumpName.setText("");
        vESPStatus.setText("");
        vWakeTime.setText("");
        vLastConnect.setText("");
        basaBasalRateView.setText("");
        tempBasalView.setText("");
        batteryView.setText("");
    }

    private boolean isBound() {
        return vPumpName != null
                && vESPStatus != null
                && vWakeTime != null
                && basaBasalRateView != null
                && tempBasalView != null
                && batteryView != null;
    }
}