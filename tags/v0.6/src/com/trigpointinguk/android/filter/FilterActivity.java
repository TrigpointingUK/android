package com.trigpointinguk.android.filter;

import android.app.Activity;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.util.Log;
import android.widget.RadioGroup;
import android.widget.Spinner;

import com.trigpointinguk.android.R;

public class FilterActivity extends Activity {
	private static final String TAG = "FilterActivity";
	private SharedPreferences 	mPrefs;
	private Spinner				mFilterType;
	private RadioGroup 			mFilterRadio;
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.filter);

		mPrefs = PreferenceManager.getDefaultSharedPreferences(this);
		mFilterType 	= (Spinner)		findViewById(R.id.filterType);  
		mFilterRadio	= (RadioGroup)	findViewById(R.id.filterRadio);

	}

	
	


	@Override
	protected void onPause() {
		Log.i(TAG, "onPause");
		
		Editor editor = mPrefs.edit();
		
		// Get identifier for selected item from physical type list
		editor.putInt(Filter.FILTERTYPE,  mFilterType.getSelectedItemPosition());
	
		// Get ID of selected radiobox item
		editor.putInt(Filter.FILTERRADIO, mFilterRadio.getCheckedRadioButtonId());
	
		// Save to prefs
		editor.commit();
		super.onPause();
	}

	@Override
	protected void onResume() {
		Log.i(TAG, "onResume");
		super.onResume();
		
		mFilterRadio.check(mPrefs.getInt(Filter.FILTERRADIO, R.id.filterAll));
		
		mFilterType.setSelection(mPrefs.getInt(Filter.FILTERTYPE, 0));
	}

}
