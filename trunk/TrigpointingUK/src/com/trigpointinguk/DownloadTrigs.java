package com.trigpointinguk;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.net.URLConnection;
import java.util.zip.GZIPInputStream;

import android.app.Activity;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;

public class DownloadTrigs extends Activity {

	private TextView mStatus;
	private ProgressBar mProgress;
	private Button mDownloadBtn;
	private static final int mProgressMax = 7800;
	private static final String TAG = "DownloadTrigs";
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.download);
	
		mStatus = (TextView) findViewById(R.id.downloadStatus);
		mStatus.setText(R.string.downloadIdleStatus);
		mProgress = (ProgressBar) findViewById(R.id.downloadProgress);
		mProgress.setMax(mProgressMax);
		mProgress.setProgress(0);
		mDownloadBtn = (Button) findViewById(R.id.btnDownload);
		
		mDownloadBtn.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View arg0) {
		        mDownloadBtn.setClickable(false);
		        mDownloadBtn.setEnabled(false);
		        new PopulateTrigsTask().execute();
			}
		});       

	}


	private class PopulateTrigsTask extends AsyncTask<Void, Integer, Integer> {
		protected Integer doInBackground(Void... arg0) {

			TrigDbHelper db = new TrigDbHelper(DownloadTrigs.this);
			String strLine;                
			int i=0;

			try {
				db.open();
				db.mDb.beginTransaction();

				URL url = new URL("http://www.trigpointinguk.com/trigs/down-android-trigs.php");
				URLConnection ucon = url.openConnection();
				InputStream is = ucon.getInputStream();
				GZIPInputStream zis = new GZIPInputStream(new BufferedInputStream(is));
				BufferedReader br = new BufferedReader(new InputStreamReader(zis));

				db.deleteAll();

				while ((strLine = br.readLine()) != null && !strLine.trim().equals(""))   {
					Log.i(TAG,strLine);
					String[] csv=strLine.split("\t");
					int id			= Integer.valueOf(csv[0]);
					String waypoint	= csv[1];
					String name		= csv[2];
					double lat		= Double.valueOf(csv[3]);
					double lon		= Double.valueOf(csv[4]);
					int type		= Integer.valueOf(csv[5]);
					String fb		= csv[6];
					int condition	= Integer.valueOf(csv[7]);
					int logged		= Trig.CONDITION_N_NOTLOGGED;
					int current		= Integer.valueOf(csv[8]);
					int historic	= Integer.valueOf(csv[9]);
					db.createTrig(id, name, waypoint, lat, lon, type, condition, logged, current, historic, fb);
					if (i++%10==9){publishProgress(i);}
				}
				db.mDb.execSQL("create index if not exists latlon on trig (lat, lon)");
				db.mDb.setTransactionSuccessful();
			} catch (IOException e) {
				Log.d(TAG, "Error: " + e);
			} finally {
				db.mDb.endTransaction();
				db.close();
			}

			return i;
		}
		protected void onProgressUpdate(Integer... progress) {
			mProgress.setProgress(progress[0]);
			mStatus.setText("Inserted " + progress[0] + " trigs");
		}
		protected void onPostExecute(Integer arg0) {
			mProgress.setProgress(mProgressMax);
			mStatus.setText("Finished inserting " + arg0 +" trigs");
		}
		protected void onPreExecute() {
			mStatus.setText(R.string.downloadInsertStatus);
		}
	}
}
