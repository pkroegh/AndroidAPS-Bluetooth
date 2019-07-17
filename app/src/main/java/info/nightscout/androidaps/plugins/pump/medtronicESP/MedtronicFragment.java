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
import info.nightscout.androidaps.events.EventPumpStatusChanged;
import info.nightscout.androidaps.events.EventTempBasalChange;
import info.nightscout.androidaps.plugins.common.SubscriberFragment;
import info.nightscout.androidaps.plugins.pump.medtronicESP.activities.MedtronicHistoryActivity;
import info.nightscout.androidaps.plugins.pump.medtronicESP.events.EventStatusChanged;
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
    @BindView(R.id.med_service_status) TextView vServiceStatus;
    @BindView(R.id.med_connection_status) TextView vConnStatus;
    @BindView(R.id.med_connection_ext_status_label) TextView vConnExtStatusLabel;
    @BindView(R.id.med_connection_ext_status) TextView vConnExtStatus;
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
    public void onStatusEvent(final EventStatusChanged c) {
        Activity activity = getActivity();
        final char action = c.action;
        if (activity != null) {
            activity.runOnUiThread(
                    () -> {
                        synchronized (MedtronicFragment.this) {
                            if (!isBound()) return;
                            switch (action) {
                                case MedtronicPump.EVENT_FAILED:
                                    vConnStatus.setText(MainApp.gs(R.string.med_connection_failed));
                                    break;
                                case MedtronicPump.EVENT_SLEEPING:
                                    deviceSleeping();
                                    break;
                                case MedtronicPump.EVENT_CONNECTING:
                                    deviceConnecting();
                                    break;
                                case MedtronicPump.EVENT_CONNECTED:
                                    deviceConnected();
                                    break;
                                case MedtronicPump.EVENT_SCAN:
                                    deviceScanning();
                                    break;
                                default:
                                    updatePumpStatus();
                            }
                        }
                    }
            );
        }
    }

    @Subscribe
    public void onStatusEvent(final EventTempBasalChange s) {
        Activity activity = getActivity();
        if (activity != null) {
            activity.runOnUiThread(
                    () -> {
                        synchronized (MedtronicFragment.this) {
                            if (!isBound()) return;
                            MedtronicPump pump = MedtronicPump.getInstance();
                            basaBasalRateView.setText(String.valueOf(pump.baseBasal));
                            tempBasalView.setText(String.valueOf(pump.tempBasal));
                        }
                    }
            );
        }
    }

    @Subscribe
    public void onStatusEvent(final EventPumpStatusChanged c) {
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
                                vServiceStatus.setText(MainApp.gs(R.string.med_service_null));
                                return;
                            }
                            if (pump.fatalError) {
                                vServiceStatus.setText(MainApp.gs(R.string.med_service_error));
                                bConnect.setText(MainApp.gs(R.string.med_button_label_reset));

                                return;
                            }
                            if (pump.isFakingConnection) {
                                vServiceStatus.setText(MainApp.gs(R.string.med_service_faking));
                                bConnect.setText(MainApp.gs(R.string.med_button_label_faking));
                            } else if (!medtronic.sMedtronicService.getRunThread()) {
                                vServiceStatus.setText(MainApp.gs(R.string.med_service_halted));
                                bConnect.setText(MainApp.gs(R.string.med_button_label_connect));
                            } else {
                                vServiceStatus.setText(MainApp.gs(R.string.med_service_running));
                                bConnect.setText(MainApp.gs(R.string.med_button_label_stop));
                                updatePumpStatus();
                                batteryView.setText(String.valueOf(pump.batteryRemaining));
                            }
                            basaBasalRateView.setText(String.valueOf(pump.baseBasal));
                            tempBasalView.setText(String.valueOf(pump.tempBasal));
                        }
                    }
            );
        }
    }

    private void resetGUI() {
        vServiceStatus.setText("");
        vConnStatus.setText("");
        vConnExtStatus.setText("");
        vConnExtStatusLabel.setText(MainApp.gs(R.string.med_connection_ext_label_sleeping));
        basaBasalRateView.setText("");
        tempBasalView.setText("");
        batteryView.setText("");
        bConnect.setText("");
    }

    private void updatePumpStatus() {
        MedtronicPump pump = MedtronicPump.getInstance();
        if (pump.sleepStartTime != 0) {
            // Device sleeping
            deviceSleeping();
        } else if (!pump.isScanning) {
            // Starting scan
        } else if (!pump.isDeviceFound) {
            // Scanning for device



        } else if (pump.isConnected) {
            deviceConnected();
        } else if (pump.isServiceAndCharacteristicFound) {


        } else if (pump.isConnecting) {
            deviceConnecting();
        } else if (pump.isConnected) {

        } else {
            deviceScanning();
        }
    }

    private double getTimeToNextWake() {
        MedtronicPump pump = MedtronicPump.getInstance();
        return (((pump.wakeInterval * minToMillisec) - (System.currentTimeMillis() - pump.sleepStartTime)) * 0.001);
    }

    private void deviceSleeping() {
        MedtronicPump pump = MedtronicPump.getInstance();
        if (pump.sleepStartTime != 0) {
            Long agoMsec = System.currentTimeMillis() - pump.sleepStartTime;
            int agoMin = (int) (agoMsec / 60d / 1000d);
            vConnStatus.setText(MainApp.gs(R.string.med_connection_sleeping_since) + "\n" +
                    DateUtil.timeString(pump.sleepStartTime) +
                    " (" + String.format(MainApp.gs(R.string.minago),
                    agoMin) + ")");
        } else {
            vConnStatus.setText(MainApp.gs(R.string.med_connection_sleeping));
        }
        vConnExtStatusLabel.setText(MainApp.gs(R.string.med_connection_ext_label_sleeping));
        vConnExtStatus.setText(String.valueOf(getTimeToNextWake() + " s"));
    }

    private void deviceConnecting() {
        vConnStatus.setText(MainApp.gs(R.string.med_connection_connecting));
        vConnExtStatusLabel.setText(MainApp.gs(R.string.med_connection_ext_label_connecting));
        vConnExtStatus.setText(MainApp.gs(R.string.med_connection_ext_connecting)
                + String.valueOf(MedtronicPump.getInstance().connectionAttempts));
    }

    private void deviceConnected() {
        vConnStatus.setText(MainApp.gs(R.string.med_connection_connected));
        vConnExtStatusLabel.setText(MainApp.gs(R.string.med_connection_ext_label_connected));
        updatePumpProgress(MedtronicPump.getInstance().actionState);
    }

    private void deviceScanning() {
        vConnStatus.setText(MainApp.gs(R.string.med_connection_scanning));
        vConnExtStatusLabel.setText(MainApp.gs(R.string.med_connection_ext_label_scanning));
        if (MedtronicPump.getInstance().isScanning) {
            vConnExtStatus.setText(MainApp.gs(R.string.med_connection_ext_scan_active));
        } else {
            vConnExtStatus.setText(MainApp.gs(R.string.med_connection_ext_scan_stopped));
        }
    }

    private void updatePumpProgress(int action) {
        switch (action) {
            case 0:
                vConnExtStatus.setText(MainApp.gs(R.string.med_connection_ext_ping));
                break;
            case 1:
                vConnExtStatus.setText(MainApp.gs(R.string.med_connection_ext_bolus));
                break;
            case 2:
                vConnExtStatus.setText(MainApp.gs(R.string.med_connection_ext_temp));
                break;
            case 3:
                vConnExtStatus.setText(MainApp.gs(R.string.med_connection_ext_sleep));
                break;
        }
    }

    private boolean isBound() {
        return vServiceStatus != null
                && vConnStatus != null
                && vConnExtStatus != null
                && vConnExtStatusLabel != null
                && basaBasalRateView != null
                && tempBasalView != null
                && batteryView != null
                && bConnect != null;
    }
}