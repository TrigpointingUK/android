package com.trigpointinguk.android;

import com.trigpointinguk.android.common.LatLon;

import android.app.ListActivity;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ListView;
import android.widget.TextView;


public class NearestActivity extends ListActivity {
	private Location mCurrentLocation;
	private NearestCursorAdapter mListAdapter;
	private DbHelper mDb;
	static int mUpdateCount = 0;
	static int mLocationCount = 0;
	private boolean mTaskRunning = false;
	private LocationListener mLocationListener;
	private LocationManager mLocationManager; 
	private static final String TAG = "NearestActivity";
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);		
		setContentView(R.layout.triglist);

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
		updateHeader("cached");
		if (mCurrentLocation != null && !mTaskRunning) {new FindTrigsTask().execute();}
		


		// Define a listener that responds to location updates
		mLocationListener = new LocationListener() {
			public void onLocationChanged(Location location) {
				mLocationCount++;
				updateHeader("listener");
				if (isBetterLocation(location, mCurrentLocation)) {
					mCurrentLocation = location;
					if (!mTaskRunning) {new FindTrigsTask().execute();}
				}
			}
			public void onStatusChanged(String provider, int status, Bundle extras) {}
			public void onProviderEnabled(String provider) {}
			public void onProviderDisabled(String provider) {}
		};

		// Register the listener with the Location Manager to receive location updates
		mLocationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 1000*30, 250, mLocationListener);
		mLocationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000*300, 250, mLocationListener);
	}


	
	
	
	private void updateHeader(String comment) {
		final TextView strLocation = (TextView) findViewById(R.id.trigListLocation);
		
		if (null != mCurrentLocation) {
			LatLon ll = new LatLon(mCurrentLocation);
			strLocation.setText(String.format("%s %s %d %d %s" 
					, mCurrentLocation.getProvider().equals("gps") ? ll.getOSGB10() : ll.getOSGB6()
					, mCurrentLocation.getProvider(), mUpdateCount, mLocationCount, comment));
		} else {
			strLocation.setText("Location is unknown "+ comment);
		}
	}

	@Override
	protected void onDestroy() {
		mLocationManager.removeUpdates(mLocationListener);
		mDb.close();
		super.onDestroy();
	}

    protected void onListItemClick(ListView l, View v, int position, long id) {
        super.onListItemClick(l, v, position, id);
        Intent i = new Intent(this, TrigDetailsActivity.class);
        i.putExtra(DbHelper.TRIG_ID, id);
        Log.i(TAG, "Trig_id = " +id);
        startActivity(i);
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
				mListAdapter.swapCursor(c, mCurrentLocation);
			} catch (Exception e) {
				e.printStackTrace();
				mListAdapter.swapCursor(null, mCurrentLocation);
			}
			updateHeader("task");
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





}
