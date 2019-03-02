package info.nightscout.androidaps.plugins.PumpMedtronic.services;

/*
 *   Copy of DanaR AbstractSerialIOThread by mike
 */

public abstract class AbstractIOThread extends Thread {
    protected abstract boolean sendMessage(String message);
    protected abstract void disconnect();
}