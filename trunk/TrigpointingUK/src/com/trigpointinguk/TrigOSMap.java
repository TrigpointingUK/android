package com.trigpointinguk;

import java.util.ArrayList;
import java.util.List;

import android.app.Activity;
import android.database.Cursor;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.Gallery;
import android.widget.Toast;
import android.widget.AdapterView.OnItemClickListener;

public class TrigOSMap extends Activity {
	private static final String TAG = "TrigOSMap";

	private long mTrigId;
	private TrigDbHelper mDb;


	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.trigosmap);

		// get trig_id from extras
        Bundle extras = getIntent().getExtras();
		if (extras == null) {return;}
		mTrigId = extras.getLong(TrigDbHelper.TRIG_ID);
		Log.i(TAG, "Trig_id = "+mTrigId);
		
		// get trig info from database
		mDb = new TrigDbHelper(TrigOSMap.this);
		mDb.open();		
		Cursor c = mDb.fetchTrigInfo(mTrigId);
		c.moveToFirst();
		String[] urls = getURLs(mTrigId, c);
		c.close();
		
	    Gallery gallery = (Gallery) findViewById(R.id.trigosgallery);
	    gallery.setAdapter(new TrigOSMapAdapter(this, urls));

	    
	    
	    gallery.setOnItemClickListener(new OnItemClickListener() {
	        public void onItemClick(AdapterView<?> parent, View v, int position, long id) {
	            Toast.makeText(TrigOSMap.this, "" + position, Toast.LENGTH_SHORT).show();
	        }
	    });
	}
	
	
	
	public String[] getURLs (Long trigid, Cursor c) {
		String url;
		List<String> URLs = new ArrayList<String>();
		
		Double lat = c.getDouble(c.getColumnIndex(TrigDbHelper.TRIG_LAT));
		Double lon = c.getDouble(c.getColumnIndex(TrigDbHelper.TRIG_LON));

		
		url = String.format("%s/%s/%3.5f,%3.5f/%d?key=%s",
				"http://dev.virtualearth.net/REST/v1/Imagery/Map",
				"OrdnanceSurvey",
				lat, lon,
				14,
				"AmX-6eFz_aE2rrhkXUprU3HRV2BNMrCYQoKodIFdfNEcZosjAEbsNetB00GFktP5");
		URLs.add(url);

		url = String.format("%s/%s/%3.5f,%3.5f/%d?key=%s",
				"http://dev.virtualearth.net/REST/v1/Imagery/Map",
				"OrdnanceSurvey",
				lat, lon,
				15,
				"AmX-6eFz_aE2rrhkXUprU3HRV2BNMrCYQoKodIFdfNEcZosjAEbsNetB00GFktP5");
		URLs.add(url);

		url = String.format("%s/%s/%3.5f,%3.5f/%d?key=%s",
				"http://dev.virtualearth.net/REST/v1/Imagery/Map",
				"Aerial",
				lat, lon,
				14,
				"AmX-6eFz_aE2rrhkXUprU3HRV2BNMrCYQoKodIFdfNEcZosjAEbsNetB00GFktP5");
		URLs.add(url);

		url = String.format("%s/%s/%3.5f,%3.5f/%d?key=%s",
				"http://dev.virtualearth.net/REST/v1/Imagery/Map",
				"Aerial",
				lat, lon,
				17,
				"AmX-6eFz_aE2rrhkXUprU3HRV2BNMrCYQoKodIFdfNEcZosjAEbsNetB00GFktP5");
		URLs.add(url);

		url = String.format("%s/%s/%3.5f,%3.5f/%d?key=%s",
				"http://dev.virtualearth.net/REST/v1/Imagery/Map",
				"Aerial",
				lat, lon,
				19,
				"AmX-6eFz_aE2rrhkXUprU3HRV2BNMrCYQoKodIFdfNEcZosjAEbsNetB00GFktP5");
		URLs.add(url);

		
		return URLs.toArray(new String[URLs.size()]);
	}
}
