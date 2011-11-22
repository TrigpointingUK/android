package com.trigpointinguk.android;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

import android.app.Activity;
import android.content.Intent;
import android.database.Cursor;
import android.os.Bundle;
import android.provider.MediaStore;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.DatePicker;
import android.widget.EditText;
import android.widget.RatingBar;
import android.widget.Spinner;
import android.widget.TimePicker;
import android.widget.Toast;
import android.widget.ViewSwitcher;
import android.widget.DatePicker.OnDateChangedListener;

import com.trigpointinguk.android.common.ImageEventListener;
import com.trigpointinguk.android.common.ImageEventManager;

public class TrigDetailsLogTrigTab extends Activity implements ImageEventListener, OnDateChangedListener {
	private static final String TAG			= "TrigDetailsLogTrigTab";
    private static final int    TAKE_PHOTO  = 1;
    
	private Long 				mTrigId;
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
    
    private ImageEventManager 	mIem;    
    private DbHelper 			mDb;

    
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		Log.i(TAG, "onCreate");
		super.onCreate(savedInstanceState);
		setContentView(R.layout.logtrig);
		
		// Get trig_id from extras 
		Bundle extras = getIntent().getExtras();
		if (extras == null) {
			mTrigId = 0L;
		} else {
			mTrigId = extras.getLong(DbHelper.TRIG_ID);
			if (mTrigId == null) {mTrigId=0L;}
		}
		Log.i("TrigInfo", "Trig_id = "+mTrigId);
		
		// Get references to various views and form elements
		mSwitcher 	= (ViewSwitcher)	findViewById(R.id.logswitcher);
	   	mTime		= (TimePicker)		findViewById(R.id.logTime);
	   	mDate		= (DatePicker)		findViewById(R.id.logDate);
	   	mGridref 	= (EditText)		findViewById(R.id.logGridref);
	   	mFb 		= (EditText)		findViewById(R.id.logFB);
	   	mCondition	= (Spinner)			findViewById(R.id.logCondition);
	   	mScore		= (RatingBar)		findViewById(R.id.logScore);
	   	mComment	= (EditText)		findViewById(R.id.logComment);
	   	mAdminFlag	= (CheckBox)		findViewById(R.id.logAdminFlag);
	   	mUserFlag	= (CheckBox)		findViewById(R.id.logUserFlag);
	   	
	   	
    	// Setup time picker options which cannot be set in the config xml
 		mTime.setIs24HourView(true);

		// Class to listen for new photos appearing on the external storage
		mIem = new ImageEventManager(TrigDetailsLogTrigTab.this,TrigDetailsLogTrigTab.this);
		
		// Connect to database
		mDb = new DbHelper(this);
		mDb.open();
		
		// Check to see whether log already exists
		if (mDb.fetchLog(mTrigId) != null) {
			populateFields();
			mSwitcher.showNext();
		}
		
		
		// Setup button to take photo
		Button takePhotoBtn = (Button) findViewById(R.id.logTakePhoto);
		takePhotoBtn.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View arg0) {
				takePhoto();
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

		// Setup condition spinner
		List<Condition> clist = new ArrayList<Condition>();
		clist.add(Condition.NOTLOGGED);
		clist.add(Condition.DAMAGED);
		clist.add(Condition.MISSING);

		ArrayAdapter<Condition> adapter = new ArrayAdapter<Condition> (this, android.R.layout.simple_spinner_item, Condition.values());
		adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
		mCondition.setAdapter(adapter);
	
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
			takePhoto();
            return true;
        case R.id.addlocation:
        	getLocation();
        	return true;
        }
        return false;
    }
 
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
    	if(requestCode==TAKE_PHOTO){
            switch (resultCode) {
                case RESULT_OK:
                    mIem.cameraIntentComplete();
                    Log.i(TAG, "Camera Intent Complete");
                    break;
                case RESULT_CANCELED:
                    mIem.ignoreCameraEvents();
                    Log.i(TAG, "Camera Intent Cancelled");
                    break;
            }
    	}
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

	@Override
    public void newImageAvailable(String path){
        Log.i(TAG, "New Camera image:\n"+path);
    }
	
    
    private void takePhoto() {
    	Log.i(TAG, "Start camera intent and grab results");
    	Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
    	intent.putExtra(MediaStore.EXTRA_VIDEO_QUALITY, 1);
    	mIem.listenForCameraEvents();
    	startActivityForResult(intent, TAKE_PHOTO);
    }
 
    private void getLocation() {
    	Log.i(TAG, "Start location listener and grab results");
    	Toast.makeText(this, "Getting current location", Toast.LENGTH_SHORT).show();    	
    }
    
    private void populateFields() {
    	Cursor c = mDb.fetchLog(mTrigId);
    	if (c == null) {return;}
    	startManagingCursor(c);

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
    	//mCondition.setSelection (cond.ordinal());
    	
    	c.close();
    }
    
    private void saveLog() {
    	
    }


    private void createNewLog() {
    	// Create an empty log with default values for time, score etc
    	Calendar now = Calendar.getInstance();
    	mDb.createLog(mTrigId, now.get(Calendar.YEAR), now.get(Calendar.MONTH), now.get(Calendar.DAY_OF_MONTH), 
    			now.get(Calendar.HOUR_OF_DAY), now.get(Calendar.MINUTE), "", "", Condition.NOTLOGGED, 5, "", 0, 0);
    }
    
    private void deleteLog() {
    	mDb.deleteLog(mTrigId);
    }

	@Override
	public void onDateChanged(DatePicker arg0, int arg1, int arg2, int arg3) {
		// TODO Auto-generated method stub
		
	}

}




