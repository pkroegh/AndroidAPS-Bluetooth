package info.nightscout.androidaps.plugins.pump.medtronicESP;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.IBinder;

import com.squareup.otto.Subscribe;

import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.R;
import info.nightscout.androidaps.data.DetailedBolusInfo;
import info.nightscout.androidaps.data.Profile;
import info.nightscout.androidaps.data.PumpEnactResult;
import info.nightscout.androidaps.db.Source;
import info.nightscout.androidaps.db.TemporaryBasal;
import info.nightscout.androidaps.events.EventAppExit;
import info.nightscout.androidaps.events.EventPreferenceChange;
import info.nightscout.androidaps.interfaces.Constraint;
import info.nightscout.androidaps.interfaces.PluginType;
import info.nightscout.androidaps.logging.L;
import info.nightscout.androidaps.plugins.pump.common.defs.PumpType;
import info.nightscout.androidaps.plugins.pump.danaR.comm.MsgBolusStartWithSpeed;
import info.nightscout.androidaps.plugins.pump.medtronicESP.services.MedtronicService;
import info.nightscout.androidaps.plugins.treatments.Treatment;
import info.nightscout.androidaps.plugins.treatments.TreatmentsPlugin;
import info.nightscout.androidaps.utils.SP;

/*
 *   Modified version of VirtualPumpPlugin and DanaRPlugin by mike
 *
 *   Allows for communication with a Medtronic pump, through an ESP32 microcontroller
 *   Created by ldaug99 on 2019-02-17
 */

public class MedtronicPlugin extends AbstractMedtronicPlugin {
    private static MedtronicPlugin plugin = null;

    public static MedtronicPlugin getPlugin() {
        if (plugin == null)
            plugin = new MedtronicPlugin();
        return plugin;
    }

    public MedtronicPlugin() {
        super();
        pumpDescription.setPumpDescription(PumpType.Medtronic_ESP);
    }

    @Override
    protected void onStart() {
        Context context = MainApp.instance().getApplicationContext();
        Intent intent = new Intent(context, MedtronicService.class);
        context.bindService(intent, mConnection, Context.BIND_AUTO_CREATE);
        MainApp.bus().register(this);
        super.onStart();
    }

    @Override
    protected void onStop() {
        Context context = MainApp.instance().getApplicationContext();
        context.unbindService(mConnection);
        MainApp.bus().unregister(this);
    }

    private ServiceConnection mConnection = new ServiceConnection() {
        public void onServiceDisconnected(ComponentName name) {
            log.debug("MedtronicService on disconnect");
            sMedtronicService = null;
        }

        public void onServiceConnected(ComponentName name, IBinder service) {
            log.debug("MedtronicService on connect");
            MedtronicService.LocalBinder mLocalBinder = (MedtronicService.LocalBinder) service;
            sMedtronicService = mLocalBinder.getServiceInstance();
            sMedtronicService.updatePreferences();
        }
    };

    @SuppressWarnings("UnusedParameters")
    @Subscribe
    public void onStatusEvent(final EventAppExit e) { //TODO what does this do?
        //sMedtronicService.stopService();
        //MainApp.instance().getApplicationContext().unbindService(mConnection);
    }

    @Override
    public PumpEnactResult deliverTreatment(DetailedBolusInfo detailedBolusInfo) { // TODO fix so that temp is correctly set in pump
        if (sMedtronicService != null){
            if (detailedBolusInfo.insulin > 0 || detailedBolusInfo.carbs > 0) {
                Treatment t = new Treatment();
                t.isSMB = detailedBolusInfo.isSMB;
                sMedtronicService.bolus(detailedBolusInfo.insulin);
                PumpEnactResult result = new PumpEnactResult();
                result.success = Math.abs(detailedBolusInfo.insulin - t.insulin) <
                        pumpDescription.bolusStep;
                result.bolusDelivered = t.insulin;
                result.carbsDelivered = detailedBolusInfo.carbs;
                if (!result.success)
                    result.comment = String.format(MainApp.gs(R.string.boluserrorcode),
                            detailedBolusInfo.insulin, t.insulin, 0);
                else
                    result.comment = MainApp.gs(R.string.virtualpump_resultok);
                detailedBolusInfo.insulin = t.insulin;
                detailedBolusInfo.date = System.currentTimeMillis();
                TreatmentsPlugin.getPlugin().addToHistoryTreatment(detailedBolusInfo,
                        false);
                return result;
            } else {
                PumpEnactResult result = new PumpEnactResult();
                result.success = false;
                result.bolusDelivered = 0d;
                result.carbsDelivered = 0d;
                result.comment = MainApp.gs(R.string.danar_invalidinput);
                log.error("deliverTreatment: Invalid input");
                return result;
            }
        } else {
            PumpEnactResult result = new PumpEnactResult();
            result.success = false;
            result.comment = MainApp.gs(R.string.medtronicESP_result_failed);
            return result;
        }
    }

    @Override
    public PumpEnactResult setTempBasalAbsolute(Double absoluteRate, Integer durationInMinutes, // TODO fix so that temp is correctly set in pump
                                                Profile profile, boolean enforceNew) {
        if (sMedtronicService != null){
            MedtronicPump pump = MedtronicPump.getInstance();
            PumpEnactResult result = new PumpEnactResult();
            result.success = true;
            result.absolute = pump.tempBasal;
            result.duration = pump.tempBasalDuration;
            result.isPercent = false;
            result.isTempCancel = false;
            if (TreatmentsPlugin.getPlugin().isTempBasalInProgress()) {
                result.enacted = true;
                TemporaryBasal tempStop = new TemporaryBasal().date(System.currentTimeMillis()).
                        source(Source.USER);
                TreatmentsPlugin.getPlugin().addToHistoryTempBasal(tempStop);
            }
            sMedtronicService.tempBasal(absoluteRate, durationInMinutes);
            return result;
        } else {
            PumpEnactResult result = new PumpEnactResult();
            result.success = false;
            result.comment = MainApp.gs(R.string.medtronicESP_result_failed);
            return result;
        }
    }

    @Override
    public PumpEnactResult cancelTempBasal(boolean force) { // TODO fix so that temp is correctly set in pump
        if (sMedtronicService != null){
            PumpEnactResult result = new PumpEnactResult();
            result.success = true;
            result.isTempCancel = true;
            result.comment = MainApp.gs(R.string.virtualpump_resultok);
            if (TreatmentsPlugin.getPlugin().isTempBasalInProgress()) {
                result.enacted = true;
                TemporaryBasal tempStop = new TemporaryBasal().date(System.currentTimeMillis()).
                        source(Source.USER);
                TreatmentsPlugin.getPlugin().addToHistoryTempBasal(tempStop);
            }
            sMedtronicService.tempBasalStop();
            return result;
        } else {
            PumpEnactResult result = new PumpEnactResult();
            result.success = false;
            result.comment = MainApp.gs(R.string.medtronicESP_result_failed);
            return result;
        }
    }

    @Override
    public boolean isFakingTempsByExtendedBoluses() {
        if (sMedtronicService != null) {
            return sMedtronicService.isUsingExtendedBolus();
        }
        return false;
    }

    @Override
    public PumpEnactResult setExtendedBolus(Double insulin, Integer durationInMinutes) { // TODO implement this


        PumpEnactResult result = new PumpEnactResult();
        result.enacted = false;
        result.success = false;
        return result;
    }

    @Override
    public PumpEnactResult cancelExtendedBolus() { // TODO implement this


        PumpEnactResult result = new PumpEnactResult();
        result.enacted = false;
        result.success = false;
        return result;
    }
}
