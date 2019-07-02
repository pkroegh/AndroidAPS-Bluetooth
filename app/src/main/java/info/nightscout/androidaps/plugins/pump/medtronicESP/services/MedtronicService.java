package info.nightscout.androidaps.plugins.pump.medtronicESP.services;

import android.os.Binder;
import android.os.SystemClock;

import com.squareup.otto.Subscribe;

import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.R;
import info.nightscout.androidaps.events.EventPreferenceChange;
import info.nightscout.androidaps.plugins.pump.medtronicESP.MedtronicPump;
import info.nightscout.androidaps.plugins.pump.medtronicESP.events.EventUpdateGUI;
import info.nightscout.androidaps.plugins.treatments.TreatmentsPlugin;
import info.nightscout.androidaps.utils.SP;
import info.nightscout.androidaps.utils.ToastUtils;
/*
 *   Created by ldaug99 on 2019-02-17
 */

public class MedtronicService extends AbstractMedtronicService {
    private ConnectThread mConnectThread;

    public MedtronicService() {
        mBinder = new MedtronicService.LocalBinder();
        registerBus();
        connectESP();
    }

    public class LocalBinder extends Binder {
        public MedtronicService getServiceInstance() {
            return MedtronicService.this;
        }
    }

    private void registerBus() {
        try {
            MainApp.bus().unregister(this);
        } catch (RuntimeException x) {
        }
        MainApp.bus().register(this);
    }

    /* Connect and disconnect pump */
    public void connectESP() {
        if (MedtronicPump.getInstance().isFakingConnection) return;
        if (!isPasswordSet()) return;
        resetPumpInstance();
        startThread();
        MainApp.bus().post(new EventUpdateGUI()); // Update fragment, with new pump status
    }

    public void disconnectESP() {
        stopThread();
        resetPumpInstance();
        MainApp.bus().post(new EventUpdateGUI()); // Update fragment, with new pump status
    }

    void killService() {} //TODO add feature

    public boolean getRunThread() {
        if (mConnectThread != null) {
            return mConnectThread.getRunConnectThread();
        }
        return false;
    }

    private boolean isPasswordSet() {
        updatePreferences();
        if (MedtronicPump.getInstance().pump_password == null) {
            ToastUtils.showToastInUiThread(MainApp.instance().getApplicationContext(),
                    MainApp.gs(R.string.medtronicESP_noPassOrDevice));
            return false;
        }
        return true;
    }

    private void resetPumpInstance() {
        MedtronicPump pump = MedtronicPump.getInstance();
        pump.connectionAttempts = 0;
        pump.isConnecting = false;
        pump.isConnected = false;
        pump.isSleeping = false;
        pump.isReadyForMessage = false;
    }

    private void startThread() {
        if (mConnectThread != null) {
            if (!mConnectThread.getRunConnectThread()) {
                mConnectThread.setRunConnectThread(false);
                SystemClock.sleep(200);
                mConnectThread = new ConnectThread();
            }
        } else {
            mConnectThread = new ConnectThread();
        }
    }

    private void stopThread() {
        if (mConnectThread != null) {
            mConnectThread.setRunConnectThread(false);
        }
    }

    /* Pump actions */
    public void bolus(double bolus) {
        MedtronicPump pump = MedtronicPump.getInstance();
        if (pump.isFakingConnection) return;
        pump.bolusToDeliver = bolus;
        pump.deliverBolus = true;
    }

    public void tempBasalStop() {
        MedtronicPump pump = MedtronicPump.getInstance();
        if (pump.isFakingConnection) return;
        pump.tempAction = 2;
        pump.newTempAction = true;
    }

    public void tempBasal(double absoluteRate, int durationInMinutes) {
        MedtronicPump pump = MedtronicPump.getInstance();
        if (pump.isFakingConnection) return;
        pump.tempBasal = absoluteRate;
        pump.tempBasalDuration = durationInMinutes;
        pump.tempAction = 1;
        pump.newTempAction = true;
    }

    public void extendedBolus(double insulin, int durationInHalfHours) {  // TODO implement this
        MedtronicPump pump = MedtronicPump.getInstance();
        if (pump.isFakingConnection) return;

    }

    public void extendedBolusStop() {  // TODO implement this
        MedtronicPump pump = MedtronicPump.getInstance();
        if (pump.isFakingConnection) return;

    }

    /* Preference management */
    @Subscribe
    public void onStatusEvent(final EventPreferenceChange s) {
        updatePreferences();
    }

    public void updatePreferences() {
        //updateDeviceNameFromPref();
        updateWakeIntervalFromPref();
        updateExtBolusFromPref();
        updateFakeFromPref();
        updatePassFromPref();
        updateNSFromPref();
    }

    /*
    private void updateDeviceNameFromPref() {
        MedtronicPump pump = MedtronicPump.getInstance();
        String mDevName = SP.getString(MainApp.gs(R.string.key_medtronicESP_bt_name), null);
        if (mDevName != null && !mDevName.equals(pump.mDevName)) {
            pump.mDevName = mDevName;
        }
    }
    */

    private void updateWakeIntervalFromPref() {
        MedtronicPump pump = MedtronicPump.getInstance();
        int previousValue = pump.wakeInterval;
        int wakeInterval = SP.getInt(R.string.key_medtronicESP_wakeinterval, 1);
        /* //TODO: Gives: "Attempt to invoke virtual method 'android.content.res.Resources android.content.Context.getResources()' on a null object reference" why?
        int maxInterval = this.getResources().getInteger(R.integer.ESP_max_sleep_interval);
        int minInterval = this.getResources().getInteger(R.integer.ESP_min_sleep_interval);
        if (wakeInterval != previousValue) {
            if (wakeInterval > maxInterval) wakeInterval = maxInterval;
            if (wakeInterval < minInterval) wakeInterval = minInterval;
            pump.wakeInterval = wakeInterval;
        }
        */
        if (wakeInterval != previousValue) {
            if (wakeInterval > 5) wakeInterval = 5;
            if (wakeInterval < 1) wakeInterval = 1;
            pump.wakeInterval = wakeInterval;
        }
    }

    private void updateExtBolusFromPref() {
        MedtronicPump pump = MedtronicPump.getInstance();
        boolean previousValue = pump.isUsingExtendedBolus;
        pump.isUsingExtendedBolus = SP.getBoolean(R.string.key_medtronicESP_useextended, false);
        if (pump.isUsingExtendedBolus != previousValue &&
                TreatmentsPlugin.getPlugin().isInHistoryExtendedBoluslInProgress()) {
            extendedBolusStop();
        }
    }

    private void updateFakeFromPref() {
        MedtronicPump pump = MedtronicPump.getInstance();
        boolean previousValue = pump.isFakingConnection;
        pump.isFakingConnection = SP.getBoolean(R.string.key_medtronicESP_fake, false);
        if (pump.isFakingConnection != previousValue) {
            if (!pump.isFakingConnection) {
                connectESP();
            } else {
                disconnectESP();
            }
        }
    }

    private void updatePassFromPref() {
        MedtronicPump pump = MedtronicPump.getInstance();
        String new_password = SP.getString(R.string.key_medtronicESP_password, null);
        if (new_password != null && !new_password.equals(pump.pump_password)) {
            pump.pump_password = new_password;
            connectESP();
        }
    }

    private void updateNSFromPref() {
        MedtronicPump.getInstance().isUploadingToNS =
                SP.getBoolean(R.string.key_medtronicESP_uploadNS, false);
    }
}