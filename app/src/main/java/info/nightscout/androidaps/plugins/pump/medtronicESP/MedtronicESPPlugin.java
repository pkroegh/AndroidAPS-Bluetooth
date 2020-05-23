package info.nightscout.androidaps.plugins.pump.medtronicESP;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.IBinder;

import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.R;
import info.nightscout.androidaps.data.DetailedBolusInfo;
import info.nightscout.androidaps.data.Profile;
import info.nightscout.androidaps.data.PumpEnactResult;
import info.nightscout.androidaps.db.Source;
import info.nightscout.androidaps.db.TemporaryBasal;
import info.nightscout.androidaps.events.EventAppExit;
import info.nightscout.androidaps.events.EventPreferenceChange;
import info.nightscout.androidaps.interfaces.PluginType;
import info.nightscout.androidaps.plugins.bus.RxBus;
import info.nightscout.androidaps.plugins.pump.common.defs.PumpType;
import info.nightscout.androidaps.plugins.pump.medtronicESP.services.MedtronicESPService;
import info.nightscout.androidaps.plugins.treatments.Treatment;
import info.nightscout.androidaps.plugins.treatments.TreatmentsPlugin;
import info.nightscout.androidaps.utils.FabricPrivacy;
import info.nightscout.androidaps.utils.SP;
import info.nightscout.androidaps.utils.ToastUtils;
import io.reactivex.disposables.CompositeDisposable;
import io.reactivex.schedulers.Schedulers;

/*
 *   Modified version of VirtualPumpPlugin and DanaRPlugin by mike
 *
 *   Allows for communication with a Medtronic pump, through an ESP32 microcontroller
 *   Created by ldaug99 on 2019-02-17
 */

public class MedtronicESPPlugin extends AbstractMedtronicESPPlugin {
    private static MedtronicESPPlugin plugin = null;

    private CompositeDisposable disposable = new CompositeDisposable();

    public static MedtronicESPPlugin getPlugin() {
        if (plugin == null)
            plugin = new MedtronicESPPlugin();
        return plugin;
    }

    private MedtronicESPPlugin() {
        super();
        pumpDescription.setPumpDescription(PumpType.Medtronic_ESP);
    }

    @Override
    protected void onStart() {
        Context context = MainApp.instance().getApplicationContext();
        Intent intent = new Intent(context, MedtronicESPService.class);
        context.bindService(intent, mConnection, Context.BIND_AUTO_CREATE);
        // On exit
        disposable.add(RxBus.INSTANCE
                .toObservable(EventAppExit.class)
                .observeOn(Schedulers.io())
                .subscribe(event -> {
                    MainApp.instance().getApplicationContext().unbindService(mConnection);
                }, FabricPrivacy::logException)
        );
        super.onStart();
    }

    @Override
    protected void onStop() {
        Context context = MainApp.instance().getApplicationContext();
        context.unbindService(mConnection);
        disposable.clear();
        super.onStop();
    }

    private ServiceConnection mConnection = new ServiceConnection() {
        public void onServiceDisconnected(ComponentName name) {
            log.debug("MedtronicService on disconnect");
            sMedtronicService = null;
        }

        public void onServiceConnected(ComponentName name, IBinder service) {
            log.debug("MedtronicService on connect");
            MedtronicESPService.LocalBinder mLocalBinder = (MedtronicESPService.LocalBinder) service;
            sMedtronicService = mLocalBinder.getServiceInstance();
        }
    };

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

                MedtronicESPPump pump = MedtronicESPPump.getInstance();
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
            MedtronicESPPump.getInstance().expectingTempUpdate = true;
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
            MedtronicESPPump.getInstance().cancelCurrentTemp = true;
            if (TreatmentsPlugin.getPlugin().isTempBasalInProgress()) {
                log.debug("Temp active on cancel temp, proceeding.");
                result.enacted = true;
                TemporaryBasal tempStop = new TemporaryBasal().date(System.currentTimeMillis()).source(Source.USER);
                TreatmentsPlugin.getPlugin().addToHistoryTempBasal(tempStop);
                MedtronicESPPump.getInstance().expectingTempUpdate = true;
            } else {
                log.debug("No temp active on cancel temp.");
            }
        }
        return result;
    }

    private PumpEnactResult pumpAction() {
        PumpEnactResult result = new PumpEnactResult();
        if (sMedtronicService != null && sMedtronicService.getRunThread()) {
            result.success = !MedtronicESPPump.getInstance().fatalError;
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
        return MedtronicESPPump.getInstance().isUsingExtendedBolus;
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
