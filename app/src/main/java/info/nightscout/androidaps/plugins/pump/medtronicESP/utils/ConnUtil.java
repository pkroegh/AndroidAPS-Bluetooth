package info.nightscout.androidaps.plugins.pump.medtronicESP.utils;

import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.R;
import info.nightscout.androidaps.plugins.bus.RxBus;
import info.nightscout.androidaps.plugins.general.overview.events.EventNewNotification;
import info.nightscout.androidaps.plugins.general.overview.notifications.Notification;

public class ConnUtil {

    /**
     * Alerts the user of a generic hardware error or hardware incompatibility.
     *
     */
    public static void hardwareError() { //TODO: Implement

        Notification n = new Notification(Notification.PUMP_UNREACHABLE,
                MainApp.gs(R.string.pump_unreachable), Notification.URGENT);
        n.soundId = R.raw.alarm;
        RxBus.INSTANCE.send(new EventNewNotification(n));
    }

    /**
     * Alerts the user of a specific hardware error or hardware incompatibility.
     *
     */
    public static void hardwareError(String errorType) { //TODO: Implement

        Notification n = new Notification(Notification.PUMP_UNREACHABLE,
                MainApp.gs(R.string.pump_unreachable), Notification.URGENT);
        n.soundId = R.raw.alarm;
        RxBus.INSTANCE.send(new  EventNewNotification(n));
    }

    /**
     * Alerts the user of a generic internal error. These errors should never happen.
     *
     */
    public static void internalError() { //TODO: Implement

        Notification n = new Notification(Notification.PUMP_UNREACHABLE,
                MainApp.gs(R.string.pump_unreachable), Notification.URGENT);
        n.soundId = R.raw.alarm;
        RxBus.INSTANCE.send(new EventNewNotification(n));
    }

    /**
     * Alerts the user of a specific internal error. These errors should never happen.
     *
     */
    public static void internalError(String errorType) { //TODO: Implement

        Notification n = new Notification(Notification.PUMP_UNREACHABLE,
                MainApp.gs(R.string.pump_unreachable), Notification.URGENT);
        n.soundId = R.raw.alarm;
        RxBus.INSTANCE.send(new EventNewNotification(n));
    }

    /**
     * Alerts the user of a generic BLE error.
     *
     */
    public static void bleError() { //TODO: Implement

        Notification n = new Notification(Notification.PUMP_UNREACHABLE,
                MainApp.gs(R.string.pump_unreachable), Notification.URGENT);
        n.soundId = R.raw.alarm;
        RxBus.INSTANCE.send(new EventNewNotification(n));
    }

    /**
     * Alerts the user of a specific BLE error.
     *
     */
    public static void bleError(String errorType) { //TODO: Implement

        Notification n = new Notification(Notification.PUMP_UNREACHABLE,
                MainApp.gs(R.string.pump_unreachable), Notification.URGENT);
        n.soundId = R.raw.alarm;
        RxBus.INSTANCE.send(new EventNewNotification(n));
    }
}
