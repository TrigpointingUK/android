package uk.trigpointing.android;

import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.os.Bundle;
import androidx.preference.EditTextPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceManager;
import android.util.Log;
import android.view.MenuItem;
import androidx.preference.PreferenceFragmentCompat;
import uk.trigpointing.android.common.BaseActivity;

public class SettingsActivity extends BaseActivity {
    public static final String TAG = "PreferenceActivity";
    public static final String PREFERENCETYPE = "PreferenceType";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.preferences_activity);
        
        // Enable back button in action bar
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }
        
        // Ensure proper content positioning to prevent action bar overlap
        // Content positioning is now handled by BaseActivity
        
        Bundle extras = getIntent().getExtras();
        if (extras != null) {
            extras.getInt(PREFERENCETYPE);
        }
        
        // Load the appropriate preference fragment
        if (savedInstanceState == null) {
            getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.preferences_container, new PreferencesFragment())
                .commit();
        }
    }

    @Override
    protected void onPause() {
        Log.i(TAG, "onPause");
        super.onPause();
    }
    
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            // Handle back button in action bar
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
    
    /**
     * Preference Fragment to handle the preferences
     */
    public static class PreferencesFragment extends PreferenceFragmentCompat {
        @Override
        public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
            setPreferencesFromResource(R.xml.preferences, rootKey);

            // Mask the OS API key value
            EditTextPreference osApiKeyPreference = findPreference("os_api_key");
            if (osApiKeyPreference != null) {
                osApiKeyPreference.setSummaryProvider(preference -> {
                    String apiKey = ((EditTextPreference) preference).getText();
                    if (apiKey != null && !apiKey.isEmpty()) {
                        return "********";
                    } else {
                        return "Not set";
                    }
                });

                // Do not display the key in the edit dialog
                osApiKeyPreference.setOnBindEditTextListener(editText -> {
                    editText.setText("");
                    editText.setHint("Enter new API key to change it");
                });
            }
        }
    }
    
    
}
