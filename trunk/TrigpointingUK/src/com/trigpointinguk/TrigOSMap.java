package com.trigpointinguk;

import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;

import android.app.Activity;
import android.app.ProgressDialog;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.widget.ImageView;

public class TrigOSMap extends Activity {
	private long mTrigId;
	private TrigDbHelper mDb;
	private static final String TAG = "TrigOSMap";
	private ImageView mImg;
    private ProgressDialog mProgressDialog;


	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.trigosmap);
		mImg = (ImageView)findViewById(R.id.trigosimage);
		
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
		Double lat = c.getDouble(c.getColumnIndex(TrigDbHelper.TRIG_LAT));
		Double lon = c.getDouble(c.getColumnIndex(TrigDbHelper.TRIG_LON));
		c.close();
		
        new DownloadMapTask().execute(lat, lon);

	}
	
	private class DownloadMapTask extends AsyncTask<Double, Integer, Bitmap> {
		protected Bitmap doInBackground(Double... arg) {
			Double lat = arg[0];
			Double lon = arg[1];
			Bitmap bitmap = null;
			
			String url = String.format("%s/%s/%3.5f,%3.5f/%d?key=%s",
					"http://dev.virtualearth.net/REST/v1/Imagery/Map",
					"OrdnanceSurvey",
					lat, lon,
					15,
					"AmX-6eFz_aE2rrhkXUprU3HRV2BNMrCYQoKodIFdfNEcZosjAEbsNetB00GFktP5");
			Log.i(TAG, url);
			try {
				bitmap = BitmapFactory.decodeStream((InputStream)new URL(url).getContent());
			} catch (MalformedURLException e) {
				e.printStackTrace();
			} catch (IOException e) {
				e.printStackTrace();
			}

			return bitmap;
		}
		protected void onProgressUpdate(Integer... progress) {
		}
		protected void onPostExecute(Bitmap arg0) {
			mImg.setImageBitmap(arg0);
			if (mProgressDialog != null) {mProgressDialog.dismiss();}
		}
		protected void onPreExecute() {
			mProgressDialog = new ProgressDialog(TrigOSMap.this);
			mProgressDialog.setMessage("Loading map image...");
			mProgressDialog.setIndeterminate(false);
			mProgressDialog.setCancelable(true);
			mProgressDialog.show();
		}
	}
	
}
