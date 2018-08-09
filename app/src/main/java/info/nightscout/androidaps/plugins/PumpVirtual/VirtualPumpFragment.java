package info.nightscout.androidaps.plugins.PumpVirtual;


import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.support.annotation.Nullable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.crashlytics.android.Crashlytics;
import com.squareup.otto.Subscribe;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import butterknife.ButterKnife;
import butterknife.OnClick;
import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.R;
import info.nightscout.androidaps.VirtualBluetoothActivity;
import info.nightscout.androidaps.plugins.Common.SubscriberFragment;
import info.nightscout.androidaps.plugins.PumpVirtual.events.EventVirtualPumpUpdateGui;

public class VirtualPumpFragment extends SubscriberFragment {
    private static Logger log = LoggerFactory.getLogger(VirtualPumpFragment.class);

    TextView basaBasalRateView;
    TextView tempBasalView;
    TextView extendedBolusView;
    TextView batteryView;
    TextView reservoirView;

    private static Handler sLoopHandler = new Handler();
    private static Runnable sRefreshLoop = null;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (sRefreshLoop == null) {
            sRefreshLoop = new Runnable() {
                @Override
                public void run() {
                    updateGUI();
                    sLoopHandler.postDelayed(sRefreshLoop, 60 * 1000L);
                }
            };
            sLoopHandler.postDelayed(sRefreshLoop, 60 * 1000L);
        }
    }


    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        try {
            View view = inflater.inflate(R.layout.virtualpump_fragment, container, false);
            basaBasalRateView = view.findViewById(R.id.virtualpump_basabasalrate);
            tempBasalView = view.findViewById(R.id.virtualpump_tempbasal);
            extendedBolusView = view.findViewById(R.id.virtualpump_extendedbolus);
            batteryView = view.findViewById(R.id.virtualpump_battery);
            reservoirView = view.findViewById(R.id.virtualpump_reservoir);

            /**pkt har indsat dette
             */
            unbinder = ButterKnife.bind(this, view);

            return view;
        } catch (Exception e) {
            Crashlytics.logException(e);
        }

        return null;
    }

    @Subscribe
    public void onStatusEvent(final EventVirtualPumpUpdateGui ev) {
        updateGUI();
    }

    /**pkt har indsat dette
    */

    @OnClick(R.id.virtualpump_simpleble) void onSimpleBleClick() {
        startActivity(new Intent(getContext(), VirtualBluetoothActivity.class));
    }

    @Override
    protected void updateGUI() {
        Activity activity = getActivity();
        if (activity != null && basaBasalRateView != null)
            activity.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    VirtualPumpPlugin virtualPump = VirtualPumpPlugin.getPlugin();
                    basaBasalRateView.setText(virtualPump.getBaseBasalRate() + "U");
                    if (MainApp.getConfigBuilder().isTempBasalInProgress()) {
                        tempBasalView.setText(MainApp.getConfigBuilder().getTempBasalFromHistory(System.currentTimeMillis()).toStringFull());
                    } else {
                        tempBasalView.setText("");
                    }
                    if (MainApp.getConfigBuilder().isInHistoryExtendedBoluslInProgress()) {
                        extendedBolusView.setText(MainApp.getConfigBuilder().getExtendedBolusFromHistory(System.currentTimeMillis()).toString());
                    } else {
                        extendedBolusView.setText("");
                    }
                    batteryView.setText(VirtualPumpPlugin.batteryPercent + "%");
                    reservoirView.setText(VirtualPumpPlugin.reservoirInUnits + "U");
                }
            });
    }
}

