package com.trigpointinguk.android;

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

public class MainActivity extends Activity {
    public static final String TAG 				="MainActivity";
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
				Intent i = new Intent(MainActivity.this, NearestActivity.class);
				startActivity(i);
			}
		});       
        final Button btnSync = (Button) findViewById(R.id.btnSync);
        btnSync.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View arg0) {
				//new UpdateTrigLogsTask().execute();
				new SyncTask(MainActivity.this).execute();
			}
		});
        final Button btnMap = (Button) findViewById(R.id.btnMap);
        btnMap.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View arg0) {
				Intent i = new Intent(MainActivity.this, MapActivity.class);
				startActivity(i);
			}
		});
        
        //autosync
        if (mPrefs.getBoolean("autosync", false)) {
			new SyncTask(MainActivity.this).execute();        	
        }   
    }

 	
 	@Override
    public boolean onCreateOptionsMenu(Menu menu) {
        boolean result = super.onCreateOptionsMenu(menu);
		getMenuInflater().inflate(R.menu.mainmenu, menu);
        return result;
    }    
    
    
	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		Intent i;
        switch (item.getItemId()) {
        
        case R.id.downloadtrigs:
            i = new Intent(MainActivity.this, DownloadTrigsActivity.class);
            startActivity(i);
            return true;
        case R.id.downloadmaps:
            i = new Intent(MainActivity.this, DownloadMapsActivity.class);
            startActivity(i);
            return true;
        case R.id.prefs:
            i = new Intent(MainActivity.this, PreferencesActivity.class);
            startActivityForResult(i, R.id.prefs);
            return true;
        case R.id.about:
            i = new Intent(MainActivity.this, HelpPageActivity.class);
            i.putExtra(HelpPageActivity.PAGE, "about.html");
            startActivity(i);
            return true;
        }
		return super.onOptionsItemSelected(item);
	}

	
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent intent) {
    	super.onActivityResult(requestCode, resultCode, intent);
 
    	switch(requestCode) {
    	case R.id.prefs:
    		TextView user = (TextView) findViewById(R.id.txtUserName);
    		user.setText(mPrefs.getString("username", ""));
    	    break;
    	}
    }


}

	
	