package info.nightscout.androidaps.plugins.PumpMedtronic.services;

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
import info.nightscout.androidaps.plugins.PumpMedtronic.services.AbstractIOThread;
import info.nightscout.androidaps.plugins.Treatments.Treatment;
import info.nightscout.utils.SP;
import info.nightscout.utils.ToastUtils;

/*
 *   Created by ldaug99 on 2019-02-17
 */

public abstract class AbstractMedtronicService extends Service {
    protected Logger log = LoggerFactory.getLogger(L.PUMP);

    protected String mDevName;

    protected Boolean mDeviceFirstConnect = true;

    protected BluetoothSocket mRfcommSocket;
    protected BluetoothDevice mBTDevice;

    protected AbstractIOThread mSerialIOThread;

    protected IBinder mBinder;

    protected final UUID BTMODULEUUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");

    public abstract void queueMessage(String message);

    public IBinder onBind(Intent intent) {
        return mBinder;
    }

    public boolean isBusy() {
        return false;
    }

    public void connect() {

    }

    public boolean isConnected() {
        return mDeviceFirstConnect;
    }

    public boolean isConnecting() {
        return false;
    }

    public void disconnect() {

    }

    public void stopConnecting() {

    }

    protected void getBTSocketForSelectedPump() {
        mDevName = SP.getString(MainApp.gs(R.string.key_medtronicESP_bt_name), "");
        BluetoothAdapter bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if (bluetoothAdapter != null) {
            Set<BluetoothDevice> bondedDevices = bluetoothAdapter.getBondedDevices();
            for (BluetoothDevice device : bondedDevices) {
                if (mDevName.equals(device.getName())) {
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
        return mRfcommSocket != null && mRfcommSocket.isConnected();
    }

}
