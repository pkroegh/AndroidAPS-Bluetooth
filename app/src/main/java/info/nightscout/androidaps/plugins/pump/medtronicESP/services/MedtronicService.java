package info.nightscout.androidaps.plugins.pump.medtronicESP.services;

import android.os.Binder;
import android.os.SystemClock;

import com.squareup.otto.Subscribe;

import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.events.EventPreferenceChange;
import info.nightscout.androidaps.events.EventPumpStatusChanged;
import info.nightscout.androidaps.plugins.pump.medtronicESP.MedtronicPump;

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
            log.error("Unhandled runtime exception: " + x);
        }
        MainApp.bus().register(this);
    }

    /* Connect and disconnect pump */
    public void connectESP() {
        if (MedtronicPump.getInstance().isFakingConnection) return;
        startThread();
        MainApp.bus().post(new EventPumpStatusChanged(EventPumpStatusChanged.CONNECTING)); // Update fragment, with new pump status
    }

    public void disconnectESP() {
        stopThread();
        MainApp.bus().post(new EventPumpStatusChanged(EventPumpStatusChanged.DISCONNECTING)); // Update fragment, with new pump status
        MedtronicPump.clearInstance();
    }

    public boolean getRunThread() {
        if (mConnectThread != null) {
            return mConnectThread.getRunConnectThread();
        }
        return false;
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
        if (MedtronicPump.updateExtBolusFromPref()) {
            extendedBolusStop();
        }
        boolean fakeConnection = MedtronicPump.getInstance().isFakingConnection;
        if (MedtronicPump.updateFakeFromPref()) {
            if (!fakeConnection) {
                connectESP();
            } else {
                disconnectESP();
            }
        }
        if (MedtronicPump.updatePassFromPref()) {
            connectESP();
        }
        MedtronicPump.updateNSFromPref();
    }

}