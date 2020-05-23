package info.nightscout.androidaps.plugins.pump.common.utils;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.location.LocationManager;

import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.R;
import info.nightscout.androidaps.utils.OKDialog;

/**
 * Helper for checking if location services are enabled on the device.
 */
public class LocationHelper {

    /**
     * Determine if GPS is currently enabled.
     * <p>
     * On Android 6 (Marshmallow), location needs to be enabled for Bluetooth discovery to work.
     *
     * @param context The current app context.
     * @return true if location is enabled, false otherwise.
     */
    public static boolean isLocationEnabled(Context context) {
        LocationManager locationManager = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);

        return (locationManager != null && //
                (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) || //
                        locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)));

        // return locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER);
    }


    /**
     * Prompt the user to enable GPS location if it isn't already on.
     *
     * @param parent The currently visible activity.
     */
    public static void requestLocation(final Activity parent) {
        if (LocationHelper.isLocationEnabled(parent)) {
            return;
        }

        // Shamelessly borrowed from http://stackoverflow.com/a/10311877/868533
        OKDialog.showConfirmation(parent, MainApp.gs(R.string.location_not_found_title), MainApp.gs(R.string.location_not_found_message), () -> {
            parent.startActivity(new Intent(android.provider.Settings.ACTION_LOCATION_SOURCE_SETTINGS));
        });
    }


    /**
     * Prompt the user to enable GPS location on devices that need it for Bluetooth discovery.
     * <p>
     * Android 6 (Marshmallow) needs GPS enabled for Bluetooth discovery to work.
     *
     * @param activity The currently visible activity.
     */
    public static void requestLocationForBluetooth(Activity activity) {
        // Location needs to be enabled for Bluetooth discovery on Marshmallow.
        LocationHelper.requestLocation(activity);
    }

    // public static Boolean locationPermission(ActivityWithMenu act) {
    // return ActivityCompat.checkSelfPermission(act, Manifest.permission.ACCESS_FINE_LOCATION) ==
    // PackageManager.PERMISSION_GRANTED;
    // }

}
