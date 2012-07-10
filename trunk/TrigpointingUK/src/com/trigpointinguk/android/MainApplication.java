package com.trigpointinguk.android;

import org.acra.ACRA;
import org.acra.annotation.ReportsCrashes;

import android.app.Application;
import android.content.pm.ApplicationInfo;
import android.util.Log;


@ReportsCrashes(formKey = "dGlNWUtJM2Jaa0k5NjBsV1VJUm91THc6MQ") 
public class MainApplication extends Application {
	private static final String TAG = "MainApplication";

	@Override
    public void onCreate() {
        // The following line triggers the initialisation of ACRA
		if (0 == (getApplicationInfo().flags &= ApplicationInfo.FLAG_DEBUGGABLE)) {
			ACRA.init(this);
			Log.i(TAG, "ACRA enabled");
		}
        super.onCreate();
    }
}
