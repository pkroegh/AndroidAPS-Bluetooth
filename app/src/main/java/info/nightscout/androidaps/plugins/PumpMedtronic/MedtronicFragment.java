package info.nightscout.androidaps.plugins.PumpMedtronic;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import com.crashlytics.android.Crashlytics;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.R;
import info.nightscout.androidaps.logging.L;
import info.nightscout.androidaps.plugins.Common.SubscriberFragment;

/*
 *   Created by ldaug99 on 2019-02-17
 */

public class MedtronicFragment extends SubscriberFragment {
    private static Logger log = LoggerFactory.getLogger(L.PUMP);



    TextView basaBasalRateView;
    TextView tempBasalView;
    TextView extendedBolusView;
    TextView batteryView;
    TextView reservoirView;

    Button bBluetoothConnect;

    TextView vPumpName;
    TextView vBluetoothStatus;
    TextView vThreadStatus;



    private Handler loopHandler = new Handler();
    private Runnable refreshLoop = new Runnable() {
        @Override
        public void run() {
            updateGUI();
            loopHandler.postDelayed(refreshLoop, 60 * 1000L);
        }
    };

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        loopHandler.postDelayed(refreshLoop, 60 * 1000L);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        loopHandler.removeCallbacks(refreshLoop);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        try {
            View view = inflater.inflate(R.layout.medtronic_fragment, container, false);




            vPumpName = view.findViewById(R.id.bluetoothpump_client);
            vBluetoothStatus = view.findViewById(R.id.bluetoothpump_bluetoothstatus);
            vThreadStatus = view.findViewById(R.id.bluetoothpump_threadstatus);

            basaBasalRateView = view.findViewById(R.id.virtualpump_basabasalrate);
            tempBasalView = view.findViewById(R.id.virtualpump_tempbasal);
            extendedBolusView = view.findViewById(R.id.virtualpump_extendedbolus);
            batteryView = view.findViewById(R.id.virtualpump_battery);
            reservoirView = view.findViewById(R.id.virtualpump_reservoir);

            //Connection between layout element and layout page (Button elements)
            bBluetoothConnect = view.findViewById(R.id.service_connect);

            //Click listeners
            bBluetoothConnect.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    /*
                    BluetoothPumpPlugin bluetoothPump = BluetoothPumpPlugin.getPlugin();
                    if (bluetoothPump.isConnected()) {
                        Toast.makeText(MainApp.instance().getApplicationContext(), "Already connected", Toast.LENGTH_SHORT).show();
                    } else if (bluetoothPump.isConnecting()) {
                        Toast.makeText(MainApp.instance().getApplicationContext(), "Connecting...", Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(MainApp.instance().getApplicationContext(), "Starting...", Toast.LENGTH_SHORT).show();
                        bluetoothPump.createService();
                        bluetoothPump.connect("default");
                    }
                    */
                }
            });




            return view;
        } catch (Exception e) {
            Crashlytics.logException(e);
        }

        return null;
    }

    /*
    @Subscribe
    public void onStatusEvent(final EventBluetoothPumpUpdateGui ev) {
        updateGUI();
    }
    */

    @Override
    protected void updateGUI() {
        Activity activity = getActivity();
        if (activity != null) {
            activity.runOnUiThread(new Runnable() {
                   @Override
                   public void run() {






                   }
               }
            );
        }
    }
}
