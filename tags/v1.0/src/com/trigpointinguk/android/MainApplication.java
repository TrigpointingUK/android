package com.trigpointinguk.android;

import org.acra.ACRA;
import org.acra.annotation.ReportsCrashes;

import android.app.Application;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.preference.PreferenceManager;
import android.util.Log;


@ReportsCrashes(formKey = "dGlNWUtJM2Jaa0k5NjBsV1VJUm91THc6MQ") 
public class MainApplication extends Application {
	private static final String TAG = "MainApplication";

	@Override
    public void onCreate() {
        // This has been removed from the permissions and the preferences, so set to false for any legacy installations
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
		Editor editor = prefs.edit();
		editor.putBoolean("acra.syslog.enable", false);
		editor.commit();  
        
        // The following line disables ACRA when coding
		//if (0 == (getApplicationInfo().flags &= ApplicationInfo.FLAG_DEBUGGABLE)) {
			ACRA.init(this);
			Log.i(TAG, "ACRA enabled");
		//}
        super.onCreate();
    }
}
