package info.nightscout.androidaps.plugins.pump.medtronicESP;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.IBinder;

import com.squareup.otto.Subscribe;

import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.data.DetailedBolusInfo;
import info.nightscout.androidaps.data.Profile;
import info.nightscout.androidaps.data.PumpEnactResult;
import info.nightscout.androidaps.db.Source;
import info.nightscout.androidaps.db.TemporaryBasal;
import info.nightscout.androidaps.events.EventAppExit;
import info.nightscout.androidaps.plugins.pump.common.defs.PumpType;
import info.nightscout.androidaps.plugins.pump.medtronicESP.services.MedtronicService;
import info.nightscout.androidaps.plugins.treatments.Treatment;
import info.nightscout.androidaps.plugins.treatments.TreatmentsPlugin;
import info.nightscout.androidaps.utils.ToastUtils;

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

    private MedtronicPlugin() {
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
        }
    };

    @SuppressWarnings("UnusedParameters")
    @Subscribe
    public void onStatusEvent(final EventAppExit e) { //TODO what does this do?
        //sMedtronicService.stopService();
        //MainApp.instance().getApplicationContext().unbindService(mConnection);
    }

    @Override
    public PumpEnactResult deliverTreatment(DetailedBolusInfo detailedBolusInfo) {
        PumpEnactResult result = pumpAction();
        if (result.success) {
            if (detailedBolusInfo.insulin > 0 || detailedBolusInfo.carbs > 0) {
                log.debug("New bolus to deliver.");
                ToastUtils.showToastInUiThread(MainApp.instance().getApplicationContext(),
                        "Setting bolus on wake.");
                Treatment t = new Treatment();
                t.isSMB = detailedBolusInfo.isSMB;

                result.bolusDelivered = detailedBolusInfo.insulin;
                result.carbsDelivered = detailedBolusInfo.carbs;
                result.comment = "Delivering " + detailedBolusInfo.insulin + " on next wake.";

                MedtronicPump pump = MedtronicPump.getInstance();
                pump.bolusToDeliver = result.bolusDelivered;
                pump.newBolusAction = true;

                detailedBolusInfo.insulin = t.insulin;
                detailedBolusInfo.date = System.currentTimeMillis();
                TreatmentsPlugin.getPlugin().addToHistoryTreatment(detailedBolusInfo,
                        false);
            } else {
                result.success = false;
                result.bolusDelivered = 0d;
                result.carbsDelivered = 0d;
                result.comment = "deliverTreatment: Invalid input";
                log.error("deliverTreatment: Invalid input");
            }
        }
        return result;
    }

    @Override
    public PumpEnactResult setTempBasalAbsolute(Double absoluteRate, Integer durationInMinutes,
                                                Profile profile, boolean enforceNew) {
        PumpEnactResult result = pumpAction();
        if (result.success) {
            TemporaryBasal tempBasal = new TemporaryBasal()
                    .date(System.currentTimeMillis())
                    .absolute(absoluteRate)
                    .duration(durationInMinutes)
                    .source(Source.USER);
            log.debug("New temp to set.");
            ToastUtils.showToastInUiThread(MainApp.instance().getApplicationContext(),
                    "Setting temp on wake.");
            result.absolute = absoluteRate;
            result.duration = durationInMinutes;
            result.isTempCancel = false;
            result.comment = "Setting temp on next wake. Rate: " + absoluteRate +
                    " Duration: " + durationInMinutes;
            result.enacted = true;
            TreatmentsPlugin.getPlugin().addToHistoryTempBasal(tempBasal);
            MedtronicPump.getInstance().expectingTempUpdate = true;
        }
        return result;
    }

    @Override
    public PumpEnactResult cancelTempBasal(boolean force) {
        PumpEnactResult result = pumpAction();
        if (result.success) {
            log.debug("New temp to cancel.");
            ToastUtils.showToastInUiThread(MainApp.instance().getApplicationContext(),
                    "Canceling temp on wake.");
            result.isTempCancel = true;
            result.comment = "Cancelling temp on next wake.";
            MedtronicPump.getInstance().cancelCurrentTemp = true;
            if (TreatmentsPlugin.getPlugin().isTempBasalInProgress()) {
                log.debug("Temp active on cancel temp, proceeding.");
                result.enacted = true;
                TemporaryBasal tempStop = new TemporaryBasal().date(System.currentTimeMillis()).source(Source.USER);
                TreatmentsPlugin.getPlugin().addToHistoryTempBasal(tempStop);
                MedtronicPump.getInstance().expectingTempUpdate = true;
            } else {
                log.debug("No temp active on cancel temp.");
            }
        }
        return result;
    }

    private PumpEnactResult pumpAction() {
        PumpEnactResult result = new PumpEnactResult();
        if (sMedtronicService != null && sMedtronicService.getRunThread()) {
            result.success = !MedtronicPump.getInstance().fatalError;
            if (!result.success) {
                log.debug("pumpAction on fatal pump error. Cannot update pump.");
                result.comment = "Pump error. Reset pump.";
            }
            return result;
        }
        log.error("Service not running on updateTemp");
        result.comment = "Service not running on updateTemp";
        result.success = false;
        return result;
    }

    @Override
    public boolean isFakingTempsByExtendedBoluses() {
        return MedtronicPump.getInstance().isUsingExtendedBolus;
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
