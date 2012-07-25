package com.trigpointinguk.android.logging;

import java.io.File;
import java.util.Arrays;

import android.app.Activity;
import android.database.Cursor;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.Spinner;

import com.trigpointinguk.android.DbHelper;
import com.trigpointinguk.android.R;
import com.trigpointinguk.android.types.PhotoSubject;

public class LogPhotoActivity extends Activity {
	
	private static final String TAG			= "LogPhotoActivity";
    
	private Integer				mPhotoId;
	
	private String 				mIconURL;
	private String 				mPhotoURL;
	private Integer				mTrigId;
    private ImageView			mThumb;
    private EditText			mName;
    private EditText			mDescr;
    private Spinner				mSubject;
    private CheckBox			mIsPublic;
    
    private DbHelper 			mDb;

	
	public void onCreate(Bundle savedInstanceState) {
	    super.onCreate(savedInstanceState);
	    setContentView(R.layout.logphoto);

		Bundle extras = getIntent().getExtras();
		mPhotoId = extras.getInt(DbHelper.PHOTO_ID);
		
		mThumb		= (ImageView)		findViewById(R.id.photoThumb);
	   	mName		= (EditText)		findViewById(R.id.photoName);
	   	mDescr		= (EditText)		findViewById(R.id.photoDescr);
	   	mSubject	= (Spinner)			findViewById(R.id.photoSubject);
	   	mIsPublic	= (CheckBox)		findViewById(R.id.photoIsPublic);
	   	
				
		// Setup condition spinner
		ArrayAdapter<PhotoSubject> adapter = new ArrayAdapter<PhotoSubject> (this, android.R.layout.simple_spinner_item, PhotoSubject.values());
		adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
		mSubject.setAdapter(adapter);

		// Setup button to save photo
		Button savePhotoBtn = (Button) findViewById(R.id.photoSave);
		savePhotoBtn.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View arg0) {
				Log.i(TAG, "Save Photo");
				setResult(RESULT_OK);
				finish();
			}
		});	

		// Setup button to remove photo
		Button removePhotoBtn = (Button) findViewById(R.id.photoRemove);
		removePhotoBtn.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View arg0) {
				Log.i(TAG, "Remove Photo");
				// delete the photo record from the database
				mDb.deletePhoto(mPhotoId);
				// delete the files from the cache
				new File (mPhotoURL).delete();
				new File (mIconURL).delete();
				setResult(RESULT_CANCELED);
				finish();
			}
		});	

		
		// Connect to database
		mDb = new DbHelper(this);
		mDb.open();

	}
	
	@Override
	protected void onPause() {
		Log.i(TAG, "onPause");
		super.onPause();
		savePhoto();
	}

	@Override
	protected void onResume() {
		Log.i(TAG, "onResume");
		super.onResume();
		if (mPhotoId != null) {
			populateFields();
		}
	}

	
	
	
	@Override
	protected void onDestroy() {
		Log.i(TAG, "onDestroy");
		mDb.close();
		super.onDestroy();
	}

	private void savePhoto() {
    	Log.i(TAG, "savePhoto");
    	    	
    	// Save the changes to the database
   		mDb.updatePhoto(mPhotoId,
   						mTrigId,
   						mName.getText().toString(),
   						mDescr.getText().toString(),
   						mIconURL,
   						mPhotoURL,
   						(PhotoSubject) mSubject.getSelectedItem(),
   						mIsPublic.isChecked()?1:0);	
	}

	private void populateFields () {
    	Log.i(TAG, "populateFields");
    	
    	Cursor c = mDb.fetchPhoto(mPhotoId);
    	if (c == null) {return;}

    	// set hidden variables
    	mIconURL 				= c.getString(c.getColumnIndex(DbHelper.PHOTO_ICON));
    	mPhotoURL 				= c.getString(c.getColumnIndex(DbHelper.PHOTO_PHOTO));
    	mTrigId 				= c.getInt(c.getColumnIndex(DbHelper.PHOTO_TRIG));

    	// set thumbnail image
    	mThumb.setImageBitmap(BitmapFactory.decodeFile(mIconURL));
    	
    	// set Text fields    	
    	mName.setText 			(c.getString(c.getColumnIndex(DbHelper.PHOTO_NAME)));
    	mDescr.setText			(c.getString(c.getColumnIndex(DbHelper.PHOTO_DESCR)));
    	
    	// set Flags
    	mIsPublic.setChecked	(c.getInt(c.getColumnIndex(DbHelper.PHOTO_ISPUBLIC)) > 0);
    	    	
    	// set Subject
    	PhotoSubject subj = PhotoSubject.fromCode(c.getString(c.getColumnIndex(DbHelper.PHOTO_SUBJECT)));
    	mSubject.setSelection (Arrays.asList(PhotoSubject.values()).indexOf(subj));

    	c.close();

    	
	}


}
