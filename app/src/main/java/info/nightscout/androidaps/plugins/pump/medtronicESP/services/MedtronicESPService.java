package info.nightscout.androidaps.plugins.pump.medtronicESP.services;

import android.os.Binder;
import android.os.SystemClock;

import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.events.EventAppExit;
import info.nightscout.androidaps.events.EventPreferenceChange;
import info.nightscout.androidaps.events.EventPumpStatusChanged;
import info.nightscout.androidaps.logging.L;
import info.nightscout.androidaps.plugins.bus.RxBus;
import info.nightscout.androidaps.plugins.pump.medtronicESP.MedtronicESPPump;
import info.nightscout.androidaps.utils.FabricPrivacy;
import io.reactivex.disposables.CompositeDisposable;
import io.reactivex.schedulers.Schedulers;

/*
 *   Created by ldaug99 on 2019-02-17
 */

public class MedtronicESPService extends AbstractMedtronicESPService {
    protected ConnectThread mConnectThread;

    private CompositeDisposable disposable = new CompositeDisposable();

    public MedtronicESPService() {
        mBinder = new MedtronicESPService.LocalBinder();
        connectESP();
    }

    @Override
    public void onCreate() {
        super.onCreate();
        disposable.add(RxBus.INSTANCE
                .toObservable(EventPreferenceChange.class)
                .observeOn(Schedulers.io())
                .subscribe(event -> { preferencesChanged(); }, FabricPrivacy::logException)
        );
        disposable.add(RxBus.INSTANCE
                .toObservable(EventAppExit.class)
                .observeOn(Schedulers.io())
                .subscribe(event -> {
                    if (L.isEnabled(L.PUMP))
                        log.debug("EventAppExit received");
                    stopThread();
                    stopSelf();
                }, FabricPrivacy::logException)
        );
    }

    public void preferencesChanged() {
        if (MedtronicESPPump.updateExtBolusFromPref()) {
            extendedBolusStop();
        }
        boolean fakeConnection = MedtronicESPPump.getInstance().isFakingConnection;
        if (MedtronicESPPump.updateFakeFromPref()) {
            if (!fakeConnection) {
                connectESP();
            } else {
                disconnectESP();
            }
        }
        if (MedtronicESPPump.updatePassFromPref()) {
            connectESP();
        }
        MedtronicESPPump.updateNSFromPref();
    }

    @Override
    public void onDestroy() {
        disposable.clear();
        super.onDestroy();
    }

    public class LocalBinder extends Binder {
        public MedtronicESPService getServiceInstance() {
            return MedtronicESPService.this;
        }
    }

    /* Connect and disconnect pump */
    public void connectESP() {
        if (MedtronicESPPump.getInstance().isFakingConnection) return;
        startThread();
        RxBus.INSTANCE.send(new EventPumpStatusChanged(EventPumpStatusChanged.Status.CONNECTING)); // Update fragment, with new pump status
    }

    public void disconnectESP() {
        stopThread();
        RxBus.INSTANCE.send(new EventPumpStatusChanged(EventPumpStatusChanged.Status.DISCONNECTING)); // Update fragment, with new pump status
        MedtronicESPPump.clearInstance();
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
        MedtronicESPPump pump = MedtronicESPPump.getInstance();
        if (pump.isFakingConnection) return;

    }

    public void extendedBolusStop() {  // TODO implement this
        MedtronicESPPump pump = MedtronicESPPump.getInstance();
        if (pump.isFakingConnection) return;

    }
}