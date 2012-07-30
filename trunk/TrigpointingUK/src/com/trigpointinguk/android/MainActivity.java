package com.trigpointinguk.android;

import org.acra.ErrorReporter;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import com.trigpointinguk.android.common.ClearCacheTask;
import com.trigpointinguk.android.filter.FilterActivity;
import com.trigpointinguk.android.logging.SyncTask;
import com.trigpointinguk.android.mapping.DownloadMapsActivity;
import com.trigpointinguk.android.mapping.MapActivity;
import com.trigpointinguk.android.nearest.NearestActivity;

public class MainActivity extends Activity {
    public static final String TAG ="MainActivity";
    public static final int NOTRIGS = 1;
	private static final String RUNBEFORE = "RUNBEFORE";
    private SharedPreferences mPrefs;
    
 	@Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // retrieve saved instance state
        Boolean runbefore = false;
        if (savedInstanceState != null) {
        	runbefore = savedInstanceState.getBoolean(RUNBEFORE, false);
        } 
        
        
        setContentView(R.layout.main);
        mPrefs = PreferenceManager.getDefaultSharedPreferences(this);

        // Add user's name to view
        TextView user = (TextView) findViewById(R.id.txtUserName);
        user.setText(mPrefs.getString("username", getResources().getText(R.string.noUser).toString()));
        
        // Add user info to ACRA
        ErrorReporter.getInstance().putCustomData("username", mPrefs.getString("username", ""));
        
        // check for empty trig database
        DbHelper db = new DbHelper(this);
        db.open();
        if (! db.isTrigTablePopulated()) {
        	showDialog(NOTRIGS);
        }
        db.close();
        
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
				new SyncTask(MainActivity.this, null).execute();
			}
		});
        final Button btnCrash = (Button) findViewById(R.id.btnCrash);
        btnCrash.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View arg0) {
				Log.e(TAG, "Deliberate error" + 1/0);
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
        final Button btnSearch = (Button) findViewById(R.id.btnSearch);
        btnSearch.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View arg0) {
	            Intent i = new Intent(MainActivity.this, FilterActivity.class);
	            startActivityForResult(i, R.id.btnSearch);
			}
		});
        
        //autosync
        if (mPrefs.getBoolean("autosync", false) && !runbefore) {
			new SyncTask(MainActivity.this, null).execute();        	
        }   
        
        //Experimental
        if (mPrefs.getBoolean("experimental", false)) {
        	Toast.makeText(this, "Running in experimental mode", Toast.LENGTH_LONG).show();
        } else {
        	// disable crash button
        	btnCrash.setVisibility(View.INVISIBLE);
        }
        // disable search button
        btnSearch.setEnabled(false);
    }

 	
 	@Override
	protected void onSaveInstanceState(Bundle outState) {
 		outState.putBoolean(RUNBEFORE, true);
 		super.onSaveInstanceState(outState);
	}


	@Override
	protected Dialog onCreateDialog(int id) {
 	    Dialog dialog;
 	    switch(id) {
 	    case NOTRIGS:
 	    	AlertDialog.Builder builder = new AlertDialog.Builder(this);
 	    	builder.setMessage(R.string.dlgNoTrigs)
 	    	       .setCancelable(true)
 	    	       .setPositiveButton("Yes", new DialogInterface.OnClickListener() {
 	    	           public void onClick(DialogInterface dialog, int id) {
 	    	        	   dialog.cancel();
 	    	        	   Intent i = new Intent(MainActivity.this, DownloadTrigsActivity.class);
 	    	        	   startActivity(i);
 	    	           }
 	    	       })
 	    	       .setNegativeButton("No", new DialogInterface.OnClickListener() {
 	    	    	   public void onClick(DialogInterface dialog, int id) {
 	    	    		   dialog.cancel();
 	    	    	   }
 	    	       });
 	    	dialog = builder.create();
 	        break;
 	    default:
 	        dialog = null;
 	    }
 	    return dialog;
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
        case R.id.clearcache:
			new ClearCacheTask(MainActivity.this).execute();        	
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
    	
            // Add user details to ACRA
            ErrorReporter.getInstance().putCustomData("username", mPrefs.getString("username", ""));

    		break;
    	}
    }


}

	
	