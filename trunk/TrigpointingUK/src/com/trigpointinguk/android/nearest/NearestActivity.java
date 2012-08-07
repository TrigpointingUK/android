package com.trigpointinguk.android.nearest;

import android.app.ListActivity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.database.Cursor;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.AsyncTask;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.Display;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;

import com.trigpointinguk.android.DbHelper;
import com.trigpointinguk.android.R;
import com.trigpointinguk.android.filter.Filter;
import com.trigpointinguk.android.filter.FilterActivity;
import com.trigpointinguk.android.trigdetails.TrigDetailsActivity;
import com.trigpointinguk.android.types.LatLon;


public class NearestActivity extends ListActivity implements SensorEventListener {
	private Cursor 					mCursor;
	private Location 				mCurrentLocation;
	private double 					mHeading = 0;
	private NearestCursorAdapter 	mListAdapter;
	private DbHelper 				mDb;
	static int 						mUpdateCount = 0;
	static int 						mLocationCount = 0;
	private boolean 				mTaskRunning = false;
	private LocationListener 		mLocationListener;
	private LocationManager 		mLocationManager;
	TextView						mStrLocation;
	TextView						mStrFilter;
	TextView						mNorthText;
	ImageView						mImgFilterPillar;
	ImageView						mImgFilterFBM;
	ImageView						mImgFilterPassive;
	ImageView						mImgFilterIntersected;
	ImageView						mCompassArrow;
	private SharedPreferences       mPrefs;
	private float[] 				mGravity;
	private float[] 				mGeomagnetic;
	private SensorManager 			mSensorManager;
	private Sensor 					accelerometer;
	private Sensor 					magnetometer;
	private int mOrientation;
	private boolean mUsingCompass;
	private static final String     USECOMPASS="useCompass";
	private static final String TAG = "NearestActivity";
	private static final int DETAILS = 1;
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);		
		setContentView(R.layout.triglist);

		// find view references
		mStrLocation 			= (TextView)  findViewById(R.id.trigListLocation);
		mStrFilter	 			= (TextView)  findViewById(R.id.trigListHeader);
		mNorthText	 			= (TextView)  findViewById(R.id.north);
		mImgFilterPillar 		= (ImageView) findViewById(R.id.filterPillar);
		mImgFilterFBM	 		= (ImageView) findViewById(R.id.filterFbm);
		mImgFilterPassive 		= (ImageView) findViewById(R.id.filterPassive);
		mImgFilterIntersected 	= (ImageView) findViewById(R.id.filterIntersected);
		mCompassArrow			= (ImageView) findViewById(R.id.compassArrow);
		
		mPrefs = PreferenceManager.getDefaultSharedPreferences(this);
		
		// create various objects
		mDb = new DbHelper(NearestActivity.this);
		mDb.open();
	
		// Start off with no location + no trigs
		mListAdapter = new NearestCursorAdapter(this, R.layout.trigrow, null, new String[]{}, new int[]{}, null);
		setListAdapter(mListAdapter);

		// Find a cached location
		mLocationManager = (LocationManager) this.getSystemService(Context.LOCATION_SERVICE);
		mCurrentLocation = mLocationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
		Location newLoc = mLocationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
		if (isBetterLocation(newLoc, mCurrentLocation)) {mCurrentLocation = newLoc;}
		updateLocationHeader("cached");
		if (mCurrentLocation != null && !mTaskRunning) {new FindTrigsTask().execute();}
		
		// Define a listener that responds to location updates
		mLocationListener = new LocationListener() {
			public void onLocationChanged(Location location) {
				mLocationCount++;
				updateLocationHeader("listener");
				if (isBetterLocation(location, mCurrentLocation)) {
					mCurrentLocation = location;
					refreshList();
				}
			}
			public void onStatusChanged(String provider, int status, Bundle extras) {}
			public void onProviderEnabled(String provider) {}
			public void onProviderDisabled(String provider) {}
		};

		mSensorManager = (SensorManager)getSystemService(SENSOR_SERVICE);
		accelerometer = mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
		magnetometer = mSensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);
		
		// Is the screen rotated?
		WindowManager windowManager =  (WindowManager) getSystemService(WINDOW_SERVICE);
		Display display = windowManager.getDefaultDisplay();
		mOrientation = display.getOrientation();
		
		Log.i(TAG, "getOrientation(): " + mOrientation);
		mListAdapter.setOrientation(mOrientation);
		 
	}


	
	
	
	@Override
	protected void onPause() {
		// save compass preference
		Editor editor = mPrefs.edit();
		editor.putBoolean(USECOMPASS, mUsingCompass);
		editor.commit();
		// stop listening to the GPS and compass
		mLocationManager.removeUpdates(mLocationListener);
		useCompass(false);
		super.onPause();
	}





	@Override
	protected void onResume() {
		super.onResume();
		// Register the listener with the Location Manager to receive location updates
		mLocationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 1000*30, 250, mLocationListener);
		mLocationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000*300, 250, mLocationListener);
		// Decide whether to use the compass
		useCompass(mPrefs.getBoolean(USECOMPASS, false));
		// Setup header icons
		updateFilterHeader();
	}


	private void useCompass(boolean use) {
		mUsingCompass = use;
		if (mUsingCompass) {
			mSensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_NORMAL);
		    mSensorManager.registerListener(this, magnetometer, SensorManager.SENSOR_DELAY_NORMAL);
		    mNorthText.setTextColor(getResources().getColor(R.color.compassEnabled));
	    } else {
		    mSensorManager.unregisterListener(this);
		    mHeading = 0;
		    mCompassArrow.setImageResource(mListAdapter.getArrow(0));
		    mNorthText.setTextColor(getResources().getColor(R.color.compassDisabled));
        	mListAdapter.setHeading(0);
        	mListAdapter.notifyDataSetChanged();
		}
	}
	


	private void refreshList() {
		if (!mTaskRunning) {new FindTrigsTask().execute();}
	}
	
	
	private void updateLocationHeader(String comment) {
		
		if (null != mCurrentLocation) {
			LatLon ll = new LatLon(mCurrentLocation);
			mStrLocation.setText(String.format("Near to %s   (from %s)" 
					, mCurrentLocation.getProvider().equals("gps") ? ll.getOSGB10() : ll.getOSGB6()
					, mCurrentLocation.getProvider()
			));
			Log.d(TAG, "Location update count : " + mUpdateCount + " Location count : " + mLocationCount + " " + comment);
		} else {
			mStrLocation.setText("Location is unknown");
			Log.d(TAG, "Location unknown : " + mUpdateCount + " Location count : " + mLocationCount + " " + comment);
		}
	}

	private void updateFilterHeader() {
		Filter filter = new Filter(this);
		if (filter.isPillars()) {
			mImgFilterPillar.setImageResource(R.drawable.ts_pillar);
		} else {
			mImgFilterPillar.setImageResource(R.drawable.t_pillar);
		}

		if (filter.isFBMs()) {
			mImgFilterFBM.setImageResource(R.drawable.ts_fbm);
		} else {
			mImgFilterFBM.setImageResource(R.drawable.t_fbm);
		}
	
		if (filter.isPassives()) {
			mImgFilterPassive.setImageResource(R.drawable.ts_passive);
		} else {
			mImgFilterPassive.setImageResource(R.drawable.t_passive);
		}
		
		if (filter.isIntersecteds()) {
			mImgFilterIntersected.setImageResource(R.drawable.ts_intersected);
		} else {
			mImgFilterIntersected.setImageResource(R.drawable.t_intersected);
		}

		mStrFilter.setText(mPrefs.getString(Filter.FILTERRADIOTEXT, "All") + " trigpoints");

	}
	
	@Override
    public boolean onCreateOptionsMenu(Menu menu) {
        boolean result = super.onCreateOptionsMenu(menu);
		getMenuInflater().inflate(R.menu.nearestmenu, menu);
        return result;
    }    
    
    
	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		Intent i;
        switch (item.getItemId()) {
        
        case R.id.filter:
            i = new Intent(NearestActivity.this, FilterActivity.class);
            startActivityForResult(i, R.id.filter);
            return true;
        case R.id.heading:
        	useCompass(!mUsingCompass);
        	return true;
        }
		return super.onOptionsItemSelected(item);
	}
        
	@Override
	protected void onDestroy() {
		mDb.close();
		super.onDestroy();
	}

    protected void onListItemClick(ListView l, View v, int position, long id) {
        super.onListItemClick(l, v, position, id);
        Intent i = new Intent(this, TrigDetailsActivity.class);
        i.putExtra(DbHelper.TRIG_ID, id);
        Log.i(TAG, "Trig_id = " +id);
        startActivityForResult(i, DETAILS);
    }
	
	
    
	
	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		Log.i(TAG, "onActivityResult");
		refreshList();
		updateFilterHeader();
	}




	private class FindTrigsTask extends AsyncTask<Void, Integer, Cursor> {
		
		protected Cursor doInBackground(Void... arg0) {
			Log.i(TAG, "FindTrigsTask.doInBackground");
			Cursor c = null;
			try {
				c = mDb.fetchTrigList(mCurrentLocation);
				startManagingCursor(c);
			} catch (Exception e) {
				e.printStackTrace();
			}
			mUpdateCount++;
			return c;
		}
		protected void onProgressUpdate(Integer... progress) {
		}
		protected void onPostExecute(Cursor c) {			
			Log.i(TAG, "FindTrigsTask.onPostExecute " + c);
			try {
				mCursor = c;
				mListAdapter.swapCursor(mCursor, mCurrentLocation);
			} catch (Exception e) {
				e.printStackTrace();
				mListAdapter.swapCursor(null, mCurrentLocation);
			}
			updateLocationHeader("task");
			mTaskRunning = false;
		}
		protected void onPreExecute() {
			Log.i(TAG, "FindTrigsTask.onPreExecute");
			mTaskRunning = true;
		}
	}






	/** Determines whether one Location reading is better than the current Location fix
	 * @param location  The new Location that you want to evaluate
	 * @param currentBestLocation  The current Location fix, to which you want to compare the new one
	 */
	private static final int TWO_MINUTES = 1000 * 60 * 2;
	protected boolean isBetterLocation(Location location, Location currentBestLocation) {
		if (currentBestLocation == null) {
			// A new location is always better than no location
			return true;
		}
		
		if (location == null) {
			// A null location is never better than anything
			return false;
		}

		// Check whether the new location fix is newer or older
		long timeDelta = location.getTime() - currentBestLocation.getTime();
		boolean isSignificantlyNewer = timeDelta > TWO_MINUTES;
		boolean isSignificantlyOlder = timeDelta < -TWO_MINUTES;
		boolean isNewer = timeDelta > 0;

		// If it's been more than two minutes since the current location, use the new location
		// because the user has likely moved
		if (isSignificantlyNewer) {
			return true;
			// If the new location is more than two minutes older, it must be worse
		} else if (isSignificantlyOlder) {
			return false;
		}

		// Check whether the new location fix is more or less accurate
		int accuracyDelta = (int) (location.getAccuracy() - currentBestLocation.getAccuracy());
		boolean isLessAccurate = accuracyDelta > 0;
		boolean isMoreAccurate = accuracyDelta < 0;
		boolean isSignificantlyLessAccurate = accuracyDelta > 200;

		// Check if the old and new location are from the same provider
		boolean isFromSameProvider = isSameProvider(location.getProvider(),
				currentBestLocation.getProvider());

		// Determine location quality using a combination of timeliness and accuracy
		if (isMoreAccurate) {
			return true;
		} else if (isNewer && !isLessAccurate) {
			return true;
		} else if (isNewer && !isSignificantlyLessAccurate && isFromSameProvider) {
			return true;
		}
		return false;
	}

	/** Checks whether two providers are the same */
	private boolean isSameProvider(String provider1, String provider2) {
		if (provider1 == null) {
			return provider2 == null;
		}
		return provider1.equals(provider2);
	}




	// handle compass events
	@Override
	public void onAccuracyChanged(Sensor sensor, int accuracy) {}	
	@Override
	public void onSensorChanged(SensorEvent event) {
		switch (event.sensor.getType()) {
		case Sensor.TYPE_ACCELEROMETER:
			mGravity = event.values;
			break;
		case Sensor.TYPE_MAGNETIC_FIELD:
	        mGeomagnetic = event.values;
	        break;
		}

		if (!mUsingCompass) {return;}
		
		if (mGravity != null && mGeomagnetic != null) {
	    	float R[] = new float[9];
	        float I[] = new float[9];
	        boolean success = SensorManager.getRotationMatrix(R, I, mGravity, mGeomagnetic);
	        if (success) {
	        	float orientation[] = new float[3];
	        	SensorManager.getOrientation(R, orientation);
	        	mHeading = orientation[0] * 180.0/Math.PI; // orientation contains: azimuth[0], pitch[1] and roll[2]
	        	Log.d(TAG, "Heading = " + mHeading);
	        	mListAdapter.setHeading(mHeading);
	        	mListAdapter.notifyDataSetChanged();
			    mCompassArrow.setImageResource(mListAdapter.getArrow(-mHeading));
	        }
	    }
	}





}
