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
import info.nightscout.androidaps.plugins.pump.medtronicESP.MedtronicPump;
import info.nightscout.androidaps.utils.SP;
import info.nightscout.androidaps.utils.ToastUtils;

/*
 *   Created by ldaug99 on 2019-02-17
 */

public abstract class AbstractMedtronicService extends Service {
    protected Logger log = LoggerFactory.getLogger(L.PUMP);

    protected BluetoothSocket mRfcommSocket; // Bluetooth socket
    protected BluetoothDevice mBTDevice; // Bluetooth device name

    public boolean useExtendedBoluses = false; // If true, use extended bolus TODO implement this
    public boolean fakeESPconnection = false; // If true, run plugin as normal, but without ESP connection
    public boolean uploadCommandsToNS = false; // If true, upload commands send to NS and conformations of commands

    protected AbstractIOThread mSerialIOThread; // Pointer to bluetooth input/output thread

    protected IBinder mBinder; // Binder to plugin

    protected final UUID BTMODULEUUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");

    public abstract void connectESP();
    public abstract void disconnectESP();

    public abstract void bolus(double bolus);
    public abstract void tempBasal(double absoluteRate, int durationInMinutes);
    public abstract void tempBasalStop();
    public abstract void extendedBolus(double insulin, int durationInHalfHours); // TODO implement this
    public abstract void extendedBolusStop(); // TODO implement this

    public abstract boolean isThreadRunning();
    public abstract void updatePreferences();

    abstract void killService();

    public IBinder onBind(Intent intent) {
        return mBinder;
    }

    public void stopService() {
        disconnectESP();
        killService();
        this.stopSelf();
    }

    public void connect() {} //TODO add connect feature

    public boolean isConnected() {
        return !MedtronicPump.getInstance().loopHandshake;
    }

    public void disconnect() {} //TODO add disconnect feature

    protected void getBTSocketForSelectedPump() {
        MedtronicPump pump = MedtronicPump.getInstance();
        pump.mDevName = SP.getString(MainApp.gs(R.string.key_medtronicESP_bt_name), "");
        BluetoothAdapter bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if (bluetoothAdapter != null) {
            Set<BluetoothDevice> bondedDevices = bluetoothAdapter.getBondedDevices();
            for (BluetoothDevice device : bondedDevices) {
                if (pump.mDevName.equals(device.getName())) {
                    mBTDevice = device;
                    try {
                        mRfcommSocket = mBTDevice.createRfcommSocketToServiceRecord(BTMODULEUUID);
                    } catch (IOException e) {
                        log.error("Error creating socket: ", e);
                    }
                    break;
                }
            }
        } else {
            ToastUtils.showToastInUiThread(MainApp.instance().getApplicationContext(), MainApp.gs(R.string.nobtadapter));
        }
        if (mBTDevice == null) {
            ToastUtils.showToastInUiThread(MainApp.instance().getApplicationContext(), MainApp.gs(R.string.devicenotfound));
        }
    }

    public boolean isBTConnected() {
        return isFakingConnection() || (mRfcommSocket != null && mRfcommSocket.isConnected());
    }

    public boolean isFakingConnection() {
        return fakeESPconnection;
    }
    public boolean isUsingExtendedBolus() {
        return useExtendedBoluses;
    }
}
