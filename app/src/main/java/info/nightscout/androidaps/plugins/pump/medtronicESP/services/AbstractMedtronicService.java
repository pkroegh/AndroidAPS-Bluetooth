package info.nightscout.androidaps.plugins.pump.medtronicESP.services;

import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.Intent;
import android.os.IBinder;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Set;
import java.util.UUID;

import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.R;
import info.nightscout.androidaps.logging.L;
import info.nightscout.androidaps.plugins.pump.danaR.services.AbstractSerialIOThread;
import info.nightscout.androidaps.plugins.pump.medtronicESP.MedtronicPump;
import info.nightscout.androidaps.utils.SP;
import info.nightscout.androidaps.utils.ToastUtils;

/*
 *   Created by ldaug99 on 2019-02-17
 */

public abstract class AbstractMedtronicService extends Service {
    protected Logger log = LoggerFactory.getLogger(L.PUMP);

    protected IBinder mBinder; // Binder to plugin

    public abstract void connectESP();
    public abstract void disconnectESP();

    public abstract boolean getRunThread();
    public abstract void setRunThread(boolean state);

    public abstract void bolus(double bolus);
    public abstract void tempBasal(double absoluteRate, int durationInMinutes);
    public abstract void tempBasalStop();
    public abstract void extendedBolus(double insulin, int durationInHalfHours); // TODO implement this
    public abstract void extendedBolusStop(); // TODO implement this

    public abstract void updatePreferences();

    abstract void killService();

    public void connect() {} //TODO add connect feature
    public boolean isConnected() {
        return !MedtronicPump.getInstance().loopHandshake;
    }
    public void disconnect() {} //TODO add disconnect feature

    public IBinder onBind(Intent intent) {
        return mBinder;
    }

    public boolean isFakingConnection() {
        return MedtronicPump.getInstance().isFakingConnection;
    }

    public boolean isUsingExtendedBolus() {
        return MedtronicPump.getInstance().isUsingExtendedBolus;
    }

    public void stopService() {
        disconnectESP();
        killService();
        this.stopSelf();
    }
}
