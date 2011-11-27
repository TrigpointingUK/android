package com.trigpointinguk.android;

import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.os.Bundle;
import android.preference.PreferenceActivity;
import android.preference.PreferenceManager;
import android.util.Log;

public class PreferencesActivity extends PreferenceActivity {
    public static final String PREFS_NAME = "TUKPrefsFile";
    public static final String TAG = "PreferenceActivity";

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		addPreferencesFromResource(R.xml.preferences); 
	}

	@Override
	protected void onPause() {
		Log.i(TAG, "onPause");
		super.onPause();

		SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
		
		String plaintext = prefs.getString("password", "");
		if (!plaintext.equals("")) {
			Editor editor = prefs.edit();
			editor.putString("plaintextpassword", plaintext);
			editor.remove("password");
			editor.commit();
		}
	}
	
	
}
