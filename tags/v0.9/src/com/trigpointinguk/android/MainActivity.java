package com.trigpointinguk.android;

import org.acra.ErrorReporter;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.trigpointinguk.android.common.ClearCacheTask;
import com.trigpointinguk.android.filter.FilterActivity;
import com.trigpointinguk.android.logging.SyncListener;
import com.trigpointinguk.android.logging.SyncTask;
import com.trigpointinguk.android.mapping.DownloadMapsActivity;
import com.trigpointinguk.android.mapping.MapActivity;
import com.trigpointinguk.android.nearest.NearestActivity;

public class MainActivity extends Activity implements SyncListener {
    public static final String 	TAG ="MainActivity";
    public static final int 	NOTRIGS = 1;
	private static final String RUNBEFORE = "RUNBEFORE";
    private SharedPreferences 	mPrefs;
    private ImageView			mPillarIcon;
    private ImageView			mFbmIcon;
    private ImageView			mPassiveIcon;
    private ImageView			mIntersectedIcon;
    private ImageView			mUnsyncedIcon;
    private ImageView			mPhotosIcon;
    private TextView			mPillarCount;
    private TextView			mFbmCount;
    private TextView			mPassiveCount;
    private TextView			mIntersectedCount;
    private TextView			mUnsyncedCount;
    private TextView			mPhotosCount;
    private Button				mSyncBtn;
    
    
 	@Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.i(TAG, "onCreate");

        // retrieve saved instance state
        Boolean runbefore = false;
        if (savedInstanceState != null) {
        	runbefore = savedInstanceState.getBoolean(RUNBEFORE, false);
        } 
        
        
        setContentView(R.layout.main);
        mPrefs = PreferenceManager.getDefaultSharedPreferences(this);

        mPillarIcon 		= (ImageView) 		findViewById(R.id.countPillarImage);
        mPillarCount		= (TextView)		findViewById(R.id.countPillarText);
        mFbmIcon 			= (ImageView) 		findViewById(R.id.countFbmImage);
        mFbmCount			= (TextView)		findViewById(R.id.countFbmText);
        mPassiveIcon 		= (ImageView) 		findViewById(R.id.countPassiveImage);
        mPassiveCount		= (TextView)		findViewById(R.id.countPassiveText);
        mIntersectedIcon 	= (ImageView) 		findViewById(R.id.countIntersectedImage);
        mIntersectedCount	= (TextView)		findViewById(R.id.countIntersectedText);
        mUnsyncedCount		= (TextView)		findViewById(R.id.countUnsyncedText);
        mUnsyncedIcon		= (ImageView) 		findViewById(R.id.countUnsyncedImage);
        mPhotosCount		= (TextView)		findViewById(R.id.countPhotosText);
        mPhotosIcon			= (ImageView) 		findViewById(R.id.countPhotosImage);
        mSyncBtn 			= (Button) 			findViewById(R.id.btnSync);
        
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
				startActivityForResult(i, R.id.btnNearest);
			}
		});       
        mSyncBtn.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View arg0) {
				new SyncTask(MainActivity.this, MainActivity.this).execute();
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
				startActivityForResult(i, R.id.btnMap);
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
			new SyncTask(MainActivity.this, this).execute();        	
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
		new PopulateCountsTask().execute();
    }
 
    
    
	@Override
	protected void onResume() {
		super.onResume();
		new PopulateCountsTask().execute();
	}


	@Override
	public void onSynced(int status) {
		Log.i(TAG, "onSynced");
		new PopulateCountsTask().execute();
	}





	private class PopulateCountsTask extends AsyncTask<Void, Void, Void> {
		int nPillar 		= 0;
		int nFbm			= 0;
		int nPassive		= 0;
		int nIntersected 	= 0;
		int nUnsynced 		= 0;
		int nPhotos 		= 0;
		DbHelper mDb = new DbHelper(MainActivity.this);
		@Override
		protected Void doInBackground(Void... arg0) {
			nPillar 		= mDb.countLoggedPillars();
			nFbm 			= mDb.countLoggedFbms();
			nPassive 		= mDb.countLoggedPassives();
			nIntersected 	= mDb.countLoggedIntersecteds();
			nUnsynced 		= mDb.countUnsynced();
			nPhotos 		= mDb.countPhotos();
			return null;
		}
		@Override
		protected void onPreExecute() {
			mDb.open();
	        mPillarIcon.setImageResource(R.drawable.t_pillar);
	        mPillarCount.setText("");
	        mFbmIcon.setImageResource(R.drawable.t_fbm);
	        mFbmCount.setText("");
	        mPassiveIcon.setImageResource(R.drawable.t_passive);
	        mPassiveCount.setText("");
	        mIntersectedIcon.setImageResource(R.drawable.t_intersected);
	        mIntersectedCount.setText("");
	        mUnsyncedIcon.setVisibility(View.INVISIBLE);
	        mUnsyncedCount.setText("");
	        mPhotosIcon.setVisibility(View.INVISIBLE);
	        mPhotosCount.setText("");
	        mSyncBtn.setTextColor(getResources().getColor(android.R.color.primary_text_light));
		}
		@Override
		protected void onProgressUpdate(Void... progress) {
		}
		@Override
		protected void onPostExecute(Void arg0) {
			if (nPillar > 0) {
				mPillarIcon.setImageResource(R.drawable.ts_pillar);
		        mPillarCount.setText(String.valueOf(nPillar));				
			}
			if (nFbm > 0) {
				mFbmIcon.setImageResource(R.drawable.ts_fbm);
		        mFbmCount.setText(String.valueOf(nFbm));				
			}
			if (nPassive > 0) {
				mPassiveIcon.setImageResource(R.drawable.ts_passive);
		        mPassiveCount.setText(String.valueOf(nPassive));				
			}
			if (nIntersected > 0) {
				mIntersectedIcon.setImageResource(R.drawable.ts_intersected);
		        mIntersectedCount.setText(String.valueOf(nIntersected));				
			}
			if (nUnsynced > 0) {
		        mUnsyncedIcon.setVisibility(View.VISIBLE);
		        mUnsyncedCount.setText(String.valueOf(nUnsynced));
			}
			if (nPhotos > 0) {
		        mPhotosIcon.setVisibility(View.VISIBLE);
		        mPhotosCount.setText(String.valueOf(nPhotos));
			}
			
			if (nUnsynced > 0 || nPhotos > 0) {
		        mSyncBtn.setTextColor(getResources().getColor(R.color.syncNow));
			} else {
		        mSyncBtn.setTextColor(getResources().getColor(android.R.color.primary_text_light));
			}
			if (mDb != null) {
				mDb.close();
			}
		}
		@Override
		protected void onCancelled() {
		}

	}


    
    

}

	
	