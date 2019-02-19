package info.nightscout.androidaps.plugins.PumpMedtronic.services;

/*
 *   Copy of DanaR AbstractSerialIOThread by mike
 */

public abstract class AbstractIOThread extends Thread {
    public abstract void sendMessage(String message);
    public abstract void disconnect();
}