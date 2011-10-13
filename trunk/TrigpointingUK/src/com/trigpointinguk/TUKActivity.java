package com.trigpointinguk;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.TextView;

public class TUKActivity extends Activity {

    public static final int DOWNLOAD_MAPS_ID	= Menu.FIRST;
    public static final int DOWNLOAD_TRIGS_ID	= Menu.FIRST + 1;
    public static final int PREFS_ID 			= Menu.FIRST + 2;
    public static final int ABOUT_ID 			= Menu.FIRST + 3;
    public static final String TAG 		="TUKActivity";
    private SharedPreferences mPrefs;
    
 	@Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);
        mPrefs = PreferenceManager.getDefaultSharedPreferences(this);

        TextView user = (TextView) findViewById(R.id.txtUserName);
        user.setText(mPrefs.getString("username", ""));
        
        final Button btnNearest = (Button) findViewById(R.id.btnNearest);
        btnNearest.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View arg0) {
				Intent i = new Intent(TUKActivity.this, TrigList.class);
				startActivity(i);
			}
		});       
        final Button btnSync = (Button) findViewById(R.id.btnSync);
        btnSync.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View arg0) {
				//new UpdateTrigLogsTask().execute();
				new Sync(TUKActivity.this).execute();
			}
		});
        final Button btnMap = (Button) findViewById(R.id.btnMap);
        btnMap.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View arg0) {
				Intent i = new Intent(TUKActivity.this, TrigMap.class);
				startActivity(i);
			}
		});
        
        //autosync
        if (mPrefs.getBoolean("autosync", false)) {
			new Sync(TUKActivity.this).execute();        	
        }   
    }

 	
 	@Override
    public boolean onCreateOptionsMenu(Menu menu) {
        boolean result = super.onCreateOptionsMenu(menu);
		menu.add(0, DOWNLOAD_MAPS_ID, 0, R.string.downMaps);
        menu.add(0, DOWNLOAD_TRIGS_ID, 0, R.string.downTrigs);
        menu.add(0, PREFS_ID, 0, R.string.prefs);
        menu.add(0, ABOUT_ID, 0, R.string.help_about);
        return result;
    }    
    
    
	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		Intent i;
        switch (item.getItemId()) {
        case DOWNLOAD_TRIGS_ID:
            i = new Intent(TUKActivity.this, DownloadTrigs.class);
            startActivity(i);
            return true;
        case DOWNLOAD_MAPS_ID:
            i = new Intent(TUKActivity.this, DownloadMaps.class);
            startActivity(i);
            return true;
        case PREFS_ID:
            i = new Intent(TUKActivity.this, Preferences.class);
            startActivityForResult(i, PREFS_ID);
            return true;
        case ABOUT_ID:
            i = new Intent(TUKActivity.this, HelpPage.class);
            i.putExtra(HelpPage.PAGE, "about.html");
            startActivity(i);
            return true;
        }
		return super.onOptionsItemSelected(item);
	}

	
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent intent) {
    	super.onActivityResult(requestCode, resultCode, intent);
 
    	switch(requestCode) {
    	case PREFS_ID:
    		TextView user = (TextView) findViewById(R.id.txtUserName);
    		user.setText(mPrefs.getString("username", ""));
    	    break;
    	}
    }


}

	
	