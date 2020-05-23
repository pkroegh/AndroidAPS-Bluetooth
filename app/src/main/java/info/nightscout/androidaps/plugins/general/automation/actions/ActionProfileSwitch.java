package info.nightscout.androidaps.plugins.general.automation.actions;

import android.widget.LinearLayout;

import com.google.common.base.Optional;

import org.json.JSONException;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.R;
import info.nightscout.androidaps.data.ProfileStore;
import info.nightscout.androidaps.data.PumpEnactResult;
import info.nightscout.androidaps.interfaces.ProfileInterface;
import info.nightscout.androidaps.logging.L;
import info.nightscout.androidaps.plugins.configBuilder.ConfigBuilderPlugin;
import info.nightscout.androidaps.plugins.configBuilder.ProfileFunctions;
import info.nightscout.androidaps.plugins.general.automation.elements.InputProfileName;
import info.nightscout.androidaps.plugins.general.automation.elements.LabelWithElement;
import info.nightscout.androidaps.plugins.general.automation.elements.LayoutBuilder;
import info.nightscout.androidaps.queue.Callback;
import info.nightscout.androidaps.utils.DateUtil;
import info.nightscout.androidaps.utils.JsonHelper;

public class ActionProfileSwitch extends Action {
    private static Logger log = LoggerFactory.getLogger(L.AUTOMATION);
    InputProfileName inputProfileName;
    String profileName = "";

    public ActionProfileSwitch() {
        // Prevent action if active profile is already active
        // but we don't have a trigger IS_NOT_EQUAL
        // so check is in the doRun()
        ProfileInterface profileInterface =  ConfigBuilderPlugin.getPlugin().getActiveProfileInterface();
        if (profileInterface != null) {
            ProfileStore profileStore = profileInterface.getProfile();
            if (profileStore != null) {
                String name = profileStore.getDefaultProfileName();
                if (name != null) {
                    profileName = name;
                }
            }
        }
        inputProfileName = new InputProfileName(profileName);
    }

    @Override
    public int friendlyName() {
        return R.string.profilename;
    }

    @Override
    public String shortDescription() {
        String returned = MainApp.gs(R.string.changengetoprofilename, inputProfileName.getValue());
        return returned;
    }

    @Override
    public void doAction(Callback callback) {

        String activeProfileName = ProfileFunctions.getInstance().getProfileName();
        //Check for uninitialized profileName
        if ( profileName.equals("")){
            log.error("Selected profile not initialized");
            if (callback != null)
                callback.result(new PumpEnactResult().success(false).comment(R.string.error_field_must_not_be_empty)).run();
            return;
        }
        if ( ProfileFunctions.getInstance().getProfile() == null){
            log.error("ProfileFunctions not initialized");
            if (callback != null)
                callback.result(new PumpEnactResult().success(false).comment(R.string.noprofile)).run();
            return;
        }
        if (profileName.equals(activeProfileName)) {
            if (L.isEnabled(L.AUTOMATION))
                log.debug("Profile is already switched");
            if (callback != null)
                callback.result(new PumpEnactResult().success(true).comment(R.string.alreadyset)).run();
            return;
        }
        ProfileInterface activeProfile = ConfigBuilderPlugin.getPlugin().getActiveProfileInterface();
        if (activeProfile == null) {
            log.error("ProfileInterface not initialized");
            if (callback != null)
                callback.result(new PumpEnactResult().success(false).comment(R.string.noprofile)).run();
            return;
        }
        ProfileStore profileStore = activeProfile.getProfile();
        if (profileStore == null) return;
        if(profileStore.getSpecificProfile(profileName) == null) {
            if (L.isEnabled(L.AUTOMATION))
                log.error("Selected profile does not exist! - "+ profileName);
            if (callback != null)
                callback.result(new PumpEnactResult().success(false).comment(R.string.notexists)).run();
            return;
        }

        ProfileFunctions.doProfileSwitch(profileStore, profileName, 0, 100, 0, DateUtil.now());
        if (callback != null)
            callback.result(new PumpEnactResult().success(true).comment(R.string.ok)).run();
    }

    @Override
    public void generateDialog(LinearLayout root) {
        new LayoutBuilder()
                .add(new LabelWithElement(MainApp.gs(R.string.profilename), "", inputProfileName))
                .build(root);
    }

    @Override
    public boolean hasDialog() {
        return true;
    }

    @Override
    public String toJSON() {
        JSONObject o = new JSONObject();
        try {
            o.put("type", ActionProfileSwitch.class.getName());
            JSONObject data = new JSONObject();
            data.put("profileToSwitchTo", inputProfileName.getValue());
            o.put("data", data);
        } catch (JSONException e) {
            log.error("Unhandled exception", e);
        }
        return o.toString();
    }

    @Override
    public Action fromJSON(String data) {

        try {
            JSONObject d = new JSONObject(data);
            profileName = JsonHelper.safeGetString(d, "profileToSwitchTo");
            inputProfileName.setValue(profileName);
        } catch (JSONException e) {
            log.error("Unhandled exception", e);
        }
        return this;
    }

    @Override
    public Optional<Integer> icon() {
        return Optional.of(R.drawable.icon_actions_profileswitch);
    }

}
