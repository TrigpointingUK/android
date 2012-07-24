package com.trigpointinguk.android.logging;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.List;

import org.acra.ErrorReporter;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.net.Uri;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnFocusChangeListener;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.DatePicker;
import android.widget.DatePicker.OnDateChangedListener;
import android.widget.EditText;
import android.widget.Gallery;
import android.widget.RatingBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.TimePicker;
import android.widget.Toast;
import android.widget.ViewSwitcher;

import com.trigpointinguk.android.DbHelper;
import com.trigpointinguk.android.R;
import com.trigpointinguk.android.common.FileCache;
import com.trigpointinguk.android.common.Utils;
import com.trigpointinguk.android.types.Condition;
import com.trigpointinguk.android.types.LatLon;
import com.trigpointinguk.android.types.PhotoSubject;
import com.trigpointinguk.android.types.TrigPhoto;

public class LogTrigActivity extends Activity implements OnDateChangedListener, LocationListener, SyncListener {
	private static final String TAG			= "LogTrigActivity";
    private static final int    CHOOSE_PHOTO  = 1;
    private static final int    EDIT_PHOTO  = 2;
    private SharedPreferences 	mPrefs;
    
	private Long				mTrigId;
	private LatLon				mTrigLocation;
	
    private ViewSwitcher 		mSwitcher;
    private DatePicker			mDate;
    private TimePicker			mTime;
    private EditText			mGridref;
    private EditText			mFb;
    private Spinner				mCondition;
    private RatingBar			mScore;
    private EditText			mComment;
    private CheckBox			mAdminFlag;
    private CheckBox			mUserFlag;
    private Gallery				mGallery;
    private TextView			mLocationError;
    
    private DbHelper 			mDb;
    private boolean				mHaveLog;
    
    private	List<TrigPhoto> 	mPhotos; 

    private ProgressDialog		mProgressDialog;
    private LocationManager 	mLocationManager;
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		Log.i(TAG, "onCreate");
		super.onCreate(savedInstanceState);
		setContentView(R.layout.logtrig);
        mPrefs = PreferenceManager.getDefaultSharedPreferences(this);

		// Get trig_id from extras 
		Bundle extras = getIntent().getExtras();
		if (extras != null) {
			mTrigId = extras.getLong(DbHelper.TRIG_ID);
		}
		if (mTrigId == null) {mTrigId=0L;}
		Log.i(TAG, "Trig_id = "+mTrigId);
		
		// Get references to various views and form elements
		mSwitcher 		= (ViewSwitcher)	findViewById(R.id.logswitcher);
	   	mTime			= (TimePicker)		findViewById(R.id.logTime);
	   	mDate			= (DatePicker)		findViewById(R.id.logDate);
	   	mGridref 		= (EditText)		findViewById(R.id.logGridref);
	   	mLocationError 	= (TextView)		findViewById(R.id.locationError);
	   	mFb 			= (EditText)		findViewById(R.id.logFB);
	   	mCondition		= (Spinner)			findViewById(R.id.logCondition);
	   	mScore			= (RatingBar)		findViewById(R.id.logScore);
	   	mComment		= (EditText)		findViewById(R.id.logComment);
	   	mAdminFlag		= (CheckBox)		findViewById(R.id.logAdminFlag);
	   	mUserFlag		= (CheckBox)		findViewById(R.id.logUserFlag);
	    mGallery 		= (Gallery) 		findViewById(R.id.logGallery);

	    
    	// Setup time picker options which cannot be set in the config xml
 		mTime.setIs24HourView(true);

		// Setup condition spinner
		ArrayAdapter<Condition> adapter = new ArrayAdapter<Condition> (this, android.R.layout.simple_spinner_item, Condition.values());
		adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
		mCondition.setAdapter(adapter);

		// Switch off logUserFlag
        if (!mPrefs.getBoolean("experimental", false)) {
        	mUserFlag.setVisibility(View.INVISIBLE);
        }
        	
		
		// Setup listener on gallery photos
	    mGallery.setOnItemClickListener(new OnItemClickListener() {
	        public void onItemClick(AdapterView<?> parent, View v, int position, long id) {
	            Log.i(TAG, "Clicked photo icon number : " + position);
	            Intent i = new Intent(LogTrigActivity.this, LogPhotoActivity.class);
	            i.putExtra(DbHelper.PHOTO_ID, mPhotos.get(position).getLogID());
	            startActivityForResult(i, EDIT_PHOTO);
	        }
	    });

		
		// Connect to database
		mDb = new DbHelper(this);
		mDb.open();
		
		// Fetch trigpoint info
		Cursor c = mDb.fetchTrigInfo(mTrigId);
		c.moveToFirst();
		Double lat = Double.valueOf(c.getString(c.getColumnIndex(DbHelper.TRIG_LAT)));
		Double lon = Double.valueOf(c.getString(c.getColumnIndex(DbHelper.TRIG_LON)));
		mTrigLocation = new LatLon(lat, lon);
		c.close();


		// Check to see whether log already exists
		mHaveLog = (mDb.fetchLog(mTrigId) != null);
		if (mHaveLog) {
			mSwitcher.showNext();
		}
		
		
		// Setup button to add photo
		Button takePhotoBtn = (Button) findViewById(R.id.logAddPhoto);
		takePhotoBtn.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View arg0) {
				choosePhoto();
			}
		});	

		// Setup button to create a new log
		Button addLogBtn = (Button) findViewById(R.id.addLog);
		addLogBtn.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View arg0) {
	        	Log.i(TAG, "Create Log");
	        	createNewLog();
				populateFields();
	        	mSwitcher.showNext();
	        	mHaveLog = true;
			}
		});	

		// Setup button to delete the log
		Button deleteLogBtn = (Button) findViewById(R.id.logDelete);
		deleteLogBtn.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View arg0) {
	        	Log.i(TAG, "Delete Log");
	        	deleteLog();
	        	mSwitcher.showNext();
	        	mHaveLog = false;
			}
		});	

		// Setup button to add the current location
		Button locationBtn = (Button) findViewById(R.id.logGetLocation);
		locationBtn.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View arg0) {
	        	getLocation();
			}
		});	

		// Setup button to upload the log
		Button uploadBtn = (Button) findViewById(R.id.logUploadNow);
		uploadBtn.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View arg0) {
	        	uploadLog();
			}
		});	

		// Setup button to sync the log later (ie do nothing!)
		Button syncLaterBtn = (Button) findViewById(R.id.logSyncLater);
		syncLaterBtn.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View arg0) {
	        	finish();
			}
		});	


		
		// check new grid references
		mGridref.setOnFocusChangeListener(new OnFocusChangeListener() {			@Override
			public void onFocusChange(View v, boolean hasFocus) {
				if (!hasFocus) {
					checkDistance();
				}
			}
		});		
	}

	public void checkDistance() {
		Log.i(TAG, "checkDistance");
		try {
			LatLon ll = new LatLon(mGridref.getText().toString());
			Double dist = 1000 * ll.distanceTo(mTrigLocation);
			Log.e(TAG, "trig " + mTrigLocation.getOSGB10() + " - " + mTrigLocation.getWGS());
			Log.e(TAG, "log " + ll.getOSGB10() + " - " + ll.getWGS());
			Log.e(TAG, "Gridref " + dist.intValue() + "m from database location");
			if (dist >= 50) {
				mLocationError.setText("Warning: " + dist.intValue() + "m from database location");
				mLocationError.setTextColor(getResources().getColor(R.color.errorcolour));
			} else {
				mLocationError.setText(dist.intValue() + "m from database location");
				mLocationError.setTextColor(getResources().getColor(R.color.okcolour));
			}
		} catch (IllegalArgumentException e) {
			Log.e(TAG, e.getMessage());
			mLocationError.setText(e.getMessage());
			mLocationError.setTextColor(getResources().getColor(R.color.errorcolour));
		}		
	}
	
	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		boolean result = super.onCreateOptionsMenu(menu);
		getMenuInflater().inflate(R.menu.logtrigmenu, menu);
		return result;
	}    

	public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
        case R.id.addphoto:
			choosePhoto();
            return true;
        case R.id.addlocation:
        	getLocation();
        	return true;
        }
        return false;
    }
 
    
	@Override
	protected void onPause() {
		Log.i(TAG, "onPause");
		super.onPause();
		saveLog();
	}

	@Override
	protected void onResume() {
		Log.i(TAG, "onResume");
		super.onResume();
		if (mTrigId != null) {
			populateFields();
		}
	}

    @Override
	protected void onDestroy() {
		Log.i(TAG, "onDestroy");
		super.onDestroy();
		if (mDb != null) {
			mDb.close();
		}
	}

	
    // service request to choose a photo
    private void choosePhoto() {
    	Log.i(TAG, "Get a photo from the gallery");
    	Intent photoPickerIntent = new Intent(Intent.ACTION_PICK);
    	photoPickerIntent.setType("image/*");
    	startActivityForResult(photoPickerIntent, CHOOSE_PHOTO);
    }
 
    
    
    
	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		super.onActivityResult(requestCode, resultCode, data); 
    	switch (requestCode) {
    	case CHOOSE_PHOTO:
    		createPhoto(requestCode, resultCode, data);
    		break;
    	case EDIT_PHOTO:
    		updateGallery();
    		break;
    	}
	}	
	
    
    
	private void createPhoto (int requestCode, int resultCode, Intent data) {
		// Photo chosen - create DB entry and send user to edit activity
		Log.i(TAG, "createPhoto");
		if(resultCode == RESULT_OK){  
			Uri selectedImageUri = data.getData();
    		Log.i(TAG, "Photo URI - " + selectedImageUri);
    		
    		// create a database record for the new photo
    		Long photoId = mDb.createPhoto(mTrigId, "", "", "", "", PhotoSubject.NOSUBJECT, 0);
    		Log.i(TAG, "Created Photo - " + photoId);

    		
    		// shrink the photo and store on SD card
			String cachedir  = new FileCache(this, "logphotos").getCacheDir().getAbsolutePath();
			String photoPath = new String(cachedir + "/" + photoId + "_I.jpg");
			String thumbPath = new String(cachedir + "/" + photoId + "_T.jpg");
			Log.d(TAG, photoPath + " - " + thumbPath);
			try {
				Bitmap bThumb = Utils.decodeUri(this, selectedImageUri,  100);
				Bitmap bPhoto = Utils.decodeUri(this, selectedImageUri,  640);
				Utils.saveBitmapToFile(thumbPath, bThumb, 50);
				Utils.saveBitmapToFile(photoPath, bPhoto, 50);
			} catch (FileNotFoundException e) {
				e.printStackTrace();
				ErrorReporter.getInstance().handleSilentException(e);
				return;
			} catch (IOException e) {
				e.printStackTrace();
				ErrorReporter.getInstance().handleSilentException(e);
				return;
			}
    		
    		// update the database record with the new photos
    		mDb.updatePhoto(photoId, mTrigId, "", "", thumbPath, photoPath, PhotoSubject.NOSUBJECT, 0);
    		Log.i(TAG, "Updated photo - " + photoId);
    		
    		// edit the other fields for the new photo
            Intent i = new Intent(this, LogPhotoActivity.class);
            i.putExtra(DbHelper.PHOTO_ID, photoId);
            startActivityForResult(i, EDIT_PHOTO);
		}
    		
	}

	
    
 
    private void populateFields() {
    	Log.i(TAG, "populateFields");
    	Cursor c = mDb.fetchLog(mTrigId);
    	if (c == null) {return;}

    	// set Date
    	mDate.init				(c.getInt(c.getColumnIndex(DbHelper.LOG_YEAR)), 
    							 c.getInt(c.getColumnIndex(DbHelper.LOG_MONTH)),
    							 c.getInt(c.getColumnIndex(DbHelper.LOG_DAY)), 
    							 this);

    	// set Time
    	mTime.setCurrentHour  	(c.getInt(c.getColumnIndex(DbHelper.LOG_HOUR)));
    	mTime.setCurrentMinute 	(c.getInt(c.getColumnIndex(DbHelper.LOG_MINUTES)));
    	
    	// set Text fields    	
    	mComment.setText 		(c.getString(c.getColumnIndex(DbHelper.LOG_COMMENT)));
    	mGridref.setText		(c.getString(c.getColumnIndex(DbHelper.LOG_GRIDREF)));
    	mFb.setText 			(c.getString(c.getColumnIndex(DbHelper.LOG_FB)));
    	
    	// set Flags
    	mAdminFlag.setChecked	(c.getInt(c.getColumnIndex(DbHelper.LOG_FLAGADMINS)) > 0);
    	mUserFlag.setChecked 	(c.getInt(c.getColumnIndex(DbHelper.LOG_FLAGUSERS))  > 0);
    	
    	// set Score
    	mScore.setRating		(c.getInt(c.getColumnIndex(DbHelper.LOG_SCORE)));
    	
    	// set Condition
    	Condition cond = Condition.fromCode(c.getString(c.getColumnIndex(DbHelper.LOG_CONDITION)));
    	mCondition.setSelection (Arrays.asList(Condition.values()).indexOf(cond));

    	c.close();
    	
    	updateGallery();
    	checkDistance();

    }
    
    private void updateGallery() {
		Log.i(TAG, "updateGallery");
	    mPhotos = new ArrayList<TrigPhoto>(); 
		Cursor c = mDb.fetchPhotos(mTrigId);
		if (c!=null) {
			do {
				TrigPhoto photo = new TrigPhoto();
				photo.setLogID		(c.getLong(c.getColumnIndex(DbHelper.PHOTO_ID)));
				photo.setIconURL	(c.getString(c.getColumnIndex(DbHelper.PHOTO_ICON)));
				mPhotos.add(photo);
			} while (c.moveToNext());
			c.close();		
		}
		
		mGallery.setAdapter(new LogTrigGalleryAdapter(this, mPhotos.toArray(new TrigPhoto[mPhotos.size()])));
	}

    
    
	private void saveLog() {
    	Log.i(TAG, "saveLog");
    	
    	// Only save the log if one already exists
    	if (!mHaveLog) {return;}
    	
    	// Save the changes to the database
    	try {
    		mDb.mDb.beginTransaction();
    		mDb.deleteLog(mTrigId);
    		mDb.createLog(mTrigId, mDate.getYear(), mDate.getMonth(), mDate.getDayOfMonth(), 
				mTime.getCurrentHour(), mTime.getCurrentMinute(), mGridref.getText().toString(), mFb.getText().toString(), 
				(Condition) mCondition.getSelectedItem(), (int) mScore.getRating(), 
				mComment.getText().toString(), mAdminFlag.isChecked()?1:0, mUserFlag.isChecked()?1:0);
    		mDb.mDb.setTransactionSuccessful();
        	Log.i(TAG, "Transaction Successful");
    	} catch (Exception e) {
    		Log.i(TAG, "Transaction rolled back");
    	} finally {
    		mDb.mDb.endTransaction();
    	}
    }


    private void createNewLog() {
    	// Create an empty log with default values for time, score etc
    	Calendar now = Calendar.getInstance();
    	mDb.createLog(mTrigId, now.get(Calendar.YEAR), now.get(Calendar.MONTH), now.get(Calendar.DAY_OF_MONTH), 
    			now.get(Calendar.HOUR_OF_DAY), now.get(Calendar.MINUTE), "", "", Condition.NOTLOGGED, 5, "", 0, 0);
    }
    
    private void deleteLog() {
    	// delete log records from DB
    	mDb.deleteLog(mTrigId);
    	// delete image files from filesystem
		Cursor c = mDb.fetchPhotos(mTrigId);
		if (c!=null) {
			do {
				new File(c.getString(c.getColumnIndex(DbHelper.PHOTO_PHOTO))).delete();
				new File(c.getString(c.getColumnIndex(DbHelper.PHOTO_ICON))).delete();
			} while (c.moveToNext());
		}
    	// delete photo records from DB
    	mDb.deletePhotosForTrig(mTrigId);
    }

    
    
    private void uploadLog() {
    	Log.i(TAG, "uploadLog");
    	
    	// check for login credentials in prefs
    	SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
		if (prefs.getString("username", "").equals("")) {
			Toast.makeText(this, R.string.toastAddUsername, Toast.LENGTH_LONG).show();
			return;
		} 
		if (prefs.getString("plaintextpassword", "").equals("")) {
			Toast.makeText(this, R.string.toastAddPassword, Toast.LENGTH_LONG).show();
			return;
		} 
		saveLog();
		new SyncTask(LogTrigActivity.this, this).execute(mTrigId);
    }

    
	@Override
	public void onDateChanged(DatePicker view, int pYear, int pMonth, int pDay) {
		Log.i(TAG, "Date changed");
		Calendar cal = Calendar.getInstance();
		int day = cal.get(Calendar.DATE);
		int month = cal.get(Calendar.MONTH);
		int year = cal.get(Calendar.YEAR);
		if ( pYear  > year
		|| ( pYear == year && pMonth  > month)
		|| ( pYear == year && pMonth == month && pDay > day) ) {
			view.updateDate(year, month, day);
		}
	}

	
	
	
	   private void getLocation() {
	    	Log.i(TAG, "Start location listener and put up dialog box");
	    
		    mLocationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
		    
		    if (!mLocationManager.isProviderEnabled (LocationManager.GPS_PROVIDER)) {
				Log.w(TAG, "GPS not enabled in system settings!");
				Toast.makeText(this, "Please enable GPS in system settings!", Toast.LENGTH_LONG).show();
				return;
		    }

		    // Start listening for GPS updates
		    mLocationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000L,1.0f, this);

			mProgressDialog = new ProgressDialog(this);
			mProgressDialog.setMessage("Getting accurate GPS fix...");
			mProgressDialog.setIndeterminate(true);
			mProgressDialog.setCancelable(true);
			mProgressDialog.show();
			mProgressDialog.setOnCancelListener (new DialogInterface.OnCancelListener() {
				@Override
				public void onCancel(DialogInterface arg0) {
					Toast.makeText(LogTrigActivity.this, "Cancelled Get location", Toast.LENGTH_SHORT).show();
					mLocationManager.removeUpdates(LogTrigActivity.this);
				}});
	    }
	    

	
	@Override
	public void onLocationChanged(Location location) {
		// Found GPS location, so cancel dialog, update the GUI, and stop listening for locations

		LatLon ll = new LatLon(location);
		mGridref.setText(ll.getOSGB10());
		checkDistance();
		
		mProgressDialog.dismiss();
		Toast.makeText(this, "GPS location added", Toast.LENGTH_SHORT).show();

		mLocationManager.removeUpdates(this);
	}

	@Override
	public void onProviderDisabled(String provider) {
		Log.i(TAG, "GPS provider disabled");
		mProgressDialog.cancel();
	}

	@Override
	public void onProviderEnabled(String provider) {
		Log.i(TAG, "GPS provider enabled");
	}

	@Override
	public void onStatusChanged(String provider, int status, Bundle extras) {
	}

	@Override
	public void onSynced(int status) {
    	if (status == SyncTask.SUCCESS) {
    		mSwitcher.showNext();
        	mHaveLog = false;
    	}
	}
}




