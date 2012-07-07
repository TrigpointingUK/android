package com.trigpointinguk.android;

import org.acra.ACRA;
import org.acra.annotation.ReportsCrashes;

import android.app.Application;

@ReportsCrashes(formKey = "dGlNWUtJM2Jaa0k5NjBsV1VJUm91THc6MQ") 
public class MainApplication extends Application {
	@Override
    public void onCreate() {
        // The following line triggers the initialisation of ACRA
        ACRA.init(this);
        super.onCreate();
    }
}
