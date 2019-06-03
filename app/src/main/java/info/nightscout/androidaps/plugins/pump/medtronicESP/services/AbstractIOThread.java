package info.nightscout.androidaps.plugins.pump.medtronicESP.services;

/*
 *   Copy of DanaR AbstractSerialIOThread by mike
 */

public abstract class AbstractIOThread extends Thread {
    protected abstract void sendMessage(String message);
    protected abstract void disconnect();
}