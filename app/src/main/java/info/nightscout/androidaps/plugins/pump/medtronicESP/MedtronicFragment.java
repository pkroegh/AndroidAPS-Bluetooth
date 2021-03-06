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

import java.sql.Time;
import java.text.DecimalFormat;

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
import info.nightscout.androidaps.plugins.pump.medtronicESP.utils.TimeUtil;
import info.nightscout.androidaps.utils.DateUtil;

/*
 *   Created by ldaug99 on 2019-02-17
 */

public class MedtronicFragment extends SubscriberFragment {
    //private static Logger log = LoggerFactory.getLogger(L.PUMP);
    private Logger log = LoggerFactory.getLogger("Medtronic");

    DecimalFormat precision = new DecimalFormat("0.00");

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
        if (MedtronicPump.getInstance().fatalError) {
            medtronic.sMedtronicService.disconnectESP();
        } else if (!medtronic.sMedtronicService.getRunThread()) { //Start connecting to pump
            medtronic.sMedtronicService.connectESP();
        } else if (medtronic.sMedtronicService.getRunThread()) { // Stop connecting to pump
            medtronic.sMedtronicService.disconnectESP();
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
        if (activity != null) {
            activity.runOnUiThread(
                    () -> {
                        synchronized (MedtronicFragment.this) {
                            if (!isBound()) return;
                            updatePumpStatus();
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
                            if (medtronic.sMedtronicService == null) {
                                vServiceStatus.setText(MainApp.gs(R.string.med_service_null));
                                return;
                            }
                            MedtronicPump pump = MedtronicPump.getInstance();
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
                            basaBasalRateView.setText(String.valueOf(precision.format(pump.baseBasal)));
                            tempBasalView.setText(String.valueOf(precision.format(pump.tempBasal)));
                        }
                    }
            );
        }
    }

    private void resetGUI() {
        vServiceStatus.setText("");
        vConnStatus.setText("");
        vConnExtStatus.setText("");
        vConnExtStatusLabel.setText(MainApp.gs(R.string.med_progress_label_sleeping));
        basaBasalRateView.setText("");
        tempBasalView.setText("");
        batteryView.setText("");
        bConnect.setText("");
    }

    private void updatePumpStatus() {
        MedtronicPump pump = MedtronicPump.getInstance();
        if (pump.sleepStartTime != 0) { // Device sleeping.
            deviceSleeping();
            return;
        }
        switch (pump.connectPhase) {
            case 0: // Starting scan.
                deviceInitializing();
                break;
            case 1: // Scanning for device.
                deviceScanning();
                break;
            case 2: // Device found, connecting.
                deviceConnecting();
                break;
            case 3: // Connected, handshaking.
                deviceConnected();
                break;
            case 4: // Handshaking done, setting commands.
                deviceCommunicating();
                break;
        }
    }

    private void deviceSleeping() {
        //Long agoMsec = System.currentTimeMillis() - pump.sleepStartTime;
        //int agoMin = (int) (agoMsec / 60d / 1000d);
        MedtronicPump pump = MedtronicPump.getInstance();
        String text = MainApp.gs(R.string.med_state_sleeping) +
                DateUtil.timeString(pump.sleepStartTime);
        vConnStatus.setText(text);
        //vConnStatus.setText(MainApp.gs(R.string.med_state_sleeping) + "\n" +
        //                DateUtil.timeString(pump.sleepStartTime) +
        //                " (" + String.format(MainApp.gs(R.string.minago),
        //                agoMin) + ")");
        vConnExtStatusLabel.setText(MainApp.gs(R.string.med_progress_label_sleeping));
        text = precision.format(TimeUtil.countdownTimer(
                pump.sleepStartTime,
                pump.wakeInterval)) +
                MainApp.gs(R.string.med_progress_time);
        vConnExtStatus.setText(text);
    }

    private void deviceInitializing() {
        vConnStatus.setText(MainApp.gs(R.string.med_state_waiting));
        vConnExtStatusLabel.setText(MainApp.gs(R.string.med_progress_label_progress));
        vConnExtStatus.setText(MainApp.gs(R.string.med_progress_waiting));
    }

    private void deviceScanning() {
        vConnStatus.setText(MainApp.gs(R.string.med_state_scanning));
        vConnExtStatusLabel.setText(MainApp.gs(R.string.med_progress_label_time));
        String text = precision.format(TimeUtil.elapsedTime(
                MedtronicPump.getInstance().scanStartTime)) +
                MainApp.gs(R.string.med_progress_time);
        vConnExtStatus.setText(text);
    }

    private void deviceConnecting() {
        vConnStatus.setText(MainApp.gs(R.string.med_state_connecting));
        vConnExtStatusLabel.setText(MainApp.gs(R.string.med_progress_label_attempt));
        vConnExtStatus.setText(String.valueOf(MedtronicPump.getInstance().connectionAttempts));
    }

    private void deviceConnected() {
        vConnStatus.setText(MainApp.gs(R.string.med_state_connected));
        vConnExtStatusLabel.setText(MainApp.gs(R.string.med_progress_label_attempt));
        vConnExtStatus.setText(String.valueOf(MedtronicPump.getInstance().connectionAttempts));
    }

    private void deviceCommunicating() {
        vConnStatus.setText(MainApp.gs(R.string.med_state_communicating));
        vConnExtStatusLabel.setText(MainApp.gs(R.string.med_progress_label_progress));
        updatePumpProgress();
    }

    private void updatePumpProgress() {
        String status = MainApp.gs(R.string.med_progress_command_1);
        MedtronicPump pump = MedtronicPump.getInstance();
        switch (pump.actionState) {
            case 0:
                status += MainApp.gs(R.string.med_progress_command_ping);
                break;
            case 1:
                status += MainApp.gs(R.string.med_progress_command_bolus);
                break;
            case 2:
                status += MainApp.gs(R.string.med_progress_command_temp);
                break;
            case 3:
                status += MainApp.gs(R.string.med_progress_command_sleep);
                break;
        }
        status += (MainApp.gs(R.string.med_progress_command_2) + (pump.commandRetries + 1));
        vConnExtStatus.setText(status);
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
