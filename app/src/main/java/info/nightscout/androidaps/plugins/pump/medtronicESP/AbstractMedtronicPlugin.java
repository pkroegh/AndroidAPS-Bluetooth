package info.nightscout.androidaps.plugins.pump.medtronicESP;

import org.json.JSONException;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Date;
import java.util.List;

import info.nightscout.androidaps.BuildConfig;
import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.R;
import info.nightscout.androidaps.data.Profile;
import info.nightscout.androidaps.data.PumpEnactResult;
import info.nightscout.androidaps.db.ExtendedBolus;
import info.nightscout.androidaps.db.TemporaryBasal;
import info.nightscout.androidaps.interfaces.PluginBase;
import info.nightscout.androidaps.interfaces.PluginDescription;
import info.nightscout.androidaps.interfaces.PluginType;
import info.nightscout.androidaps.interfaces.PumpDescription;
import info.nightscout.androidaps.interfaces.PumpInterface;
import info.nightscout.androidaps.logging.L;
import info.nightscout.androidaps.plugins.configBuilder.ProfileFunctions;
import info.nightscout.androidaps.plugins.general.actions.defs.CustomAction;
import info.nightscout.androidaps.plugins.general.actions.defs.CustomActionType;
import info.nightscout.androidaps.plugins.pump.medtronicESP.services.AbstractMedtronicService;
import info.nightscout.androidaps.plugins.treatments.TreatmentsPlugin;
import info.nightscout.androidaps.utils.DateUtil;
import info.nightscout.androidaps.utils.DecimalFormatter;

/*
 *   Modified version of VirtualPumpPlugin and DanaRPlugin by mike
 *
 *   Allows for communication with a Medtronic pump, through an ESP32 microcontroller
 *   Created by ldaug99 on 2019-02-17
 */

public abstract class AbstractMedtronicPlugin extends PluginBase implements PumpInterface {
    protected Logger log = LoggerFactory.getLogger(L.PUMP);

    AbstractMedtronicService sMedtronicService;

    public PumpDescription pumpDescription = new PumpDescription();

    public AbstractMedtronicPlugin() {
        super(new PluginDescription()
                .mainType(PluginType.PUMP)
                .fragmentClass(MedtronicFragment.class.getName())
                .pluginName(R.string.medtronicESP)
                .shortName(R.string.medtronicESP_shortname)
                .preferencesId(R.xml.pref_medtronic)
                .description(R.string.description_pump_medtronicESP_r)
        );
    }

    @Override
    public int getPreferencesId() {
        return R.xml.pref_medtronic;
    }

    @Override
    public String getName() {
        return MainApp.gs(R.string.medtronicESP);
    }

    @Override
    public String deviceID() {
        return MedtronicPump.getInstance().mDevName;
    }

    @Override
    public PumpDescription getPumpDescription() {
        return pumpDescription;
    }

    @Override
    public void getPumpStatus() {
        if (sMedtronicService != null) {
            MedtronicPump pump = MedtronicPump.getInstance();
            pumpDescription.basalStep = pump.basalStep;
            pumpDescription.bolusStep = pump.bolusStep;
        }
    }

    @Override
    public double getReservoirLevel() { return MedtronicPump.getInstance().reservoirRemainingUnits; }

    @Override
    public int getBatteryLevel() { return MedtronicPump.getInstance().batteryRemaining; }

    @Override
    public long lastDataTime() {
        MedtronicPump pump = MedtronicPump.getInstance();
        if (pump.failedToReconnect && !pump.loopHandshake) {
            return pump.lastConnection;
        } else {
            return System.currentTimeMillis();
        }
    }

    @Override
    public boolean isSuspended() {
        return false;
    }

    @Override
    public boolean isBusy() { return false; }

    @Override
    public void connect(String from) {
        if (sMedtronicService != null) sMedtronicService.connect();
    }

    @Override
    public boolean isConnected() { return true; }

    @Override
    public boolean isConnecting() { return false; }

    @Override
    public boolean isInitialized() {
        return sMedtronicService != null;
    }

    @Override
    public boolean isHandshakeInProgress() {
        return false;
    }

    @Override
    public void finishHandshaking() {}

    @Override
    public void disconnect(String from) {
        if (sMedtronicService != null) sMedtronicService.disconnect();
    }

    @Override
    public void stopConnecting() {
        if (sMedtronicService != null) sMedtronicService.disconnect();
    }
    @Override
    public double getBaseBasalRate() {
        Profile profile = ProfileFunctions.getInstance().getProfile();
        if (profile != null) {
            MedtronicPump.getInstance().baseBasal = profile.getBasal();
            return profile.getBasal();
        } else {
            return 0d;
        }
    }

    @Override
    public PumpEnactResult setNewBasalProfile(Profile profile) {
        PumpEnactResult result = new PumpEnactResult();
        result.success = true;
        result.enacted = true;
        result.comment = "OK";
        return result;
    }

    @Override
    public boolean isThisProfileSet(Profile profile) {
        return true;
    }

    @Override
    public void stopBolusDelivering() {  // TODO implement this
    }

    @Override
    public PumpEnactResult setTempBasalPercent(Integer percent, Integer durationInMinutes, Profile profile, boolean enforceNew) {
        PumpEnactResult result = new PumpEnactResult();
        result.enacted = false;
        result.success = false;
        return result;
    }

    @Override
    public PumpEnactResult loadTDDs() {
        return null;
    }

    public String shortStatus(boolean veryShort) {
        MedtronicPump pump = MedtronicPump.getInstance(); //TODO implement last connection tracking
        String ret = "";
        /*
        if (pump.lastConnection != 0) {
            Long agoMsec = System.currentTimeMillis() - pump.lastConnection;
            int agoMin = (int) (agoMsec / 60d / 1000d);
            ret += "LastConn: " + agoMin + " minago\n";
        }
        */
        Long agoMsec = System.currentTimeMillis();
        int agoMin = (int) (agoMsec / 60d / 1000d);
        ret += "LastConn: " + agoMin + " minago\n";
        ret += "Reserv: " + DecimalFormatter.to0Decimal(pump.reservoirRemainingUnits) + "U\n";
        ret += "Batt: " + pump.batteryRemaining + "\n";
        return ret;
    }

    @Override
    public JSONObject getJSONStatus(Profile profile, String profilename) {
        MedtronicPump pump = MedtronicPump.getInstance();
        long now = System.currentTimeMillis();
        /*
        if (pump.lastConnection + 5 * 60 * 1000L < now) {
            return null;
        }
        */
        JSONObject pumpjson = new JSONObject();
        JSONObject battery = new JSONObject();
        JSONObject status = new JSONObject();
        JSONObject extended = new JSONObject();
        try {
            battery.put("percent", pump.batteryRemaining);
            status.put("status", pump.isDeviceSleeping ? "suspended" : "normal");
            status.put("timestamp", DateUtil.toISOString(pump.lastConnection));
            extended.put("Version", BuildConfig.VERSION_NAME + "-" + BuildConfig.BUILDVERSION);
            TemporaryBasal tb = TreatmentsPlugin.getPlugin().getRealTempBasalFromHistory(now);
            if (tb != null) {
                extended.put("TempBasalAbsoluteRate", tb.tempBasalConvertedToAbsolute(now, profile));
                extended.put("TempBasalStart", DateUtil.dateAndTimeString(tb.date));
                extended.put("TempBasalRemaining", tb.getPlannedRemainingMinutes());
            }
            ExtendedBolus eb = TreatmentsPlugin.getPlugin().getExtendedBolusFromHistory(now);
            if (eb != null) {
                extended.put("ExtendedBolusAbsoluteRate", eb.absoluteRate());
                extended.put("ExtendedBolusStart", DateUtil.dateAndTimeString(eb.date));
                extended.put("ExtendedBolusRemaining", eb.getPlannedRemainingMinutes());
            }
            extended.put("BaseBasalRate", getBaseBasalRate());
            try {
                extended.put("ActiveProfile", profilename);
            } catch (Exception ignored) {
            }

            pumpjson.put("battery", battery);
            pumpjson.put("status", status);
            pumpjson.put("extended", extended);
            pumpjson.put("reservoir", (int) pump.reservoirRemainingUnits);
            pumpjson.put("clock", DateUtil.toISOString(new Date()));
        } catch (JSONException e) {
            log.error("Unhandled exception", e);
        }
        return pumpjson;
    }

    @Override
    public boolean canHandleDST() {
        return false;
    }

    @Override
    public List<CustomAction> getCustomActions() {
        return null;
    }

    @Override
    public void executeCustomAction(CustomActionType customActionType) {}
}
