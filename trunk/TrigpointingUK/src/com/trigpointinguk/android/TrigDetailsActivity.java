package com.trigpointinguk.android;

import android.app.TabActivity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.database.Cursor;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.util.Log;
import android.widget.TabHost;

public class TrigDetailsActivity extends TabActivity {

	private static final String TAG="TrigDetailsActivity";
    private SharedPreferences mPrefs;
	
	public void onCreate(Bundle savedInstanceState) {
	    super.onCreate(savedInstanceState);
	    setContentView(R.layout.trigdetails);

		Bundle extras = getIntent().getExtras();
       mPrefs = PreferenceManager.getDefaultSharedPreferences(this);

	    
	    Resources res = getResources();
	    TabHost tabHost = getTabHost();
	    TabHost.TabSpec spec;
	    Intent intent;
	    

	    intent = new Intent().setClass(this, TrigDetailsInfoTab.class);
	    intent.putExtras(extras);
	    spec = tabHost.newTabSpec("info").setIndicator("",
	                    res.getDrawable(android.R.drawable.ic_menu_info_details))
	                    .setContent(intent);
	    tabHost.addTab(spec);

	    intent = new Intent().setClass(this, TrigDetailsLogsTab.class);
	    intent.putExtras(extras);
	    spec = tabHost.newTabSpec("logs").setIndicator("",
	                      res.getDrawable(android.R.drawable.ic_menu_agenda))
	                  .setContent(intent);
	    tabHost.addTab(spec);

	    intent = new Intent().setClass(this, TrigDetailsAlbumTab.class);
	    intent.putExtras(extras);
	    spec = tabHost.newTabSpec("album").setIndicator("",
	                      res.getDrawable(android.R.drawable.ic_menu_gallery))
	                  .setContent(intent);
	    tabHost.addTab(spec);

	    intent = new Intent().setClass(this, TrigDetailsOSMapTab.class);
	    intent.putExtras(extras);
	    spec = tabHost.newTabSpec("map").setIndicator("",
	                      res.getDrawable(android.R.drawable.ic_menu_mapmode))
	                  .setContent(intent);
	    tabHost.addTab(spec);

	    if (mPrefs.getBoolean("experimental", false)) {
		    intent = new Intent().setClass(this, LogTrigActivity.class);
		    intent.putExtras(extras);
		    spec = tabHost.newTabSpec("mylog").setIndicator("",
		                      res.getDrawable(android.R.drawable.ic_menu_edit))
		                  .setContent(intent);
		    tabHost.addTab(spec);
	    }
	    
	    tabHost.setCurrentTab(0);
	    
	    // Change title
	    // get trig_id from extras
		if (extras == null) {return;}
		Long  trigId = extras.getLong(DbHelper.TRIG_ID);
		Log.i(TAG, "Trig_id = "+trigId);

		// get trig info from database
		DbHelper mDb = new DbHelper(this);
		try {
			mDb.open();		
			Cursor c = mDb.fetchTrigInfo(trigId);
			c.moveToFirst();
				
			String title = String.format("TrigpointingUK - %s" 
					, c.getString(c.getColumnIndex(DbHelper.TRIG_NAME))
			);
			this.setTitle(title);
	        mDb.close();
		} catch (Exception e) {
		} finally {
			mDb.close();
		}
	}
}