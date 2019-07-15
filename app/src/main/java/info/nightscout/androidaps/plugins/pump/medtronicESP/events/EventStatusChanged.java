package info.nightscout.androidaps.plugins.pump.medtronicESP.events;

import info.nightscout.androidaps.events.Event;

public class EventStatusChanged extends Event {
    public char action;

    public EventStatusChanged() {
        this.action = '0';
    }

    public EventStatusChanged(char action) {
        this.action = action;
    }
}
