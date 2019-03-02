package info.nightscout.androidaps.plugins.PumpMedtronic;

import org.json.JSONException;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Date;

import info.nightscout.androidaps.BuildConfig;
import info.nightscout.androidaps.R;
import info.nightscout.androidaps.data.DetailedBolusInfo;
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
import info.nightscout.androidaps.plugins.ConfigBuilder.ProfileFunctions;
import info.nightscout.androidaps.plugins.PumpMedtronic.services.AbstractMedtronicService;
import info.nightscout.androidaps.plugins.Treatments.TreatmentsPlugin;
import info.nightscout.utils.DateUtil;
import info.nightscout.utils.DecimalFormatter;

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
                .preferencesId(R.xml.pref_medtronicpump)
                .description(R.string.description_pump_medtronicESP_r)
        );
    }

    @Override
    public boolean isSuspended() {
        return false;
    }

    @Override
    public boolean isBusy() {
        return false;
        //if (sMedtronicService == null) return false;
        //return sMedtronicService.isConnected() || sMedtronicService.isConnecting();
    }

    @Override
    public void connect(String from) {
        if (sMedtronicService != null) sMedtronicService.connect();
    }

    @Override
    public boolean isConnected() {
        //return sMedtronicService != null && sMedtronicService.isConnecting();
        return true;
    }

    @Override
    public boolean isConnecting() {

        //return !MedtronicPump.getInstance().isNewPump;
        return false;
    }

    @Override
    public void disconnect(String from) {
        if (serviceNotNull()) {
            sMedtronicService.disconnect();
        }
    }

    @Override
    public void stopConnecting() {
        if (serviceNotNull()) {
            sMedtronicService.stopConnecting();
        }
    }

    @Override
    public void getPumpStatus() {
        if (serviceNotNull()) {
            //sMedtronicService.getPumpStatus();
            pumpDescription.basalStep = MedtronicPump.getInstance().basalStep;
            pumpDescription.bolusStep = MedtronicPump.getInstance().bolusStep;
        }
    }

    public boolean serviceNotNull() {
        return sMedtronicService != null;
    }

    @Override
    public String deviceID() {
        return MedtronicPump.getInstance().serialNumber;
    }

    @Override
    public PumpDescription getPumpDescription() {
        return pumpDescription;
    }

    @Override
    public long lastDataTime() {
        return System.currentTimeMillis(); //TODO implement tracking of missed wake
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
        if (!isInitialized())
            return true; // TODO: not sure what's better. so far TRUE to prevent too many SMS
        MedtronicPump pump = MedtronicPump.getInstance();
        if (pump.pumpProfiles == null)
            return true; // TODO: not sure what's better. so far TRUE to prevent too many SMS
        int basalValues = pump.basal48Enable ? 48 : 24;
        int basalIncrement = pump.basal48Enable ? 30 * 60 : 60 * 60;
        for (int h = 0; h < basalValues; h++) {
            Double pumpValue = pump.pumpProfiles[pump.activeProfile][h];
            Double profileValue = profile.getBasalTimeFromMidnight(h * basalIncrement);
            if (profileValue == null) return true;
            if (Math.abs(pumpValue - profileValue) > getPumpDescription().basalStep) {
                if (L.isEnabled(L.PUMP))
                    log.debug("Diff found. Hour: " + h + " Pump: " + pumpValue + " Profile: " + profileValue);
                return false;
            }
        }
        return true;
    }

    @Override
    public void stopBolusDelivering() {

    }

    @Override
    public PumpEnactResult deliverTreatment(DetailedBolusInfo detailedBolusInfo) {
        PumpEnactResult result = new PumpEnactResult();
        result.enacted = false;
        result.success = false;
        return result;
    }

    @Override
    public PumpEnactResult setTempBasalPercent(Integer percent, Integer durationInMinutes, Profile profile, boolean enforceNew) {
        PumpEnactResult result = new PumpEnactResult();
        result.enacted = false;
        result.success = false;
        return result;
    }

    @Override
    public boolean isFakingTempsByExtendedBoluses() {
        return false;
    }

    @Override
    public PumpEnactResult setExtendedBolus(Double insulin, Integer durationInMinutes) {
        PumpEnactResult result = new PumpEnactResult();
        result.enacted = false;
        result.success = false;
        return result;
    }

    @Override
    public PumpEnactResult cancelExtendedBolus() {
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
        MedtronicPump pump = MedtronicPump.getInstance();
        String ret = "";
        if (pump.lastConnection != 0) {
            Long agoMsec = System.currentTimeMillis() - pump.lastConnection;
            int agoMin = (int) (agoMsec / 60d / 1000d);
            ret += "LastConn: " + agoMin + " minago\n";
        }
        ret += "Reserv: " + DecimalFormatter.to0Decimal(pump.reservoirRemainingUnits) + "U\n";
        ret += "Batt: " + pump.batteryRemaining + "\n";
        return ret;
    }

    @Override
    public JSONObject getJSONStatus(Profile profile, String profilename) {
        MedtronicPump pump = MedtronicPump.getInstance();
        long now = System.currentTimeMillis();
        if (pump.lastConnection + 5 * 60 * 1000L < System.currentTimeMillis()) {
            return null;
        }
        JSONObject pumpjson = new JSONObject();
        JSONObject battery = new JSONObject();
        JSONObject status = new JSONObject();
        JSONObject extended = new JSONObject();
        try {
            battery.put("percent", pump.batteryRemaining);
            status.put("status", pump.mDeviceSleeping ? "suspended" : "normal");
            status.put("timestamp", DateUtil.toISOString(pump.lastConnection));
            extended.put("Version", BuildConfig.VERSION_NAME + "-" + BuildConfig.BUILDVERSION);
            extended.put("PumpIOB", pump.iob);
            if (pump.lastBolusTime != 0) {
                extended.put("LastBolus", DateUtil.dateAndTimeFullString(pump.lastBolusTime));
                extended.put("LastBolusAmount", pump.lastBolusAmount);
            }
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
}
