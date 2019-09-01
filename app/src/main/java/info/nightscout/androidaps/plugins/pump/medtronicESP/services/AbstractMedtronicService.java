package info.nightscout.androidaps.plugins.pump.medtronicESP.services;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/*
 *   Created by ldaug99 on 2019-02-17
 */

public abstract class AbstractMedtronicService extends Service {
    //protected Logger log = LoggerFactory.getLogger(L.PUMP);
    protected Logger log = LoggerFactory.getLogger("Medtronic");

    protected IBinder mBinder; // Binder to plugin

    public abstract void connectESP();
    public abstract void disconnectESP();

    public abstract boolean getRunThread();

    public abstract void extendedBolus(double insulin, int durationInHalfHours); // TODO implement this
    public abstract void extendedBolusStop(); // TODO implement this

    public void connect() {} //TODO add connect feature
    public void disconnect() {} //TODO add disconnect feature

    public IBinder onBind(Intent intent) {
        return mBinder;
    }
}
