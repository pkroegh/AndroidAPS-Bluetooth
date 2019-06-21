package info.nightscout.androidaps.plugins.pump.medtronicESP;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import com.squareup.otto.Subscribe;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.OnClick;
import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.R;
import info.nightscout.androidaps.events.EventTempBasalChange;
import info.nightscout.androidaps.logging.L;
import info.nightscout.androidaps.plugins.common.SubscriberFragment;
import info.nightscout.androidaps.plugins.pump.danaR.activities.DanaRHistoryActivity;
import info.nightscout.androidaps.plugins.pump.medtronicESP.activities.MedtronicHistoryActivity;
import info.nightscout.androidaps.plugins.pump.medtronicESP.events.EventESPStatusUpdate;
import info.nightscout.androidaps.utils.DateUtil;

/*
 *   Created by ldaug99 on 2019-02-17
 */

public class MedtronicFragment extends SubscriberFragment {
    //private static Logger log = LoggerFactory.getLogger(L.PUMP);
    private Logger log = LoggerFactory.getLogger("Medtronic");
    private static final long minToMillisec = 60000; // Conversion from min to milliseconds

    public MedtronicFragment() {}

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
        View view = inflater.inflate(R.layout.medtronic_fragment, container, false);
        unbinder = ButterKnife.bind(this, view);

        return view;
    }

    // Interface variable declaration
    @BindView(R.id.medtronicESP_service) TextView vServiceStatus;
    @BindView(R.id.medtronicESP_status) TextView vESPStatus;
    @BindView(R.id.medtronicESP_wake) TextView vWakeTime;
    @BindView(R.id.medtronicESP_lastconnect) TextView vLastConnect;
    @BindView(R.id.medtronicESP_basabasalrate) TextView basaBasalRateView;
    @BindView(R.id.medtronicESP_tempbasal) TextView tempBasalView;
    @BindView(R.id.medtronicESP_battery) TextView batteryView;

    @BindView(R.id.medtronicESP_button_connect) Button bConnect;

    @OnClick(R.id.medtronicESP_button_connect)
    void onBtConnectionClick() {
        MedtronicPlugin medtronic = MedtronicPlugin.getPlugin();
        if (medtronic.sMedtronicService == null) {
            log.error("Service not running on click");
            return;
        }
        if (medtronic.sMedtronicService.getRunThread()) { // Stop connecting to pump
            medtronic.sMedtronicService.disconnectESP();
        } else if (!medtronic.sMedtronicService.getRunThread()) { //Start connecting to pump
            medtronic.sMedtronicService.connectESP();
        }
        updateGUI();
    }

    @BindView(R.id.medtronicESP_button_history) Button bHistory;

    @OnClick(R.id.medtronicESP_button_history)
    void onHistoryConnectionClick() {
        startActivity(new Intent(getContext(), MedtronicHistoryActivity.class));
    }

    /* Event subscription, updates GUI */
    @Subscribe
    public void onStatusEvent(final EventESPStatusUpdate s) {
        updateGUI();
    }

    @Subscribe
    public void onStatusEvent(final EventTempBasalChange s) {
        updateGUI();
    }

    /* GUI update function */
    @Override
    protected void updateGUI() {
        Activity activity = getActivity();
        if (activity != null) {
            activity.runOnUiThread(() -> {
                        synchronized (MedtronicFragment.this) {
                            if (!isBound()) return;
                            MedtronicPlugin medtronic = MedtronicPlugin.getPlugin();
                            if (medtronic.sMedtronicService == null) {
                                vServiceStatus.setText(MainApp.gs(R.string.medtronicESP_service_null));
                                return;
                            }
                            if (!medtronic.sMedtronicService.getRunThread()) {
                                bConnect.setText(MainApp.gs(R.string.medtronicESP_button_label_connect));
                            } else {
                                bConnect.setText(MainApp.gs(R.string.medtronicESP_button_label_stop));
                            }
                            MedtronicPump pump = MedtronicPump.getInstance();
                            vServiceStatus.setText(MainApp.gs(R.string.medtronicESP_service_running));
                            if (pump.isFakingConnection) {
                                vESPStatus.setText(MainApp.gs(R.string.medtronicESP_faking));
                            } else {
                                if (pump.isSleeping) {
                                    vESPStatus.setText(MainApp.gs(R.string.medtronicESP_ESPsleeping));
                                    if (pump.wakeInterval != 0) {
                                        vWakeTime.setText(String.valueOf(getTimeToNextWake() + " s"));
                                    }
                                    if (pump.lastMessageTime != 0) {
                                        Long agoMsec = System.currentTimeMillis() - pump.lastMessageTime;
                                        int agoMin = (int) (agoMsec / 60d / 1000d);
                                        vLastConnect.setText(DateUtil.timeString(pump.lastMessageTime) +
                                                " (" + String.format(MainApp.gs(R.string.minago),
                                                agoMin) + ")");
                                    }
                                } else if (pump.isConnected) {
                                    vESPStatus.setText(MainApp.gs(R.string.medtronicESP_ESPactive));
                                } else if (medtronic.sMedtronicService.getRunThread()) {
                                    vESPStatus.setText(MainApp.gs(R.string.medtronicESP_ESPfirstConnect));
                                } else if (!medtronic.sMedtronicService.getRunThread()) {
                                    vESPStatus.setText(MainApp.gs(R.string.medtronicESP_ESPhalted));
                                } else {
                                    vESPStatus.setText(" ");
                                }
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
        return (((pump.wakeInterval * minToMillisec) - (System.currentTimeMillis() - pump.lastMessageTime)) * 0.001);
    }

    private boolean isBound() {
        return vServiceStatus != null
                && vESPStatus != null
                && vWakeTime != null
                && vLastConnect != null
                && basaBasalRateView != null
                && tempBasalView != null
                && batteryView != null
                && bConnect != null;
    }
}