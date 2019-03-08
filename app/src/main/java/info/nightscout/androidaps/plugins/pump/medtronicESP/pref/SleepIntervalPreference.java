package info.nightscout.androidaps.plugins.pump.medtronicESP.pref;

import android.content.Context;
import android.preference.ListPreference;
import android.util.AttributeSet;

import java.util.Vector;

import info.nightscout.androidaps.R;

/*
 *   ESP Sleep interval selector
 *   Created by ldaug99 on 2019-03-08
 */

public class SleepIntervalPreference extends ListPreference {

    public SleepIntervalPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
        Vector<CharSequence> entries = new Vector<CharSequence>();
        for (int i = R.integer.ESP_min_sleep_interval; !(i > R.integer.ESP_max_sleep_interval); i++) {
            entries.add(String.valueOf(i));
        }
        setEntries(entries.toArray(new CharSequence[0]));
        setEntryValues(entries.toArray(new CharSequence[0]));
    }

    public SleepIntervalPreference(Context context) {
        this(context, null);
    }
}