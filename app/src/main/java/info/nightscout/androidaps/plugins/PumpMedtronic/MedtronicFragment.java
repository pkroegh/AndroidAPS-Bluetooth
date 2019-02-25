package info.nightscout.androidaps.plugins.PumpMedtronic;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
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
import info.nightscout.androidaps.events.EventTempBasalChange;
import info.nightscout.androidaps.logging.L;
import info.nightscout.androidaps.plugins.Common.SubscriberFragment;
import info.nightscout.androidaps.plugins.PumpDanaR.DanaRFragment;
import info.nightscout.androidaps.plugins.PumpDanaR.DanaRPump;
import info.nightscout.androidaps.plugins.PumpDanaR.events.EventDanaRNewStatus;
import info.nightscout.androidaps.plugins.PumpMedtronic.events.EventESPStatusUpdate;
import info.nightscout.utils.DateUtil;
import info.nightscout.utils.SP;
import info.nightscout.utils.SetWarnColor;

/*
 *   Created by ldaug99 on 2019-02-17
 */

public class MedtronicFragment extends SubscriberFragment {
    private static Logger log = LoggerFactory.getLogger(L.PUMP);

    private static final long minToMillisec = 60000;

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
            loopHandler.postDelayed(refreshLoop, 60 * 1000L);
        }
    };

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        loopHandler.postDelayed(refreshLoop, 60 * 1000L);
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

            vPumpName = (TextView) view.findViewById(R.id.medtronicESP_client);
            vESPStatus = (TextView) view.findViewById(R.id.medtronicESP_status);
            vWakeTime = (TextView) view.findViewById(R.id.medtronicESP_wake);
            vLastConnect = (TextView) view.findViewById(R.id.medtronicESP_lastconnect);

            basaBasalRateView = (TextView) view.findViewById(R.id.medtronicESP_basabasalrate);
            tempBasalView = (TextView) view.findViewById(R.id.medtronicESP_tempbasal);
            batteryView = (TextView) view.findViewById(R.id.medtronicESP_battery);

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
                    } else if (!pump.mantainingConnection) { //Start connecting to pump
                        medtronic.sMedtronicService.connectESP();
                    }
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
                    if (!medtronic.serviceNotNull()) return;
                    MedtronicPump pump = MedtronicPump.getInstance();
                    vPumpName.setText(pump.mDevName);
                    if (pump.mFirstConnect) {
                        vESPStatus.setText(String.format(MainApp.gs(R.string.medtronicESP_ESPfirstConnect)));
                    } else if (pump.mDeviceSleeping) {
                        vESPStatus.setText(String.format(MainApp.gs(R.string.medtronicESP_ESPsleeping)));
                    } else {
                        vESPStatus.setText(String.format(MainApp.gs(R.string.medtronicESP_ESPactive)));
                    }
                    if (pump.wakeInterval != 0) {
                        vWakeTime.setText(String.valueOf(getTimeToNextWake() + " s"));
                    } else {
                        vWakeTime.setText(String.format(MainApp.gs(R.string.medtronicESP_ESPfirstConnect)));
                    }
                    if (pump.lastConnection != 0) {
                        Long agoMsec = System.currentTimeMillis() - pump.lastConnection;
                        int agoMin = (int) (agoMsec / 60d / 1000d);
                        vLastConnect.setText(DateUtil.timeString(pump.lastConnection) + " (" + String.format(MainApp.gs(R.string.minago), agoMin) + ")");
                    }
                    basaBasalRateView.setText(String.valueOf(pump.baseBasal));
                    tempBasalView.setText(String.valueOf(pump.tempBasal));
                    batteryView.setText(String.valueOf(pump.tempBasalDuration));
                    if (!medtronic.serviceNotNull()) {
                        bConnect.setText(String.format(MainApp.gs(R.string.medtronicESP_button_label_serviceNull)));

                    } else if (pump.mFirstConnect && pump.mantainingConnection) {
                        bConnect.setText(String.format(MainApp.gs(R.string.medtronicESP_ESPfirstConnect)));

                    } else if (!pump.mFirstConnect && pump.mantainingConnection) {
                        bConnect.setText(String.format(MainApp.gs(R.string.medtronicESP_button_label_reset)));

                    } else {
                        bConnect.setText(String.format(MainApp.gs(R.string.medtronicESP_button_label_connect)));

                    }
                   }
               }
            );
        }
    }

    private double getTimeToNextWake() {
        MedtronicPump pump = MedtronicPump.getInstance();
        return (((pump.wakeInterval * minToMillisec) - (System.currentTimeMillis() - pump.lastConnection)) * 0.001);
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