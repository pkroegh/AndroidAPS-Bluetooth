package info.nightscout.androidaps.plugins.PumpMedtronic;

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
import info.nightscout.androidaps.db.ExtendedBolus;
import info.nightscout.androidaps.db.Source;
import info.nightscout.androidaps.db.TemporaryBasal;
import info.nightscout.androidaps.events.EventAppExit;
import info.nightscout.androidaps.events.EventPreferenceChange;
import info.nightscout.androidaps.interfaces.Constraint;
import info.nightscout.androidaps.interfaces.PluginType;
import info.nightscout.androidaps.logging.L;
import info.nightscout.androidaps.plugins.PumpCommon.defs.PumpType;
import info.nightscout.androidaps.plugins.PumpDanaR.DanaRPlugin;
import info.nightscout.androidaps.plugins.PumpDanaR.DanaRPump;
import info.nightscout.androidaps.plugins.PumpDanaR.comm.MsgBolusStartWithSpeed;
import info.nightscout.androidaps.plugins.PumpDanaR.services.DanaRExecutionService;
import info.nightscout.androidaps.plugins.PumpMedtronic.services.MedtronicService;
import info.nightscout.androidaps.plugins.Treatments.Treatment;
import info.nightscout.androidaps.plugins.Treatments.TreatmentsPlugin;
import info.nightscout.utils.Round;
import info.nightscout.utils.SP;

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
            log.debug("Service is disconnected");
            sMedtronicService = null;
        }

        public void onServiceConnected(ComponentName name, IBinder service) {
            log.debug("Service is connected");
            MedtronicService.LocalBinder mLocalBinder = (MedtronicService.LocalBinder) service;
            sMedtronicService = mLocalBinder.getServiceInstance();
        }
    };

    @SuppressWarnings("UnusedParameters")
    @Subscribe
    public void onStatusEvent(final EventAppExit e) {
        MainApp.instance().getApplicationContext().unbindService(mConnection);
    }

    @Override
    public String getName() {
        return MainApp.gs(R.string.medtronicESP);
    }

    @Override
    public int getPreferencesId() {
        return R.xml.pref_medtronicpump;
    }

    @Override
    public boolean isInitialized() {
        return sMedtronicService != null;
    }

    @Override
    public boolean isHandshakeInProgress() {
        return false;
    }

    @Override
    public void finishHandshaking() {

    }

    @Override
    public PumpEnactResult setTempBasalAbsolute(Double absoluteRate, Integer durationInMinutes, Profile profile, boolean enforceNew) {
        if (sMedtronicService != null && sMedtronicService.isBTConnected()){
            MedtronicPump pump = MedtronicPump.getInstance();
            PumpEnactResult result = new PumpEnactResult();
            pump.isTempBasalInProgress = true;
            result.success = true;
            result.absolute = pump.extendedBolusAbsoluteRate;
            result.duration = pump.extendedBolusRemainingMinutes;
            result.isPercent = false;
            result.isTempCancel = false;
            if (TreatmentsPlugin.getPlugin().isTempBasalInProgress()) {
                result.enacted = true;
                TemporaryBasal tempStop = new TemporaryBasal().date(System.currentTimeMillis()).source(Source.USER);
                TreatmentsPlugin.getPlugin().addToHistoryTempBasal(tempStop);
            }
            String message = "T=null";
            sMedtronicService.queueMessage(message);
            return result;
        } else {
            PumpEnactResult result = new PumpEnactResult();
            result.success = false;
            result.comment = MainApp.gs(R.string.medtronicESP_result_failed);
            return result;
        }
    }

    @Override
    public PumpEnactResult cancelTempBasal(boolean force) {
        if (sMedtronicService != null && sMedtronicService.isBTConnected()){
            PumpEnactResult result = new PumpEnactResult();
            result.success = true;
            result.isTempCancel = true;
            result.comment = MainApp.gs(R.string.virtualpump_resultok);
            if (TreatmentsPlugin.getPlugin().isTempBasalInProgress()) {
                result.enacted = true;
                TemporaryBasal tempStop = new TemporaryBasal().date(System.currentTimeMillis()).source(Source.USER);
                TreatmentsPlugin.getPlugin().addToHistoryTempBasal(tempStop);
            }
            String message = "T=null";
            sMedtronicService.queueMessage(message);
            return result;
        } else {
            PumpEnactResult result = new PumpEnactResult();
            result.success = false;
            result.comment = MainApp.gs(R.string.medtronicESP_result_failed);
            return result;
        }
    }
}
