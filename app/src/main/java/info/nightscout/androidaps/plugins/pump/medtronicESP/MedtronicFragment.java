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
import info.nightscout.androidaps.plugins.common.SubscriberFragment;
import info.nightscout.androidaps.plugins.pump.medtronicESP.activities.MedtronicHistoryActivity;
import info.nightscout.androidaps.plugins.pump.medtronicESP.events.EventUpdateGUI;
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
    @BindView(R.id.medtronicESP_wakeStatus) TextView vWakeStatus;
    @BindView(R.id.medtronicESP_wake) TextView vWakeTime;
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
    public void onStatusEvent(final EventUpdateGUI s) {
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
                            resetGUI();
                            MedtronicPlugin medtronic = MedtronicPlugin.getPlugin();
                            MedtronicPump pump = MedtronicPump.getInstance();
                            if (medtronic.sMedtronicService == null) {
                                vServiceStatus.setText(MainApp.gs(R.string.medtronicESP_service_null));
                                return;
                            } else if (pump.isFakingConnection) {
                                vServiceStatus.setText(MainApp.gs(R.string.medtronicESP_faking));
                                bConnect.setText(MainApp.gs(R.string.medtronicESP_faking));
                            } else if (!medtronic.sMedtronicService.getRunThread()) {
                                vServiceStatus.setText(MainApp.gs(R.string.medtronicESP_ESPhalted));
                                bConnect.setText(MainApp.gs(R.string.medtronicESP_button_label_connect));
                            } else {
                                vServiceStatus.setText(MainApp.gs(R.string.medtronicESP_service_running));
                                bConnect.setText(MainApp.gs(R.string.medtronicESP_button_label_stop));
                                if (pump.connectionAttempts <= MainApp.gi(R.integer.medtronic_connection_attempts_alarm_threshold)) {
                                    if (pump.isSleeping) {
                                        if (pump.lastMessageTime != 0) {
                                            Long agoMsec = System.currentTimeMillis() - pump.lastMessageTime;
                                            int agoMin = (int) (agoMsec / 60d / 1000d);
                                            vESPStatus.setText(MainApp.gs(R.string.medtronicESP_sleeping_since) +
                                                    DateUtil.timeString(pump.lastMessageTime) +
                                                    " (" + String.format(MainApp.gs(R.string.minago),
                                                    agoMin) + ")");
                                        } else {
                                            vESPStatus.setText(MainApp.gs(R.string.medtronicESP_ESPsleeping));
                                        }
                                        vWakeStatus.setText(MainApp.gs(R.string.medtronicESP_wake_label));
                                        vWakeTime.setText(String.valueOf(getTimeToNextWake() + " s"));
                                    } else if (pump.isConnecting) {
                                        vESPStatus.setText(MainApp.gs(R.string.medtronicESP_ESPfirstConnect));
                                        vWakeStatus.setText(MainApp.gs(R.string.medtronicESP_connectionStatus));
                                        vWakeTime.setText(MainApp.gs(R.string.medtronicESP_connectionAttempts)
                                                + String.valueOf(pump.connectionAttempts));
                                    } else if (pump.isConnected) {
                                        vESPStatus.setText(MainApp.gs(R.string.medtronicESP_ESPactive));
                                        vWakeStatus.setText(MainApp.gs(R.string.medtronicESP_connected_label));
                                        switch (pump.mActionState) {
                                            case 0:
                                                vWakeTime.setText(MainApp.gs(R.string.medtronicESP_connected_send_ping));
                                                break;
                                            case 1:
                                                vWakeTime.setText(MainApp.gs(R.string.medtronicESP_connected_send_bolus));
                                                break;
                                            case 2:
                                                vWakeTime.setText(MainApp.gs(R.string.medtronicESP_connected_send_temp));
                                                break;
                                            case 3:
                                                vWakeTime.setText(MainApp.gs(R.string.medtronicESP_connected_send_sleep));
                                                break;
                                        }
                                    } else {
                                        vESPStatus.setText(MainApp.gs(R.string.medtornicESP_scanning));
                                    }
                                    batteryView.setText(String.valueOf(pump.batteryRemaining));
                                } else {
                                    vESPStatus.setText(MainApp.gs(R.string.medtronicESP_status_failed));
                                }
                            }
                            /*
                            if (!medtronic.sMedtronicService.getRunThread()) {
                                bConnect.setText(MainApp.gs(R.string.medtronicESP_button_label_connect));
                            } else {
                                bConnect.setText(MainApp.gs(R.string.medtronicESP_button_label_stop));
                            }
                            MedtronicPump pump = MedtronicPump.getInstance();
                            if (pump.isFakingConnection) {
                                vServiceStatus.setText(MainApp.gs(R.string.medtronicESP_faking));
                            } else if (!medtronic.sMedtronicService.getRunThread() {
                                vServiceStatus.setText(MainApp.gs(R.string.medtronicESP_ESPhalted));
                            } else {
                                vServiceStatus.setText(MainApp.gs(R.string.medtronicESP_service_running));
                                if (pump.isConnected) {
                                    vESPStatus.setText(MainApp.gs(R.string.medtronicESP_ESPactive));
                                } else if (pump.isConnecting) {
                                    vESPStatus.setText(MainApp.gs(R.string.medtronicESP_ESPfirstConnect));
                                    vWakeTime.setText(String.valueOf(getTimeToNextWake() + " s"));
                                }
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
                            */
                            basaBasalRateView.setText(String.valueOf(pump.baseBasal));
                            tempBasalView.setText(String.valueOf(pump.tempBasal));
                        }
                    }
            );
        }
    }

    private void resetGUI() {
        vServiceStatus.setText("");
        vESPStatus.setText("");
        vWakeTime.setText("");
        vWakeStatus.setText(MainApp.gs(R.string.medtronicESP_wake_label));
        basaBasalRateView.setText("");
        tempBasalView.setText("");
        batteryView.setText("");
        bConnect.setText("");
    }

    private double getTimeToNextWake() {
        MedtronicPump pump = MedtronicPump.getInstance();
        return (((pump.wakeInterval * minToMillisec) - (System.currentTimeMillis() - pump.lastMessageTime)) * 0.001);
    }

    private boolean isBound() {
        return vServiceStatus != null
                && vESPStatus != null
                && vWakeTime != null
                && vWakeStatus != null
                && basaBasalRateView != null
                && tempBasalView != null
                && batteryView != null
                && bConnect != null;
    }
}