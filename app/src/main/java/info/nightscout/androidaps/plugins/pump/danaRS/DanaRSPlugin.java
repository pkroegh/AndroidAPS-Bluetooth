package info.nightscout.androidaps.plugins.pump.danaRS;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.IBinder;
import android.support.annotation.Nullable;
import android.support.v4.app.FragmentActivity;
import android.support.v7.app.AlertDialog;

import com.squareup.otto.Subscribe;

import org.json.JSONException;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

import info.nightscout.androidaps.BuildConfig;
import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.R;
import info.nightscout.androidaps.data.DetailedBolusInfo;
import info.nightscout.androidaps.data.Profile;
import info.nightscout.androidaps.data.ProfileStore;
import info.nightscout.androidaps.data.PumpEnactResult;
import info.nightscout.androidaps.db.ExtendedBolus;
import info.nightscout.androidaps.db.TemporaryBasal;
import info.nightscout.androidaps.events.EventAppExit;
import info.nightscout.androidaps.interfaces.Constraint;
import info.nightscout.androidaps.interfaces.ConstraintsInterface;
import info.nightscout.androidaps.interfaces.DanaRInterface;
import info.nightscout.androidaps.interfaces.PluginBase;
import info.nightscout.androidaps.interfaces.PluginDescription;
import info.nightscout.androidaps.interfaces.PluginType;
import info.nightscout.androidaps.interfaces.ProfileInterface;
import info.nightscout.androidaps.interfaces.PumpDescription;
import info.nightscout.androidaps.interfaces.PumpInterface;
import info.nightscout.androidaps.logging.L;
import info.nightscout.androidaps.plugins.configBuilder.ConfigBuilderFragment;
import info.nightscout.androidaps.plugins.configBuilder.DetailedBolusInfoStorage;
import info.nightscout.androidaps.plugins.configBuilder.ProfileFunctions;
import info.nightscout.androidaps.plugins.general.actions.defs.CustomAction;
import info.nightscout.androidaps.plugins.general.actions.defs.CustomActionType;
import info.nightscout.androidaps.plugins.general.overview.events.EventDismissNotification;
import info.nightscout.androidaps.plugins.general.overview.events.EventNewNotification;
import info.nightscout.androidaps.plugins.general.overview.notifications.Notification;
import info.nightscout.androidaps.plugins.profile.ns.NSProfilePlugin;
import info.nightscout.androidaps.plugins.pump.common.defs.PumpType;
import info.nightscout.androidaps.plugins.pump.danaR.DanaRFragment;
import info.nightscout.androidaps.plugins.pump.danaR.DanaRPump;
import info.nightscout.androidaps.plugins.pump.danaR.comm.RecordTypes;
import info.nightscout.androidaps.plugins.pump.danaRS.comm.DanaRS_Packet_Bolus_Set_Step_Bolus_Start;
import info.nightscout.androidaps.plugins.pump.danaRS.events.EventDanaRSDeviceChange;
import info.nightscout.androidaps.plugins.pump.danaRS.services.DanaRSService;
import info.nightscout.androidaps.plugins.treatments.Treatment;
import info.nightscout.androidaps.plugins.treatments.TreatmentsPlugin;
import info.nightscout.androidaps.utils.DateUtil;
import info.nightscout.androidaps.utils.DecimalFormatter;
import info.nightscout.androidaps.utils.Round;
import info.nightscout.androidaps.utils.SP;
import info.nightscout.androidaps.utils.T;

/**
 * Created by mike on 03.09.2017.
 */

public class DanaRSPlugin extends PluginBase implements PumpInterface, DanaRInterface, ConstraintsInterface, ProfileInterface {
    private Logger log = LoggerFactory.getLogger(L.PUMP);
    private static DanaRSPlugin plugin = null;

    public static DanaRSPlugin getPlugin() {
        if (plugin == null)
            plugin = new DanaRSPlugin();
        return plugin;
    }

    private static DanaRSService danaRSService;

    private static String mDeviceAddress = "";
    public static String mDeviceName = "";

    public static PumpDescription pumpDescription = new PumpDescription();

    private DanaRSPlugin() {
        super(new PluginDescription()
                .mainType(PluginType.PUMP)
                .fragmentClass(DanaRFragment.class.getName())
                .pluginName(R.string.danarspump)
                .shortName(R.string.danarspump_shortname)
                .preferencesId(R.xml.pref_danars)
                .description(R.string.description_pump_dana_rs)
        );

        pumpDescription.setPumpDescription(PumpType.DanaRS);
    }

    @Override
    public void onStateChange(PluginType type, State oldState, State newState) {
        // if pump profile was enabled need to switch to another too
        if (type == PluginType.PUMP && newState == State.DISABLED && isProfileInterfaceEnabled) {
            setPluginEnabled(PluginType.PROFILE, false);
            NSProfilePlugin.getPlugin().setPluginEnabled(PluginType.PROFILE, true);
            NSProfilePlugin.getPlugin().setFragmentVisible(PluginType.PROFILE, true);
        }
    }

    @Override
    protected void onStart() {
        Context context = MainApp.instance().getApplicationContext();
        Intent intent = new Intent(context, DanaRSService.class);
        context.bindService(intent, mConnection, Context.BIND_AUTO_CREATE);

        MainApp.bus().register(this);
        onStatusEvent(new EventDanaRSDeviceChange()); // load device name
        super.onStart();
    }

    @Override
    protected void onStop() {
        Context context = MainApp.instance().getApplicationContext();
        context.unbindService(mConnection);

        MainApp.bus().unregister(this);
    }

    @Override
    public void switchAllowed(ConfigBuilderFragment.PluginViewHolder.PluginSwitcher pluginSwitcher, FragmentActivity context) {
        boolean allowHardwarePump = SP.getBoolean("allow_hardware_pump", false);
        if (allowHardwarePump || context == null) {
            pluginSwitcher.invoke();
        } else {
            AlertDialog.Builder builder = new AlertDialog.Builder(context);
            builder.setMessage(R.string.allow_hardware_pump_text)
                    .setPositiveButton(R.string.yes, (dialog, id) -> {
                        pluginSwitcher.invoke();
                        SP.putBoolean("allow_hardware_pump", true);
                        if (L.isEnabled(L.PUMP))
                            log.debug("First time HW pump allowed!");
                    })
                    .setNegativeButton(R.string.cancel, (dialog, id) -> {
                        pluginSwitcher.cancel();
                        if (L.isEnabled(L.PUMP))
                            log.debug("User does not allow switching to HW pump!");
                    });
            builder.create().show();
        }
    }

    private ServiceConnection mConnection = new ServiceConnection() {

        public void onServiceDisconnected(ComponentName name) {
            if (L.isEnabled(L.PUMP))
                log.debug("Service is disconnected");
            danaRSService = null;
        }

        public void onServiceConnected(ComponentName name, IBinder service) {
            if (L.isEnabled(L.PUMP))
                log.debug("Service is connected");
            DanaRSService.LocalBinder mLocalBinder = (DanaRSService.LocalBinder) service;
            danaRSService = mLocalBinder.getServiceInstance();
        }
    };

    @SuppressWarnings("UnusedParameters")
    @Subscribe
    public void onStatusEvent(final EventAppExit e) {
        MainApp.instance().getApplicationContext().unbindService(mConnection);
    }

    @Subscribe
    public void onStatusEvent(final EventDanaRSDeviceChange e) {
        mDeviceAddress = SP.getString(R.string.key_danars_address, "");
        mDeviceName = SP.getString(R.string.key_danars_name, "");
    }

    @Override
    public void connect(String from) {
        if (L.isEnabled(L.PUMP))
            log.debug("RS connect from: " + from);
        if (danaRSService != null && !mDeviceAddress.equals("") && !mDeviceName.equals("")) {
            final Object o = new Object();

            danaRSService.connect(from, mDeviceAddress, o);
        }
    }

    @Override
    public boolean isConnected() {
        return danaRSService != null && danaRSService.isConnected();
    }

    @Override
    public boolean isConnecting() {
        return danaRSService != null && danaRSService.isConnecting();
    }

    @Override
    public boolean isHandshakeInProgress() {
        return false;
    }

    @Override
    public void finishHandshaking() {
    }

    @Override
    public void disconnect(String from) {
        if (L.isEnabled(L.PUMP))
            log.debug("RS disconnect from: " + from);
        if (danaRSService != null) danaRSService.disconnect(from);
    }

    @Override
    public void stopConnecting() {
        if (danaRSService != null) danaRSService.stopConnecting();
    }

    @Override
    public void getPumpStatus() {
        if (danaRSService != null) {
            danaRSService.getPumpStatus();
            pumpDescription.basalStep = DanaRPump.getInstance().basalStep;
            pumpDescription.bolusStep = DanaRPump.getInstance().bolusStep;
        }
    }

    // DanaR interface

    @Override
    public PumpEnactResult loadHistory(byte type) {
        return danaRSService.loadHistory(type);
    }

    @Override
    public PumpEnactResult loadEvents() {
        return danaRSService.loadEvents();
    }

    @Override
    public PumpEnactResult setUserOptions() {
        return danaRSService.setUserSettings();
    }

    // Constraints interface

    @Override
    public Constraint<Double> applyBasalConstraints(Constraint<Double> absoluteRate, Profile profile) {
        absoluteRate.setIfSmaller(DanaRPump.getInstance().maxBasal, String.format(MainApp.gs(R.string.limitingbasalratio), DanaRPump.getInstance().maxBasal, MainApp.gs(R.string.pumplimit)), this);
        return absoluteRate;
    }

    @Override
    public Constraint<Integer> applyBasalPercentConstraints(Constraint<Integer> percentRate, Profile profile) {
        percentRate.setIfGreater(0, String.format(MainApp.gs(R.string.limitingpercentrate), 0, MainApp.gs(R.string.itmustbepositivevalue)), this);
        percentRate.setIfSmaller(getPumpDescription().maxTempPercent, String.format(MainApp.gs(R.string.limitingpercentrate), getPumpDescription().maxTempPercent, MainApp.gs(R.string.pumplimit)), this);

        return percentRate;
    }


    @Override
    public Constraint<Double> applyBolusConstraints(Constraint<Double> insulin) {
        insulin.setIfSmaller(DanaRPump.getInstance().maxBolus, String.format(MainApp.gs(R.string.limitingbolus), DanaRPump.getInstance().maxBolus, MainApp.gs(R.string.pumplimit)), this);
        return insulin;
    }

    @Override
    public Constraint<Double> applyExtendedBolusConstraints(Constraint<Double> insulin) {
        return applyBolusConstraints(insulin);
    }

    // Profile interface

    @Nullable
    @Override
    public ProfileStore getProfile() {
        if (DanaRPump.getInstance().lastSettingsRead == 0)
            return null; // no info now
        return DanaRPump.getInstance().createConvertedProfile();
    }

    @Override
    public String getUnits() {
        return DanaRPump.getInstance().getUnits();
    }

    @Override
    public String getProfileName() {
        return DanaRPump.getInstance().createConvertedProfileName();
    }

    // Pump interface

    @Override
    public boolean isInitialized() {
        return DanaRPump.getInstance().lastConnection > 0 && DanaRPump.getInstance().maxBasal > 0;
    }

    @Override
    public boolean isSuspended() {
        return DanaRPump.getInstance().pumpSuspended;
    }

    @Override
    public boolean isBusy() {
        if (danaRSService == null) return false;
        return danaRSService.isConnected() || danaRSService.isConnecting();
    }

    @Override
    public PumpEnactResult setNewBasalProfile(Profile profile) {
        PumpEnactResult result = new PumpEnactResult();

        if (danaRSService == null) {
            log.error("setNewBasalProfile sExecutionService is null");
            result.comment = "setNewBasalProfile sExecutionService is null";
            return result;
        }
        if (!isInitialized()) {
            log.error("setNewBasalProfile not initialized");
            Notification notification = new Notification(Notification.PROFILE_NOT_SET_NOT_INITIALIZED, MainApp.gs(R.string.pumpNotInitializedProfileNotSet), Notification.URGENT);
            MainApp.bus().post(new EventNewNotification(notification));
            result.comment = MainApp.gs(R.string.pumpNotInitializedProfileNotSet);
            return result;
        } else {
            MainApp.bus().post(new EventDismissNotification(Notification.PROFILE_NOT_SET_NOT_INITIALIZED));
        }
        if (!danaRSService.updateBasalsInPump(profile)) {
            Notification notification = new Notification(Notification.FAILED_UDPATE_PROFILE, MainApp.gs(R.string.failedupdatebasalprofile), Notification.URGENT);
            MainApp.bus().post(new EventNewNotification(notification));
            result.comment = MainApp.gs(R.string.failedupdatebasalprofile);
            return result;
        } else {
            MainApp.bus().post(new EventDismissNotification(Notification.PROFILE_NOT_SET_NOT_INITIALIZED));
            MainApp.bus().post(new EventDismissNotification(Notification.FAILED_UDPATE_PROFILE));
            Notification notification = new Notification(Notification.PROFILE_SET_OK, MainApp.gs(R.string.profile_set_ok), Notification.INFO, 60);
            MainApp.bus().post(new EventNewNotification(notification));
            result.success = true;
            result.enacted = true;
            result.comment = "OK";
            return result;
        }
    }

    @Override
    public boolean isThisProfileSet(Profile profile) {
        if (!isInitialized())
            return true; // TODO: not sure what's better. so far TRUE to prevent too many SMS
        DanaRPump pump = DanaRPump.getInstance();
        if (pump.pumpProfiles == null)
            return true; // TODO: not sure what's better. so far TRUE to prevent too many SMS
        int basalValues = pump.basal48Enable ? 48 : 24;
        int basalIncrement = pump.basal48Enable ? 30 * 60 : 60 * 60;
        for (int h = 0; h < basalValues; h++) {
            Double pumpValue = pump.pumpProfiles[pump.activeProfile][h];
            Double profileValue = profile.getBasalTimeFromMidnight((Integer) (h * basalIncrement));
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
    public long lastDataTime() {
        return DanaRPump.getInstance().lastConnection;
    }

    @Override
    public double getBaseBasalRate() {
        return DanaRPump.getInstance().currentBasal;
    }

    @Override
    public double getReservoirLevel() { return DanaRPump.getInstance().reservoirRemainingUnits; }

    @Override
    public int getBatteryLevel() { return DanaRPump.getInstance().batteryRemaining; }

    @Override
    public synchronized PumpEnactResult deliverTreatment(DetailedBolusInfo detailedBolusInfo) {
        detailedBolusInfo.insulin = MainApp.getConstraintChecker().applyBolusConstraints(new Constraint<>(detailedBolusInfo.insulin)).value();
        if (detailedBolusInfo.insulin > 0 || detailedBolusInfo.carbs > 0) {
            int preferencesSpeed = SP.getInt(R.string.key_danars_bolusspeed, 0);
            int speed = 12;
            switch (preferencesSpeed) {
                case 0:
                    speed = 12;
                    break;
                case 1:
                    speed = 30;
                    break;
                case 2:
                    speed = 60;
                    break;
            }
            // RS stores end time for bolus, we need to adjust time
            // default delivery speed is 12 sec/U
            detailedBolusInfo.date = DateUtil.now() + (long) (speed * detailedBolusInfo.insulin * 1000);
            // clean carbs to prevent counting them as twice because they will picked up as another record
            // I don't think it's necessary to copy DetailedBolusInfo right now for carbs records
            double carbs = detailedBolusInfo.carbs;
            detailedBolusInfo.carbs = 0;
            int carbTime = detailedBolusInfo.carbTime;
            if (carbTime == 0) carbTime--; // better set 1 min back to prevents clash with insulin
            detailedBolusInfo.carbTime = 0;

            DetailedBolusInfoStorage.add(detailedBolusInfo); // will be picked up on reading history

            Treatment t = new Treatment();
            t.isSMB = detailedBolusInfo.isSMB;
            boolean connectionOK = false;
            if (detailedBolusInfo.insulin > 0 || carbs > 0)
                connectionOK = danaRSService.bolus(detailedBolusInfo.insulin, (int) carbs, DateUtil.now() + T.mins(carbTime).msecs(), t);
            PumpEnactResult result = new PumpEnactResult();
            result.success = connectionOK && Math.abs(detailedBolusInfo.insulin - t.insulin) < pumpDescription.bolusStep;
            result.bolusDelivered = t.insulin;
            result.carbsDelivered = detailedBolusInfo.carbs;
            if (!result.success) {
                String error = "" + DanaRS_Packet_Bolus_Set_Step_Bolus_Start.errorCode;
                switch (DanaRS_Packet_Bolus_Set_Step_Bolus_Start.errorCode) {
                    // 4 reported as max bolus violation. Check later
                    case 0x10:
                        error = MainApp.gs(R.string.maxbolusviolation);
                        break;
                    case 0x20:
                        error = MainApp.gs(R.string.commanderror);
                        break;
                    case 0x40:
                        error = MainApp.gs(R.string.speederror);
                        break;
                    case 0x80:
                        error = MainApp.gs(R.string.insulinlimitviolation);
                        break;
                }
                result.comment = String.format(MainApp.gs(R.string.boluserrorcode), detailedBolusInfo.insulin, t.insulin, error);
            } else
                result.comment = MainApp.gs(R.string.virtualpump_resultok);
            if (L.isEnabled(L.PUMP))
                log.debug("deliverTreatment: OK. Asked: " + detailedBolusInfo.insulin + " Delivered: " + result.bolusDelivered);
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
    }

    @Override
    public void stopBolusDelivering() {
        if (danaRSService == null) {
            log.error("stopBolusDelivering sExecutionService is null");
            return;
        }
        danaRSService.bolusStop();
    }

    // This is called from APS
    @Override
    public synchronized PumpEnactResult setTempBasalAbsolute(Double absoluteRate, Integer durationInMinutes, Profile profile, boolean enforceNew) {
        // Recheck pump status if older than 30 min

        //This should not be needed while using queue because connection should be done before calling this
        //if (pump.lastConnection.getTime() + 30 * 60 * 1000L < System.currentTimeMillis()) {
        //    connect("setTempBasalAbsolute old data");
        //}

        PumpEnactResult result = new PumpEnactResult();

        absoluteRate = MainApp.getConstraintChecker().applyBasalConstraints(new Constraint<>(absoluteRate), profile).value();

        final boolean doTempOff = getBaseBasalRate() - absoluteRate == 0d;
        final boolean doLowTemp = absoluteRate < getBaseBasalRate();
        final boolean doHighTemp = absoluteRate > getBaseBasalRate();

        if (doTempOff) {
            // If temp in progress
            if (TreatmentsPlugin.getPlugin().isTempBasalInProgress()) {
                if (L.isEnabled(L.PUMP))
                    log.debug("setTempBasalAbsolute: Stopping temp basal (doTempOff)");
                return cancelTempBasal(false);
            }
            result.success = true;
            result.enacted = false;
            result.percent = 100;
            result.isPercent = true;
            result.isTempCancel = true;
            if (L.isEnabled(L.PUMP))
                log.debug("setTempBasalAbsolute: doTempOff OK");
            return result;
        }

        if (doLowTemp || doHighTemp) {
            Integer percentRate = Double.valueOf(absoluteRate / getBaseBasalRate() * 100).intValue();
            if (percentRate < 100) percentRate = Round.ceilTo((double) percentRate, 10d).intValue();
            else percentRate = Round.floorTo((double) percentRate, 10d).intValue();
            if (percentRate > 500) // Special high temp 500/15min
                percentRate = 500;
            // Check if some temp is already in progress
            TemporaryBasal activeTemp = TreatmentsPlugin.getPlugin().getTempBasalFromHistory(System.currentTimeMillis());
            if (activeTemp != null) {
                if (L.isEnabled(L.PUMP))
                    log.debug("setTempBasalAbsolute: currently running: " + activeTemp.toString());
                // Correct basal already set ?
                if (activeTemp.percentRate == percentRate) {
                    if (!enforceNew) {
                        result.success = true;
                        result.percent = percentRate;
                        result.enacted = false;
                        result.duration = activeTemp.getPlannedRemainingMinutes();
                        result.isPercent = true;
                        result.isTempCancel = false;
                        if (L.isEnabled(L.PUMP))
                            log.debug("setTempBasalAbsolute: Correct temp basal already set (doLowTemp || doHighTemp)");
                        return result;
                    }
                }
            }
            // Convert duration from minutes to hours
            if (L.isEnabled(L.PUMP))
                log.debug("setTempBasalAbsolute: Setting temp basal " + percentRate + "% for " + durationInMinutes + " mins (doLowTemp || doHighTemp)");
            if (percentRate == 0 && durationInMinutes > 30) {
                result = setTempBasalPercent(percentRate, durationInMinutes, profile, false);
            } else {
                // use special APS temp basal call ... 100+/15min .... 100-/30min
                result = setHighTempBasalPercent(percentRate);
            }
            if (!result.success) {
                log.error("setTempBasalAbsolute: Failed to set hightemp basal");
                return result;
            }
            if (L.isEnabled(L.PUMP))
                log.debug("setTempBasalAbsolute: hightemp basal set ok");
            return result;
        }
        // We should never end here
        log.error("setTempBasalAbsolute: Internal error");
        result.success = false;
        result.comment = "Internal error";
        return result;
    }

    @Override
    public synchronized PumpEnactResult setTempBasalPercent(Integer percent, Integer durationInMinutes, Profile profile, boolean enforceNew) {
        DanaRPump pump = DanaRPump.getInstance();
        PumpEnactResult result = new PumpEnactResult();
        percent = MainApp.getConstraintChecker().applyBasalPercentConstraints(new Constraint<>(percent), profile).value();
        if (percent < 0) {
            result.isTempCancel = false;
            result.enacted = false;
            result.success = false;
            result.comment = MainApp.gs(R.string.danar_invalidinput);
            log.error("setTempBasalPercent: Invalid input");
            return result;
        }
        if (percent > getPumpDescription().maxTempPercent)
            percent = getPumpDescription().maxTempPercent;
        long now = System.currentTimeMillis();
        TemporaryBasal runningTB = TreatmentsPlugin.getPlugin().getTempBasalFromHistory(now);
        if (runningTB != null && runningTB.percentRate == percent && !enforceNew) {
            result.enacted = false;
            result.success = true;
            result.isTempCancel = false;
            result.comment = MainApp.gs(R.string.virtualpump_resultok);
            result.duration = pump.tempBasalRemainingMin;
            result.percent = pump.tempBasalPercent;
            result.isPercent = true;
            if (L.isEnabled(L.PUMP))
                log.debug("setTempBasalPercent: Correct value already set");
            return result;
        }
        boolean connectionOK;
        if (durationInMinutes == 15 || durationInMinutes == 30) {
            connectionOK = danaRSService.tempBasalShortDuration(percent, durationInMinutes);
        } else {
            int durationInHours = Math.max(durationInMinutes / 60, 1);
            connectionOK = danaRSService.tempBasal(percent, durationInHours);
        }
        if (connectionOK && pump.isTempBasalInProgress && pump.tempBasalPercent == percent) {
            result.enacted = true;
            result.success = true;
            result.comment = MainApp.gs(R.string.virtualpump_resultok);
            result.isTempCancel = false;
            result.duration = pump.tempBasalRemainingMin;
            result.percent = pump.tempBasalPercent;
            result.isPercent = true;
            if (L.isEnabled(L.PUMP))
                log.debug("setTempBasalPercent: OK");
            return result;
        }
        result.enacted = false;
        result.success = false;
        result.comment = MainApp.gs(R.string.tempbasaldeliveryerror);
        log.error("setTempBasalPercent: Failed to set temp basal");
        return result;
    }

    private synchronized PumpEnactResult setHighTempBasalPercent(Integer percent) {
        DanaRPump pump = DanaRPump.getInstance();
        PumpEnactResult result = new PumpEnactResult();
        boolean connectionOK = danaRSService.highTempBasal(percent);
        if (connectionOK && pump.isTempBasalInProgress && pump.tempBasalPercent == percent) {
            result.enacted = true;
            result.success = true;
            result.comment = MainApp.gs(R.string.virtualpump_resultok);
            result.isTempCancel = false;
            result.duration = pump.tempBasalRemainingMin;
            result.percent = pump.tempBasalPercent;
            result.isPercent = true;
            if (L.isEnabled(L.PUMP))
                log.debug("setHighTempBasalPercent: OK");
            return result;
        }
        result.enacted = false;
        result.success = false;
        result.comment = MainApp.gs(R.string.danar_valuenotsetproperly);
        log.error("setHighTempBasalPercent: Failed to set temp basal");
        return result;
    }

    @Override
    public synchronized PumpEnactResult setExtendedBolus(Double insulin, Integer durationInMinutes) {
        DanaRPump pump = DanaRPump.getInstance();
        insulin = MainApp.getConstraintChecker().applyExtendedBolusConstraints(new Constraint<>(insulin)).value();
        // needs to be rounded
        int durationInHalfHours = Math.max(durationInMinutes / 30, 1);
        insulin = Round.roundTo(insulin, getPumpDescription().extendedBolusStep);
        PumpEnactResult result = new PumpEnactResult();
        ExtendedBolus runningEB = TreatmentsPlugin.getPlugin().getExtendedBolusFromHistory(System.currentTimeMillis());
        if (runningEB != null && Math.abs(runningEB.insulin - insulin) < getPumpDescription().extendedBolusStep) {
            result.enacted = false;
            result.success = true;
            result.comment = MainApp.gs(R.string.virtualpump_resultok);
            result.duration = pump.extendedBolusRemainingMinutes;
            result.absolute = pump.extendedBolusAbsoluteRate;
            result.isPercent = false;
            result.isTempCancel = false;
            if (L.isEnabled(L.PUMP))
                log.debug("setExtendedBolus: Correct extended bolus already set. Current: " + pump.extendedBolusAmount + " Asked: " + insulin);
            return result;
        }
        boolean connectionOK = danaRSService.extendedBolus(insulin, durationInHalfHours);
        if (connectionOK && pump.isExtendedInProgress && Math.abs(pump.extendedBolusAbsoluteRate - insulin) < getPumpDescription().extendedBolusStep) {
            result.enacted = true;
            result.success = true;
            result.comment = MainApp.gs(R.string.virtualpump_resultok);
            result.isTempCancel = false;
            result.duration = pump.extendedBolusRemainingMinutes;
            result.absolute = pump.extendedBolusAbsoluteRate;
            result.bolusDelivered = pump.extendedBolusAmount;
            result.isPercent = false;
            if (L.isEnabled(L.PUMP))
                log.debug("setExtendedBolus: OK");
            return result;
        }
        result.enacted = false;
        result.success = false;
        result.comment = MainApp.gs(R.string.danar_valuenotsetproperly);
        log.error("setExtendedBolus: Failed to extended bolus");
        return result;
    }

    @Override
    public synchronized PumpEnactResult cancelTempBasal(boolean force) {
        PumpEnactResult result = new PumpEnactResult();
        TemporaryBasal runningTB = TreatmentsPlugin.getPlugin().getTempBasalFromHistory(System.currentTimeMillis());
        if (runningTB != null) {
            danaRSService.tempBasalStop();
            result.enacted = true;
            result.isTempCancel = true;
        }
        if (!DanaRPump.getInstance().isTempBasalInProgress) {
            result.success = true;
            result.isTempCancel = true;
            result.comment = MainApp.gs(R.string.virtualpump_resultok);
            if (L.isEnabled(L.PUMP))
                log.debug("cancelRealTempBasal: OK");
            return result;
        } else {
            result.success = false;
            result.comment = MainApp.gs(R.string.danar_valuenotsetproperly);
            result.isTempCancel = true;
            log.error("cancelRealTempBasal: Failed to cancel temp basal");
            return result;
        }
    }

    @Override
    public synchronized PumpEnactResult cancelExtendedBolus() {
        PumpEnactResult result = new PumpEnactResult();
        ExtendedBolus runningEB = TreatmentsPlugin.getPlugin().getExtendedBolusFromHistory(System.currentTimeMillis());
        if (runningEB != null) {
            danaRSService.extendedBolusStop();
            result.enacted = true;
            result.isTempCancel = true;
        }
        if (!DanaRPump.getInstance().isExtendedInProgress) {
            result.success = true;
            result.comment = MainApp.gs(R.string.virtualpump_resultok);
            if (L.isEnabled(L.PUMP))
                log.debug("cancelExtendedBolus: OK");
            return result;
        } else {
            result.success = false;
            result.comment = MainApp.gs(R.string.danar_valuenotsetproperly);
            log.error("cancelExtendedBolus: Failed to cancel extended bolus");
            return result;
        }
    }

    @Override
    public JSONObject getJSONStatus(Profile profile, String profileName) {
        DanaRPump pump = DanaRPump.getInstance();
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
            status.put("status", pump.pumpSuspended ? "suspended" : "normal");
            status.put("timestamp", DateUtil.toISOString(pump.lastConnection));
            extended.put("Version", BuildConfig.VERSION_NAME + "-" + BuildConfig.BUILDVERSION);
            extended.put("PumpIOB", pump.iob);
            if (pump.lastBolusTime != 0) {
                extended.put("LastBolus", DateUtil.dateAndTimeFullString(pump.lastBolusTime));
                extended.put("LastBolusAmount", pump.lastBolusAmount);
            }
            TemporaryBasal tb = TreatmentsPlugin.getPlugin().getTempBasalFromHistory(now);
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
                extended.put("ActiveProfile", ProfileFunctions.getInstance().getProfileName());
            } catch (Exception e) {
            }

            pumpjson.put("battery", battery);
            pumpjson.put("status", status);
            pumpjson.put("extended", extended);
            pumpjson.put("reservoir", (int) pump.reservoirRemainingUnits);
            pumpjson.put("clock", DateUtil.toISOString(now));
        } catch (JSONException e) {
            log.error("Unhandled exception", e);
        }
        return pumpjson;
    }

    @Override
    public String deviceID() {
        return DanaRPump.getInstance().serialNumber;
    }

    @Override
    public PumpDescription getPumpDescription() {
        return pumpDescription;
    }

    @Override
    public String shortStatus(boolean veryShort) {
        DanaRPump pump = DanaRPump.getInstance();
        String ret = "";
        if (pump.lastConnection != 0) {
            Long agoMsec = System.currentTimeMillis() - pump.lastConnection;
            int agoMin = (int) (agoMsec / 60d / 1000d);
            ret += "LastConn: " + agoMin + " minago\n";
        }
        if (pump.lastBolusTime != 0) {
            ret += "LastBolus: " + DecimalFormatter.to2Decimal(pump.lastBolusAmount) + "U @" + android.text.format.DateFormat.format("HH:mm", pump.lastBolusTime) + "\n";
        }
        TemporaryBasal activeTemp = TreatmentsPlugin.getPlugin().getRealTempBasalFromHistory(System.currentTimeMillis());
        if (activeTemp != null) {
            ret += "Temp: " + activeTemp.toStringFull() + "\n";
        }
        ExtendedBolus activeExtendedBolus = TreatmentsPlugin.getPlugin().getExtendedBolusFromHistory(System.currentTimeMillis());
        if (activeExtendedBolus != null) {
            ret += "Extended: " + activeExtendedBolus.toString() + "\n";
        }
        if (!veryShort) {
            ret += "TDD: " + DecimalFormatter.to0Decimal(pump.dailyTotalUnits) + " / " + pump.maxDailyTotalUnits + " U\n";
        }
        ret += "IOB: " + pump.iob + "U\n";
        ret += "Reserv: " + DecimalFormatter.to0Decimal(pump.reservoirRemainingUnits) + "U\n";
        ret += "Batt: " + pump.batteryRemaining + "\n";
        return ret;
    }

    @Override
    public boolean isFakingTempsByExtendedBoluses() {
        return false;
    }

    @Override
    public PumpEnactResult loadTDDs() {
        return loadHistory(RecordTypes.RECORD_TYPE_DAILY);
    }

    @Override
    public List<CustomAction> getCustomActions() {
        return null;
    }

    @Override
    public void executeCustomAction(CustomActionType customActionType) {

    }

    @Override
    public boolean canHandleDST() {
        return false;
    }

}
