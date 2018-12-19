package info.nightscout.androidaps.activities;

import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.support.v7.app.AppCompatActivity;
import android.view.Menu;
import android.view.MenuItem;

import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.R;
import info.nightscout.androidaps.activities.PreferencesActivity;
import info.nightscout.androidaps.interfaces.PluginBase;
import info.nightscout.utils.PasswordProtection;

public class SingleFragmentActivity extends AppCompatActivity {

    private PluginBase plugin;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_single_fragment);

        this.plugin = MainApp.getPluginsList().get(getIntent().getIntExtra("plugin", -1));
        setTitle(plugin.getName());
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        getSupportActionBar().setDisplayShowHomeEnabled(true);

        if (savedInstanceState == null) {
            getSupportFragmentManager().beginTransaction().replace(R.id.frame_layout,
                    Fragment.instantiate(this, plugin.pluginDescription.getFragmentClass())).commit();
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        else if (item.getItemId() == R.id.nav_plugin_preferences) {
            PasswordProtection.QueryPassword(this, R.string.settings_password, "settings_password", () -> {
                Intent i = new Intent(this, PreferencesActivity.class);
                i.putExtra("id", plugin.getPreferencesId());
                startActivity(i);
            }, null);
            return true;
        }
        return false;
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        if (plugin.getPreferencesId() != -1)
            getMenuInflater().inflate(R.menu.menu_single_fragment, menu);
        return super.onCreateOptionsMenu(menu);
    }
}
