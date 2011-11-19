package com.trigpointinguk.android;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.provider.MediaStore;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.TimePicker;
import android.widget.Toast;
import android.widget.ViewSwitcher;

import com.trigpointinguk.android.common.ImageEventListener;
import com.trigpointinguk.android.common.ImageEventManager;

public class TrigDetailsLogTrigTab extends Activity implements ImageEventListener {
	private static final String TAG="TrigDetailsLogTrigTab";
    public static int TAKE_PHOTO = 1;
    private ImageEventManager mIem;
    private ViewSwitcher mSwitcher;
    
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.logtrig);

    	TimePicker tp = (TimePicker) findViewById(R.id.logTime);
		tp.setIs24HourView(true);

		mIem = new ImageEventManager(TrigDetailsLogTrigTab.this,TrigDetailsLogTrigTab.this);

		mSwitcher = (ViewSwitcher) findViewById(R.id.logswitcher);

		
		// Setup button to take photo
		Button takePhotoBtn = (Button) findViewById(R.id.logTakePhoto);
		takePhotoBtn.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View arg0) {
	        	Log.i(TAG, "Start camera intent and grab results");
	        	Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
	        	intent.putExtra(MediaStore.EXTRA_VIDEO_QUALITY, 1);
	        	mIem.listenForCameraEvents();
	        	startActivityForResult(intent, TAKE_PHOTO);
			}
		});	

		// Setup button to create a new log
		Button addLogBtn = (Button) findViewById(R.id.addLog);
		addLogBtn.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View arg0) {
	        	Log.i(TAG, "Create Log");
	        	mSwitcher.showNext();
			}
		});	


		// Setup button to create a new log
		Button deleteLogBtn = (Button) findViewById(R.id.logDelete);
		deleteLogBtn.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View arg0) {
	        	Log.i(TAG, "Delete Log");
	        	mSwitcher.showNext();
			}
		});	

	
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
        	Log.i(TAG, "Start camera intent and grab results");
        	Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        	intent.putExtra(MediaStore.EXTRA_VIDEO_QUALITY, 1);
        	mIem.listenForCameraEvents();
        	startActivityForResult(intent, TAKE_PHOTO);
            return true;
        case R.id.addlocation:
        	Toast.makeText(this, "Start location listener and grab results", Toast.LENGTH_SHORT).show();
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
    public void newImageAvailable(String path){
        Log.i(TAG, "New Camera image:\n"+path);
    }
	
        
}
