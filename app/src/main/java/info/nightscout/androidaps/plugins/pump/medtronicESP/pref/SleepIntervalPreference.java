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

public class SleepIntervalPreference extends ListPreference { // TODO delete this and find another solution

    public SleepIntervalPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
        Vector<CharSequence> entries = new Vector<CharSequence>();
        for (int i = 0; i <= (getContext().getResources().getInteger(R.integer.ESP_max_sleep_interval) - getContext().getResources().getInteger(R.integer.ESP_min_sleep_interval)); i++) {
            entries.add(String.valueOf(i + getContext().getResources().getInteger(R.integer.ESP_min_sleep_interval)));
        }
        setEntries(entries.toArray(new CharSequence[0]));
        setEntryValues(entries.toArray(new CharSequence[0]));
    }

    public SleepIntervalPreference(Context context) {
        this(context, null);
    }
}