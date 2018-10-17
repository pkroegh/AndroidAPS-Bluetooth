package info.nightscout.androidaps.plugins.PumpBluetoothV2;

import android.app.ActivityManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.IBinder;
import android.os.SystemClock;

import org.json.JSONException;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Date;

import info.nightscout.androidaps.BuildConfig;
import info.nightscout.androidaps.Config;
import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.R;
import info.nightscout.androidaps.data.DetailedBolusInfo;
import info.nightscout.androidaps.data.Profile;
import info.nightscout.androidaps.data.PumpEnactResult;
import info.nightscout.androidaps.db.ExtendedBolus;
import info.nightscout.androidaps.db.Source;
import info.nightscout.androidaps.db.TemporaryBasal;
import info.nightscout.androidaps.interfaces.PluginBase;
import info.nightscout.androidaps.interfaces.PumpDescription;
import info.nightscout.androidaps.interfaces.PumpInterface;
import info.nightscout.androidaps.interfaces.TreatmentsInterface;
import info.nightscout.androidaps.plugins.Overview.events.EventNewNotification;
import info.nightscout.androidaps.plugins.Overview.events.EventOverviewBolusProgress;
import info.nightscout.androidaps.plugins.Overview.notifications.Notification;
import info.nightscout.androidaps.plugins.PumpBluetooth.services.BluetoothService;
import info.nightscout.androidaps.plugins.PumpBluetoothV2.events.EventBluetoothPumpV2UpdateGui;
import info.nightscout.androidaps.plugins.PumpBluetoothV2.services.BluetoothServiceV2;
import info.nightscout.utils.DateUtil;
import info.nightscout.utils.NSUpload;
import info.nightscout.utils.SP;

public class BluetoothPumpPluginV2 implements PluginBase, PumpInterface {
    private static Logger log = LoggerFactory.getLogger(BluetoothPumpPluginV2.class);

    public static Double defaultBasalValue = 0.2d;

    //Service container
    protected BluetoothServiceV2 sExecutionService;

    static Integer batteryPercent = 50;
    static Integer reservoirInUnits = 50;

    private Date lastDataTime = new Date(0);

    private boolean fragmentEnabled = true;
    private boolean fragmentVisible = true;

    private static boolean fromNSAreCommingFakedExtendedBoluses = false;

    private PumpDescription pumpDescription = new PumpDescription();

    private static void loadFakingStatus() {
        fromNSAreCommingFakedExtendedBoluses = SP.getBoolean("fromNSAreCommingFakedExtendedBoluses", false);
    }

    public static void setFakingStatus(boolean newStatus) {
        fromNSAreCommingFakedExtendedBoluses = newStatus;
        SP.putBoolean("fromNSAreCommingFakedExtendedBoluses", fromNSAreCommingFakedExtendedBoluses);
    }

    public static boolean getFakingStatus() {
        return fromNSAreCommingFakedExtendedBoluses;
    }

    private static info.nightscout.androidaps.plugins.PumpBluetoothV2.BluetoothPumpPluginV2 plugin = null;

    public static info.nightscout.androidaps.plugins.PumpBluetoothV2.BluetoothPumpPluginV2 getPlugin() {
        loadFakingStatus();
        if (plugin == null)
            plugin = new info.nightscout.androidaps.plugins.PumpBluetoothV2.BluetoothPumpPluginV2();
        return plugin;
    }

    private BluetoothPumpPluginV2() {
        pumpDescription.isBolusCapable = true;
        pumpDescription.bolusStep = 0.1d;

        pumpDescription.isExtendedBolusCapable = true;
        pumpDescription.extendedBolusStep = 0.05d;
        pumpDescription.extendedBolusDurationStep = 30;
        pumpDescription.extendedBolusMaxDuration = 8 * 60;

        pumpDescription.isTempBasalCapable = true;
        pumpDescription.tempBasalStyle = PumpDescription.PERCENT | PumpDescription.ABSOLUTE;

        pumpDescription.maxTempPercent = 500;
        pumpDescription.tempPercentStep = 10;

        pumpDescription.tempDurationStep = 30;
        pumpDescription.tempMaxDuration = 24 * 60;


        pumpDescription.isSetBasalProfileCapable = true;
        pumpDescription.basalStep = 0.01d;
        pumpDescription.basalMinimumRate = 0.01d;

        pumpDescription.isRefillingCapable = false;
    }

    @Override
    public String getFragmentClass() {
        return BluetoothPumpFragmentV2.class.getName();
    }

    @Override
    public String getName() {
        return MainApp.instance().getString(R.string.bluetoothpumpv2);
    }

    @Override
    public String getNameShort() {
        String name = MainApp.sResources.getString(R.string.bluetoothpump_shortnamev2);
        if (!name.trim().isEmpty()) {
            //only if translation exists
            return name;
        }
        // use long name as fallback
        return getName();
    }

    @Override
    public boolean isEnabled(int type) {
        return type == PUMP && fragmentEnabled;
    }

    @Override
    public boolean isVisibleInTabs(int type) {
        return type == PUMP && fragmentVisible;
    }

    @Override
    public boolean canBeHidden(int type) {
        return true;
    }

    @Override
    public boolean hasFragment() {
        return true;
    }

    @Override
    public boolean showInList(int type) {
        return true;
    }

    @Override
    public void setFragmentEnabled(int type, boolean fragmentEnabled) {
        if (type == PUMP) this.fragmentEnabled = fragmentEnabled;
    }

    @Override
    public void setFragmentVisible(int type, boolean fragmentVisible) {
        if (type == PUMP) this.fragmentVisible = fragmentVisible;
    }

    @Override
    public int getPreferencesId() {
        return R.xml.pref_bluetoothpump;
    }

    @Override
    public int getType() {
        return PluginBase.PUMP;
    }

    @Override
    public boolean isFakingTempsByExtendedBoluses() {
        return (Config.NSCLIENT || Config.G5UPLOADER) && fromNSAreCommingFakedExtendedBoluses;
    }

    @Override
    public boolean isInitialized() {
        return true;
    }

    @Override
    public boolean isSuspended() {
        return false;
    }

    @Override
    public boolean isBusy() {
        return false;
    }

    @Override
    public boolean isConnected() {
        return true;
    }

    @Override
    public boolean isConnecting() {
        return false;
    }

    @Override
    public void connect(String reason) {
        if (sExecutionService != null) {
            sExecutionService.connect();
        }
        if (!Config.NSCLIENT && !Config.G5UPLOADER)
            NSUpload.uploadDeviceStatus();
        lastDataTime = new Date();
    }

    @Override
    public void disconnect(String reason) {
    }

    @Override
    public void stopConnecting() {
    }

    @Override
    public void getPumpStatus() {
        lastDataTime = new Date();
    }

    //Service related functions
    protected boolean isServiceRunning(Class<?> serviceClass) {
        ActivityManager manager = (ActivityManager) MainApp.instance().getApplicationContext().getSystemService(Context.ACTIVITY_SERVICE);
        for (ActivityManager.RunningServiceInfo service : manager.getRunningServices(Integer.MAX_VALUE)) {
            if (serviceClass.getName().equals(service.service.getClassName())) {
                return true;
            }
        }
        return false;
    }

    private ServiceConnection mServiceConnection = new ServiceConnection() {
        public void onServiceDisconnected(ComponentName name) {
            log.debug("Service is disconnected");
            sExecutionService = null;
        }
        public void onServiceConnected(ComponentName name, IBinder service) {
            log.debug("Service is connected");
            BluetoothServiceV2.MyBinder myBinder = (BluetoothServiceV2.MyBinder) service;
            sExecutionService = myBinder.getService();
        }
    };

    //Starts the service and binds it
    public void reviveService() {
        if (!isServiceRunning(BluetoothService.class)) {
            log.debug("Starting service");
            Context context = MainApp.instance().getApplicationContext();
            Intent intent = new Intent(context, BluetoothService.class);
            context.startService(intent);
        } else {
            log.debug("Service already started");
        }
        bindService();
    }

    //Unbinds service from activity
    private void unbindService() {
        if (isServiceRunning(BluetoothService.class)) {
            if (sExecutionService != null) {
                log.debug("Unbinding service...");
                Context context = MainApp.instance().getApplicationContext();
                context.unbindService(mServiceConnection);
            }
        }
    }

    //Binds service
    private void bindService() {
        if (sExecutionService == null) {
            Context context = MainApp.instance().getApplicationContext();
            Intent intent = new Intent(context, BluetoothService.class);
            context.bindService(intent, mServiceConnection, Context.BIND_AUTO_CREATE);
        }
    }

    @Override
    public PumpEnactResult setNewBasalProfile(Profile profile) {
        lastDataTime = new Date();
        // Do nothing here. we are using MainApp.getConfigBuilder().getActiveProfile().getProfile();
        PumpEnactResult result = new PumpEnactResult();
        result.success = true;
        Notification notification = new Notification(Notification.PROFILE_SET_OK, MainApp.sResources.getString(R.string.profile_set_ok), Notification.INFO, 60);
        MainApp.bus().post(new EventNewNotification(notification));
        return result;
    }

    @Override
    public boolean isThisProfileSet(Profile profile) {
        return true;
    }

    @Override
    public Date lastDataTime() {
        return lastDataTime;
    }

    @Override
    public double getBaseBasalRate() {
        Profile profile = MainApp.getConfigBuilder().getProfile();
        if (profile != null)
            return profile.getBasal() != null ? profile.getBasal() : 0d;
        else
            return 0d;
    }

    @Override
    public PumpEnactResult deliverTreatment(DetailedBolusInfo detailedBolusInfo) {
        String message = "deliverTreatment";
        message = message.concat(" insulin: ");
        message = message.concat(Double.toString(detailedBolusInfo.insulin));
        message = message.concat(" carbs: ");
        message = message.concat(Double.toString(detailedBolusInfo.carbs));
        sExecutionService.confirmedMessage("EnactPumpResult|" + message);

        PumpEnactResult result = new PumpEnactResult();
        result.success = true;
        result.bolusDelivered = detailedBolusInfo.insulin;
        result.carbsDelivered = detailedBolusInfo.carbs;
        result.enacted = result.bolusDelivered > 0 || result.carbsDelivered > 0;
        result.comment = MainApp.instance().getString(R.string.virtualpump_resultok);

        Double delivering = 0d;

        while (delivering < detailedBolusInfo.insulin) {
            SystemClock.sleep(200);
            EventOverviewBolusProgress bolusingEvent = EventOverviewBolusProgress.getInstance();
            bolusingEvent.status = String.format(MainApp.sResources.getString(R.string.bolusdelivering), delivering);
            bolusingEvent.percent = Math.min((int) (delivering / detailedBolusInfo.insulin * 100), 100);
            MainApp.bus().post(bolusingEvent);
            delivering += 0.1d;
        }
        SystemClock.sleep(200);
        EventOverviewBolusProgress bolusingEvent = EventOverviewBolusProgress.getInstance();
        bolusingEvent.status = String.format(MainApp.sResources.getString(R.string.bolusdelivered), detailedBolusInfo.insulin);
        bolusingEvent.percent = 100;
        MainApp.bus().post(bolusingEvent);
        SystemClock.sleep(1000);
        if (Config.logPumpComm)
            log.debug("Delivering treatment insulin: " + detailedBolusInfo.insulin + "U carbs: " + detailedBolusInfo.carbs + "g " + result);
        MainApp.bus().post(new EventBluetoothPumpV2UpdateGui());
        lastDataTime = new Date();
        MainApp.getConfigBuilder().addToHistoryTreatment(detailedBolusInfo);
        return result;
    }

    @Override
    public void stopBolusDelivering() {
        String message = "stopBolusDelivering";
        sExecutionService.confirmedMessage("EnactPumpResult|" + message);

    }

    @Override
    public PumpEnactResult setTempBasalAbsolute(Double absoluteRate, Integer durationInMinutes, boolean enforceNew) {
        String message = "setTempBasalAbsolute";
        message = message.concat(" tempBasal: ");
        message = message.concat(Double.toString(absoluteRate));
        message = message.concat(" duration: ");
        message = message.concat(Integer.toString(durationInMinutes));
        sExecutionService.confirmedMessage("EnactPumpResult|" + message);


        TreatmentsInterface treatmentsInterface = MainApp.getConfigBuilder();
        TemporaryBasal tempBasal = new TemporaryBasal();
        tempBasal.date = System.currentTimeMillis();
        tempBasal.isAbsolute = true;
        tempBasal.absoluteRate = absoluteRate;
        tempBasal.durationInMinutes = durationInMinutes;
        tempBasal.source = Source.USER;
        PumpEnactResult result = new PumpEnactResult();
        result.success = true;
        result.enacted = true;
        result.isTempCancel = false;
        result.absolute = absoluteRate;
        result.duration = durationInMinutes;
        result.comment = MainApp.instance().getString(R.string.virtualpump_resultok);
        treatmentsInterface.addToHistoryTempBasal(tempBasal);
        if (Config.logPumpComm)
            log.debug("Setting temp basal absolute: " + result);
        MainApp.bus().post(new EventBluetoothPumpV2UpdateGui());
        lastDataTime = new Date();
        return result;
    }

    @Override
    public PumpEnactResult setTempBasalPercent(Integer percent, Integer durationInMinutes, boolean enforceNew) {
        String message = "setTempBasalPercent";
        message = message.concat(" percentage: ");
        message = message.concat(Integer.toString(percent));
        message = message.concat(" duration: ");
        message = message.concat(Integer.toString(durationInMinutes));
        sExecutionService.confirmedMessage("EnactPumpResult|" + message);

        TreatmentsInterface treatmentsInterface = MainApp.getConfigBuilder();
        PumpEnactResult result = new PumpEnactResult();
        if (MainApp.getConfigBuilder().isTempBasalInProgress()) {
            result = cancelTempBasal(false);
            if (!result.success)
                return result;
        }
        TemporaryBasal tempBasal = new TemporaryBasal();
        tempBasal.date = System.currentTimeMillis();
        tempBasal.isAbsolute = false;
        tempBasal.percentRate = percent;
        tempBasal.durationInMinutes = durationInMinutes;
        tempBasal.source = Source.USER;
        result.success = true;
        result.enacted = true;
        result.percent = percent;
        result.isPercent = true;
        result.isTempCancel = false;
        result.duration = durationInMinutes;
        result.comment = MainApp.instance().getString(R.string.virtualpump_resultok);
        treatmentsInterface.addToHistoryTempBasal(tempBasal);
        if (Config.logPumpComm)
            log.debug("Settings temp basal percent: " + result);
        MainApp.bus().post(new EventBluetoothPumpV2UpdateGui());
        lastDataTime = new Date();
        return result;
    }

    @Override
    public PumpEnactResult setExtendedBolus(Double insulin, Integer durationInMinutes) {
        String message = "setExtendedBolus";
        message = message.concat(" insulin: ");
        message = message.concat(Double.toString(insulin));
        message = message.concat(" duration: ");
        message = message.concat(Integer.toString(durationInMinutes));
        sExecutionService.confirmedMessage("EnactPumpResult|" + message);

        TreatmentsInterface treatmentsInterface = MainApp.getConfigBuilder();
        PumpEnactResult result = cancelExtendedBolus();
        if (!result.success)
            return result;
        ExtendedBolus extendedBolus = new ExtendedBolus();
        extendedBolus.date = System.currentTimeMillis();
        extendedBolus.insulin = insulin;
        extendedBolus.durationInMinutes = durationInMinutes;
        extendedBolus.source = Source.USER;
        result.success = true;
        result.enacted = true;
        result.bolusDelivered = insulin;
        result.isTempCancel = false;
        result.duration = durationInMinutes;
        result.comment = MainApp.instance().getString(R.string.virtualpump_resultok);
        treatmentsInterface.addToHistoryExtendedBolus(extendedBolus);
        if (Config.logPumpComm)
            log.debug("Setting extended bolus: " + result);
        MainApp.bus().post(new EventBluetoothPumpV2UpdateGui());
        lastDataTime = new Date();
        return result;
    }

    @Override
    public PumpEnactResult cancelTempBasal(boolean force) {
        String message = "cancelTempBasal";
        sExecutionService.confirmedMessage("EnactPumpResult|" + message);

        TreatmentsInterface treatmentsInterface = MainApp.getConfigBuilder();
        PumpEnactResult result = new PumpEnactResult();
        result.success = true;
        result.isTempCancel = true;
        result.comment = MainApp.instance().getString(R.string.virtualpump_resultok);
        if (treatmentsInterface.isTempBasalInProgress()) {
            result.enacted = true;
            TemporaryBasal tempStop = new TemporaryBasal(System.currentTimeMillis());
            tempStop.source = Source.USER;
            treatmentsInterface.addToHistoryTempBasal(tempStop);
            //tempBasal = null;
            if (Config.logPumpComm)
                log.debug("Canceling temp basal: " + result);
            MainApp.bus().post(new EventBluetoothPumpV2UpdateGui());
        }
        lastDataTime = new Date();
        return result;
    }

    @Override
    public PumpEnactResult cancelExtendedBolus() {
        String message = "cancelExtendedBolus";
        sExecutionService.confirmedMessage("EnactPumpResult|" + message);

        TreatmentsInterface treatmentsInterface = MainApp.getConfigBuilder();
        PumpEnactResult result = new PumpEnactResult();
        if (treatmentsInterface.isInHistoryExtendedBoluslInProgress()) {
            ExtendedBolus exStop = new ExtendedBolus(System.currentTimeMillis());
            exStop.source = Source.USER;
            treatmentsInterface.addToHistoryExtendedBolus(exStop);
        }
        result.success = true;
        result.enacted = true;
        result.isTempCancel = true;
        result.comment = MainApp.instance().getString(R.string.virtualpump_resultok);
        if (Config.logPumpComm)
            log.debug("Canceling extended basal: " + result);
        MainApp.bus().post(new EventBluetoothPumpV2UpdateGui());
        lastDataTime = new Date();
        return result;
    }

    @Override
    public JSONObject getJSONStatus() {
        if (!SP.getBoolean("virtualpump_uploadstatus", false)) {
            return null;
        }
        JSONObject pump = new JSONObject();
        JSONObject battery = new JSONObject();
        JSONObject status = new JSONObject();
        JSONObject extended = new JSONObject();
        try {
            battery.put("percent", batteryPercent);
            status.put("status", "normal");
            extended.put("Version", BuildConfig.VERSION_NAME + "-" + BuildConfig.BUILDVERSION);
            try {
                extended.put("ActiveProfile", MainApp.getConfigBuilder().getProfileName());
            } catch (Exception e) {
            }
            TemporaryBasal tb = MainApp.getConfigBuilder().getTempBasalFromHistory(System.currentTimeMillis());
            if (tb != null) {
                extended.put("TempBasalAbsoluteRate", tb.tempBasalConvertedToAbsolute(System.currentTimeMillis()));
                extended.put("TempBasalStart", DateUtil.dateAndTimeString(tb.date));
                extended.put("TempBasalRemaining", tb.getPlannedRemainingMinutes());
            }
            ExtendedBolus eb = MainApp.getConfigBuilder().getExtendedBolusFromHistory(System.currentTimeMillis());
            if (eb != null) {
                extended.put("ExtendedBolusAbsoluteRate", eb.absoluteRate());
                extended.put("ExtendedBolusStart", DateUtil.dateAndTimeString(eb.date));
                extended.put("ExtendedBolusRemaining", eb.getPlannedRemainingMinutes());
            }
            status.put("timestamp", DateUtil.toISOString(new Date()));

            pump.put("battery", battery);
            pump.put("status", status);
            pump.put("extended", extended);
            pump.put("reservoir", reservoirInUnits);
            pump.put("clock", DateUtil.toISOString(new Date()));
        } catch (JSONException e) {
            log.error("Unhandled exception", e);
        }
        return pump;
    }

    @Override
    public String deviceID() {
        return "VirtualPump";
    }

    @Override
    public PumpDescription getPumpDescription() {
        return pumpDescription;
    }

    @Override
    public String shortStatus(boolean veryShort) {
        return "Bluetooth Pump V2";
    }

}
