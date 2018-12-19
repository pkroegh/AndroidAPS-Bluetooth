package info.nightscout.androidaps.plugins.PumpBluetooth;

import android.app.ActivityManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.IBinder;
import android.os.SystemClock;

import com.squareup.otto.Subscribe;

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
import info.nightscout.androidaps.events.EventPreferenceChange;
import info.nightscout.androidaps.interfaces.PluginBase;
import info.nightscout.androidaps.interfaces.PluginDescription;
import info.nightscout.androidaps.interfaces.PluginType;
import info.nightscout.androidaps.interfaces.PumpDescription;
import info.nightscout.androidaps.interfaces.PumpInterface;
import info.nightscout.androidaps.interfaces.TreatmentsInterface;
import info.nightscout.androidaps.logging.L;
import info.nightscout.androidaps.plugins.ConfigBuilder.ProfileFunctions;
import info.nightscout.androidaps.plugins.Overview.events.EventNewNotification;
import info.nightscout.androidaps.plugins.Overview.events.EventOverviewBolusProgress;
import info.nightscout.androidaps.plugins.Overview.notifications.Notification;
import info.nightscout.androidaps.plugins.PumpBluetooth.events.EventBluetoothPumpUpdateGui;
import info.nightscout.androidaps.plugins.PumpBluetooth.services.BluetoothService;
import info.nightscout.androidaps.plugins.PumpCommon.defs.PumpType;
import info.nightscout.androidaps.plugins.PumpVirtual.events.EventVirtualPumpUpdateGui;
import info.nightscout.androidaps.plugins.Treatments.TreatmentsPlugin;
import info.nightscout.utils.DateUtil;
import info.nightscout.androidaps.plugins.NSClientInternal.NSUpload;
import info.nightscout.utils.SP;

public class BluetoothPumpPlugin extends PluginBase implements PumpInterface {
    private static Logger log = LoggerFactory.getLogger(BluetoothPumpPlugin.class);
    protected BluetoothService sExecutionService; //Service pointer
    private static BluetoothPumpPlugin plugin = null;
    private PumpType pumpType = null;
    private PumpDescription pumpDescription = new PumpDescription();
    Integer batteryPercent = 50;
    Integer reservoirInUnits = 50;
    private long lastDataTime = 0;
    private boolean fragmentEnabled = true;
    private boolean fragmentVisible = true;
    private boolean fromNSAreCommingFakedExtendedBoluses = false;
    protected static boolean ignorePump = false;

    public BluetoothPumpPlugin() {
        super(new PluginDescription()
                .mainType(PluginType.PUMP)
                .fragmentClass(BluetoothPumpFragment.class.getName())
                .pluginName(R.string.bluetoothpump)
                .shortName(R.string.bluetoothpump_shortname)
                .preferencesId(R.xml.pref_bluetoothpump)
                .neverVisible(Config.NSCLIENT)
                .description(R.string.description_pump_virtual)
        );
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
        pumpDescription.tempDurationStep15mAllowed = true;
        pumpDescription.tempDurationStep30mAllowed = true;
        pumpDescription.tempMaxDuration = 24 * 60;


        pumpDescription.isSetBasalProfileCapable = true;
        pumpDescription.basalStep = 0.01d;
        pumpDescription.basalMinimumRate = 0.01d;

        pumpDescription.isRefillingCapable = true;

        pumpDescription.storesCarbInfo = false;
        pumpDescription.is30minBasalRatesCapable = true;
    }

    public static BluetoothPumpPlugin getPlugin() {
        if (plugin == null){
            plugin = new BluetoothPumpPlugin();
        }
        plugin.loadFakingStatus();
        return plugin;
    }

    public static void loadIgnoreStatus(){
        ignorePump = SP.getBoolean("bluetoothpump_ignorepump", false);
    }

    private void loadFakingStatus() {
        fromNSAreCommingFakedExtendedBoluses = SP.getBoolean("fromNSAreCommingFakedExtendedBoluses", false);
    }

    public void setFakingStatus(boolean newStatus) {
        fromNSAreCommingFakedExtendedBoluses = newStatus;
        SP.putBoolean("fromNSAreCommingFakedExtendedBoluses", fromNSAreCommingFakedExtendedBoluses);
    }

    public boolean getFakingStatus() {
        return fromNSAreCommingFakedExtendedBoluses;
    }

    @Override
    protected void onStart() {
        super.onStart();
        MainApp.bus().register(this);
        refreshConfiguration();
    }

    @Override
    protected void onStop() {
        MainApp.bus().unregister(this);
    }

    @Subscribe
    public void onStatusEvent(final EventPreferenceChange s) {
        if (s.isChanged(R.string.key_bluetoothpump_type))
            refreshConfiguration();
    }

    @Override
    public boolean isFakingTempsByExtendedBoluses() {
        return (Config.NSCLIENT) && fromNSAreCommingFakedExtendedBoluses;
    }

    @Override
    public PumpEnactResult loadTDDs() {
        //no result, could read DB in the future?
        return new PumpEnactResult();
    }

    @Override
    public boolean isInitialized() {
        if (isServiceRunning(BluetoothService.class) && sExecutionService != null){
            return true;
        } else {
            createService();
            return false;
        }
    }

    @Override
    public boolean isSuspended() {
        return false;
    }

    @Override
    public boolean isBusy() {
        if (ignorePump) {
            if (sExecutionService != null && sExecutionService.isConnected()) {
                disconnect("");
            } else if (sExecutionService != null && sExecutionService.isConnecting()){
                stopConnecting();
            }
            return false;
        } else if (sExecutionService != null){
            return sExecutionService.isConnecting();
        } else {
            createService();
            return true;
        }
    }

    @Override
    public boolean isConnected() {
        if (ignorePump) {
            if (sExecutionService != null && sExecutionService.isConnected()) {
                disconnect("");
            } else if (sExecutionService != null && sExecutionService.isConnecting()){
                stopConnecting();
            }
            return true;
        } else if (sExecutionService != null) {
            return sExecutionService.isConnected();
        } else {
            return false;
        }
    }

    @Override
    public boolean isConnecting() {
        if (ignorePump) {
            if (sExecutionService != null && sExecutionService.isConnected()) {
                disconnect("");
            } else if (sExecutionService != null && sExecutionService.isConnecting()){
                stopConnecting();
            }
            return false;
        } else if (sExecutionService != null) {
            return sExecutionService.isConnecting();
        } else {
            return false;
        }
    }

    @Override
    public boolean isHandshakeInProgress() {
        if (ignorePump) {
            if (sExecutionService != null && sExecutionService.isConnected()) {
                disconnect("");
            } else if (sExecutionService != null && sExecutionService.isConnecting()){
                stopConnecting();
            }
            return false;
        } else if (sExecutionService != null) {
            return sExecutionService.isConnecting();
        } else {
            return false;
        }
    }

    @Override
    public void finishHandshaking() {

    }

    @Override
    public void connect(String reason) {
        if (ignorePump){
            return;
        } else if (sExecutionService != null) {
            sExecutionService.connect();
        } else {
            createService();
            if(sExecutionService != null){
                sExecutionService.connect();
            }
        }
        if (!Config.NSCLIENT)
            NSUpload.uploadDeviceStatus();
        lastDataTime = System.currentTimeMillis();
    }

    @Override
    public void disconnect(String reason) {
        if (sExecutionService != null) {
            sExecutionService.disconnect();
        }
    }

    @Override
    public void stopConnecting() {
        if (sExecutionService != null) {
            sExecutionService.stopConnecting();
        }
    }

    @Override
    public void getPumpStatus() {
        lastDataTime = System.currentTimeMillis();
    }

    @Override
    public PumpEnactResult setNewBasalProfile(Profile profile) {
        lastDataTime = System.currentTimeMillis();
        // Do nothing here. we are using ConfigBuilderPlugin.getPlugin().getActiveProfile().getProfile();
        PumpEnactResult result = new PumpEnactResult();
        result.success = true;
        Notification notification = new Notification(Notification.PROFILE_SET_OK, MainApp.gs(R.string.profile_set_ok), Notification.INFO, 60);
        MainApp.bus().post(new EventNewNotification(notification));
        return result;
    }

    @Override
    public boolean isThisProfileSet(Profile profile) {
        return true;
    }

    @Override
    public long lastDataTime() {
        return lastDataTime;
    }

    @Override
    public double getBaseBasalRate() {
        Profile profile = ProfileFunctions.getInstance().getProfile();
        if (profile != null && ignorePump){
            return profile.getBasal();
        } else if (profile != null && sExecutionService != null && sExecutionService.isConnected()){
            String message = "getBaseBasalRate";
            message = message.concat(" rate: ");
            message = message.concat(Double.toString(profile.getBasal()));
            sExecutionService.confirmedMessage("EnactPumpResult|" + message);
            return profile.getBasal();
        } else {
            return 0d;
        }
    }

    @Override
    public String deviceID() {
        return "BluetoothPump";
    }

    @Override
    public PumpDescription getPumpDescription() {
        return pumpDescription;
    }

    @Override
    public String shortStatus(boolean veryShort) {
        return "Bluetooth Pump ";
    }

    public PumpType getPumpType() {
        return pumpType;
    }

    @Override
    public int getPreferencesId() {
        return R.xml.pref_bluetoothpump;
    }

    @Override
    public PumpEnactResult deliverTreatment(DetailedBolusInfo detailedBolusInfo) {
        if (ignorePump) {
            PumpEnactResult result = new PumpEnactResult();
            result.success = true;
            result.bolusDelivered = detailedBolusInfo.insulin;
            result.carbsDelivered = detailedBolusInfo.carbs;
            result.enacted = result.bolusDelivered > 0 || result.carbsDelivered > 0;
            result.comment = MainApp.instance().getString(R.string.bluetoothpump_faking);
            lastDataTime = System.currentTimeMillis();
            TreatmentsPlugin.getPlugin().addToHistoryTreatment(detailedBolusInfo, false);
            return result;
        } else if (sExecutionService != null && sExecutionService.isConnected()){
            PumpEnactResult result = new PumpEnactResult();
            result.success = true;
            result.bolusDelivered = detailedBolusInfo.insulin;
            result.carbsDelivered = detailedBolusInfo.carbs;
            result.enacted = result.bolusDelivered > 0 || result.carbsDelivered > 0;
            result.comment = MainApp.gs(R.string.virtualpump_resultok);
            lastDataTime = System.currentTimeMillis();
            TreatmentsPlugin.getPlugin().addToHistoryTreatment(detailedBolusInfo, false);

            String message = "deliverTreatment";
            message = message.concat(" insulin: ");
            message = message.concat(Double.toString(detailedBolusInfo.insulin));
            message = message.concat(" carbs: ");
            message = message.concat(Double.toString(detailedBolusInfo.carbs));
            sExecutionService.confirmedMessage("EnactPumpResult|" + message);
            return result;
        } else {
            PumpEnactResult result = new PumpEnactResult();
            result.success = false;
            result.comment = MainApp.gs(R.string.bluetoothpump_failed);
            return result;
        }
    }

    @Override
    public void stopBolusDelivering() {
        if (sExecutionService != null && sExecutionService.isConnected()){
            String message = "stopBolusDelivering";
            sExecutionService.confirmedMessage("EnactPumpResult|" + message);
        } else {
            pumpNotConnected(true);
        }
    }

    @Override
    public PumpEnactResult setTempBasalAbsolute(Double absoluteRate, Integer durationInMinutes, Profile profile, boolean enforceNew) {
        if (ignorePump) {
            TemporaryBasal tempBasal = new TemporaryBasal()
                    .date(System.currentTimeMillis())
                    .absolute(absoluteRate)
                    .duration(durationInMinutes)
                    .source(Source.USER);
            PumpEnactResult result = new PumpEnactResult();
            result.success = true;
            result.enacted = true;
            result.isTempCancel = false;
            result.absolute = absoluteRate;
            result.duration = durationInMinutes;
            result.comment = MainApp.instance().getString(R.string.bluetoothpump_faking);
            TreatmentsPlugin.getPlugin().addToHistoryTempBasal(tempBasal);
            lastDataTime = System.currentTimeMillis();
            MainApp.bus().post(new EventBluetoothPumpUpdateGui());
            return result;
        } else if (sExecutionService != null && sExecutionService.isConnected()){
            TemporaryBasal tempBasal = new TemporaryBasal()
                    .date(System.currentTimeMillis())
                    .absolute(absoluteRate)
                    .duration(durationInMinutes)
                    .source(Source.USER);
            PumpEnactResult result = new PumpEnactResult();
            result.success = true;
            result.enacted = true;
            result.isTempCancel = false;
            result.absolute = absoluteRate;
            result.duration = durationInMinutes;
            result.comment = MainApp.gs(R.string.virtualpump_resultok);
            TreatmentsPlugin.getPlugin().addToHistoryTempBasal(tempBasal);
            lastDataTime = System.currentTimeMillis();

            String message = "setTempBasalAbsolute";
            message = message.concat(" tempBasal: ");
            message = message.concat(Double.toString(absoluteRate));
            message = message.concat(" duration: ");
            message = message.concat(Integer.toString(durationInMinutes));
            sExecutionService.confirmedMessage("EnactPumpResult|" + message);
            MainApp.bus().post(new EventBluetoothPumpUpdateGui());
            return result;
        } else {
            PumpEnactResult result = new PumpEnactResult();
            result.success = false;
            result.comment = MainApp.gs(R.string.bluetoothpump_failed);
            return result;
        }
    }

    @Override
    public PumpEnactResult setTempBasalPercent(Integer percent, Integer durationInMinutes, Profile profile, boolean enforceNew) {
        if (ignorePump) {
            TemporaryBasal tempBasal = new TemporaryBasal()
                    .date(System.currentTimeMillis())
                    .percent(percent)
                    .duration(durationInMinutes)
                    .source(Source.USER);
            PumpEnactResult result = new PumpEnactResult();
            result.success = true;
            result.enacted = true;
            result.percent = percent;
            result.isPercent = true;
            result.isTempCancel = false;
            result.duration = durationInMinutes;
            result.comment = MainApp.instance().getString(R.string.bluetoothpump_faking);
            TreatmentsPlugin.getPlugin().addToHistoryTempBasal(tempBasal);
            lastDataTime = System.currentTimeMillis();
            MainApp.bus().post(new EventBluetoothPumpUpdateGui());
            return result;
        } else if (sExecutionService != null && sExecutionService.isConnected()){
            TemporaryBasal tempBasal = new TemporaryBasal()
                    .date(System.currentTimeMillis())
                    .percent(percent)
                    .duration(durationInMinutes)
                    .source(Source.USER);
            PumpEnactResult result = new PumpEnactResult();
            result.success = true;
            result.enacted = true;
            result.percent = percent;
            result.isPercent = true;
            result.isTempCancel = false;
            result.duration = durationInMinutes;
            result.comment = MainApp.gs(R.string.virtualpump_resultok);
            TreatmentsPlugin.getPlugin().addToHistoryTempBasal(tempBasal);
            lastDataTime = System.currentTimeMillis();

            String message = "setTempBasalPercent";
            message = message.concat(" percentage: ");
            message = message.concat(Integer.toString(percent));
            message = message.concat(" duration: ");
            message = message.concat(Integer.toString(durationInMinutes));
            sExecutionService.confirmedMessage("EnactPumpResult|" + message);
            MainApp.bus().post(new EventBluetoothPumpUpdateGui());
            return result;
        } else {
            PumpEnactResult result = new PumpEnactResult();
            result.success = false;
            result.comment = MainApp.gs(R.string.bluetoothpump_failed);
            return result;
        }
    }

    @Override
    public PumpEnactResult setExtendedBolus(Double insulin, Integer durationInMinutes) {
        PumpEnactResult result = cancelExtendedBolus();
        if (!result.success){
            return result;
        }
        if (ignorePump) {
            ExtendedBolus extendedBolus = new ExtendedBolus()
                    .date(System.currentTimeMillis())
                    .insulin(insulin)
                    .durationInMinutes(durationInMinutes)
                    .source(Source.USER);
            result.success = true;
            result.enacted = true;
            result.bolusDelivered = insulin;
            result.isTempCancel = false;
            result.duration = durationInMinutes;
            result.comment = MainApp.instance().getString(R.string.bluetoothpump_faking);
            TreatmentsPlugin.getPlugin().addToHistoryExtendedBolus(extendedBolus);
            MainApp.bus().post(new EventBluetoothPumpUpdateGui());
            lastDataTime = System.currentTimeMillis();
            return result;
        } else if (sExecutionService != null && sExecutionService.isConnected()){
            ExtendedBolus extendedBolus = new ExtendedBolus()
                    .date(System.currentTimeMillis())
                    .insulin(insulin)
                    .durationInMinutes(durationInMinutes)
                    .source(Source.USER);
            result.success = true;
            result.enacted = true;
            result.bolusDelivered = insulin;
            result.isTempCancel = false;
            result.duration = durationInMinutes;
            result.comment = MainApp.gs(R.string.virtualpump_resultok);
            TreatmentsPlugin.getPlugin().addToHistoryExtendedBolus(extendedBolus);
            lastDataTime = System.currentTimeMillis();

            String message = "setExtendedBolus";
            message = message.concat(" insulin: ");
            message = message.concat(Double.toString(insulin));
            message = message.concat(" duration: ");
            message = message.concat(Integer.toString(durationInMinutes));
            sExecutionService.confirmedMessage("EnactPumpResult|" + message);
            MainApp.bus().post(new EventBluetoothPumpUpdateGui());
            return result;
        } else {
            result.success = false;
            result.comment = MainApp.gs(R.string.bluetoothpump_failed);
            return result;
        }
    }

    @Override
    public PumpEnactResult cancelTempBasal(boolean force) {
        if (ignorePump) {
            PumpEnactResult result = new PumpEnactResult();
            result.success = true;
            result.isTempCancel = true;
            result.comment = MainApp.instance().getString(R.string.bluetoothpump_faking);
            if (TreatmentsPlugin.getPlugin().isTempBasalInProgress()) {
                result.enacted = true;
                TemporaryBasal tempStop = new TemporaryBasal().date(System.currentTimeMillis()).source(Source.USER);
                TreatmentsPlugin.getPlugin().addToHistoryTempBasal(tempStop);
                MainApp.bus().post(new EventBluetoothPumpUpdateGui());
            }
            lastDataTime = System.currentTimeMillis();
            return result;
        } else if (sExecutionService != null && sExecutionService.isConnected()){
            PumpEnactResult result = new PumpEnactResult();
            result.success = true;
            result.isTempCancel = true;
            result.comment = MainApp.gs(R.string.virtualpump_resultok);
            if (TreatmentsPlugin.getPlugin().isTempBasalInProgress()) {
                result.enacted = true;
                TemporaryBasal tempStop = new TemporaryBasal().date(System.currentTimeMillis()).source(Source.USER);
                TreatmentsPlugin.getPlugin().addToHistoryTempBasal(tempStop);
                MainApp.bus().post(new EventBluetoothPumpUpdateGui());
            }
            lastDataTime = System.currentTimeMillis();

            String message = "cancelTempBasal";
            sExecutionService.confirmedMessage("EnactPumpResult|" + message);
            return result;
        } else {
            PumpEnactResult result = new PumpEnactResult();
            result.success = false;
            result.comment = MainApp.gs(R.string.bluetoothpump_failed);
            return result;
        }
    }

    @Override
    public PumpEnactResult cancelExtendedBolus() {
        if (ignorePump) {
            PumpEnactResult result = new PumpEnactResult();
            if (TreatmentsPlugin.getPlugin().isInHistoryExtendedBoluslInProgress()) {
                ExtendedBolus exStop = new ExtendedBolus(System.currentTimeMillis());
                exStop.source = Source.USER;
                TreatmentsPlugin.getPlugin().addToHistoryExtendedBolus(exStop);
            }
            result.success = true;
            result.enacted = true;
            result.isTempCancel = true;
            result.comment = MainApp.instance().getString(R.string.bluetoothpump_faking);
            MainApp.bus().post(new EventBluetoothPumpUpdateGui());
            lastDataTime = System.currentTimeMillis();
            return result;
        } else if (sExecutionService != null && sExecutionService.isConnected()){
            PumpEnactResult result = new PumpEnactResult();
            if (TreatmentsPlugin.getPlugin().isInHistoryExtendedBoluslInProgress()) {
                ExtendedBolus exStop = new ExtendedBolus(System.currentTimeMillis());
                exStop.source = Source.USER;
                TreatmentsPlugin.getPlugin().addToHistoryExtendedBolus(exStop);
            }
            result.success = true;
            result.enacted = true;
            result.isTempCancel = true;
            result.comment = MainApp.gs(R.string.virtualpump_resultok);
            lastDataTime = System.currentTimeMillis();

            String message = "cancelExtendedBolus";
            sExecutionService.confirmedMessage("EnactPumpResult|" + message);
            MainApp.bus().post(new EventBluetoothPumpUpdateGui());
            return result;

        } else {
            PumpEnactResult result = new PumpEnactResult();
            result.success = false;
            result.comment = MainApp.gs(R.string.bluetoothpump_failed);
            return result;
        }
    }

    public void refreshConfiguration() {
        String pumptype = SP.getString(R.string.key_bluetoothpump_type, "Generic AAPS");

        PumpType pumpTypeNew = PumpType.getByDescription(pumptype);

        if (L.isEnabled(L.PUMP))
            log.debug("Pump in configuration: {}, PumpType object: {}", pumptype, pumpTypeNew);

        if (pumpType == pumpTypeNew)
            return;

        if (L.isEnabled(L.PUMP))
            log.debug("New pump configuration found ({}), changing from previous ({})", pumpTypeNew, pumpType);

        pumpDescription.setPumpDescription(pumpTypeNew);

        this.pumpType = pumpTypeNew;

    }

    //Checks if service is running
    private boolean isServiceRunning(Class<?> serviceClass) {
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
            sExecutionService = null;
        }
        public void onServiceConnected(ComponentName name, IBinder service) {
            BluetoothService.ServiceBinder thisBinder = (BluetoothService.ServiceBinder) service;
            sExecutionService = thisBinder.getService();
        }
    };

    //Starts the service and binds it
    protected void createService() {
        if (!isServiceRunning(BluetoothService.class)) {
            Context context = MainApp.instance().getApplicationContext();
            Intent intent = new Intent(context, BluetoothService.class);
            context.startService(intent);
        }
        bindService();
    }

    //Binds service
    private void bindService() {
        if (sExecutionService == null) {
            Context context = MainApp.instance().getApplicationContext();
            Intent intent = new Intent(context, BluetoothService.class);
            context.bindService(intent, mServiceConnection, Context.BIND_AUTO_CREATE);
        }
    }

    //Unbinds service from activity
    private void unbindService() {
        if (isServiceRunning(BluetoothService.class)) {
            if (sExecutionService != null) {
                Context context = MainApp.instance().getApplicationContext();
                context.unbindService(mServiceConnection);
            }
        }
    }

    @Override
    public JSONObject getJSONStatus(Profile profile, String profileName) {
        long now = System.currentTimeMillis();
        if (!SP.getBoolean("bluetoothpump_uploadstatus", false)) {
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
                extended.put("ActiveProfile", profileName);
            } catch (Exception ignored) {
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
            status.put("timestamp", DateUtil.toISOString(now));

            pump.put("battery", battery);
            pump.put("status", status);
            pump.put("extended", extended);
            pump.put("reservoir", reservoirInUnits);
            pump.put("clock", DateUtil.toISOString(now));
        } catch (JSONException e) {
            log.error("Unhandled exception", e);
        }
        return pump;
    }

    public void pumpNotConnected(boolean urgent){
        log.error("Service not running or connected!");
        //Alert user that pump is not connected!
    }


/*

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
    public int getType() {
        return PluginBase.PUMP;
    }

    public boolean isLoopEnabled() {
        return true;
    }

    public boolean isClosedModeEnabled() {
        return true;
    }

    public boolean isAutosensModeEnabled() {
        return true;
    }

    public boolean isAMAModeEnabled() {
        return true;
    }

    public boolean isSMBModeEnabled() {
        return true;
    }
    */

    /*
    @SuppressWarnings("PointlessBooleanExpression")
    @Override
    public Double applyBasalConstraints(Double absoluteRate) {
        double origAbsoluteRate = absoluteRate;
        if (pump != null) {
            if (absoluteRate > pump.maxBasal) {
                absoluteRate = pump.maxBasal;
                if (Config.logConstraintsChanges && origAbsoluteRate != Constants.basalAbsoluteOnlyForCheckLimit)
                    log.debug("Limiting rate " + origAbsoluteRate + "U/h by pump constraint to " + absoluteRate + "U/h");
            }
        }
        return absoluteRate;
    }

    @SuppressWarnings("PointlessBooleanExpression")
    @Override
    public Integer applyBasalConstraints(Integer percentRate) {
        Integer origPercentRate = percentRate;
        if (percentRate < 0) percentRate = 0;
        if (percentRate > getPumpDescription().maxTempPercent)
            percentRate = getPumpDescription().maxTempPercent;
        if (!Objects.equals(percentRate, origPercentRate) && Config.logConstraintsChanges && !Objects.equals(origPercentRate, Constants.basalPercentOnlyForCheckLimit))
            log.debug("Limiting percent rate " + origPercentRate + "% to " + percentRate + "%");
        return percentRate;
    }

    @SuppressWarnings("PointlessBooleanExpression")
    @Override
    public Double applyBolusConstraints(Double insulin) {
        double origInsulin = insulin;
        if (pump != null) {
            if (insulin > pump.maxBolus) {
                insulin = pump.maxBolus;
                if (Config.logConstraintsChanges && origInsulin != Constants.bolusOnlyForCheckLimit)
                    log.debug("Limiting bolus " + origInsulin + "U by pump constraint to " + insulin + "U");
            }
        }
        return insulin;
    }

    @Override
    public Integer applyCarbsConstraints(Integer carbs) {
        return carbs;
    }

    @Override
    public Double applyMaxIOBConstraints(Double maxIob) {
        return maxIob;
    }

    @Nullable
    @Override
    public ProfileStore getProfile() {
        if (pump.lastSettingsRead == 0)
            return null; // no info now
        return pump.createConvertedProfile();
    }

    @Override
    public String getUnits() {
        return pump.getUnits();
    }

    @Override
    public String getProfileName() {
        return pump.createConvertedProfileName();
    }
    */
}
