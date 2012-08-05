package com.trigpointinguk.android;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.GZIPInputStream;

import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.client.HttpClient;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.message.BasicNameValuePair;

import com.trigpointinguk.android.types.Condition;
import com.trigpointinguk.android.types.Trig;

import android.app.Activity;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.content.pm.PackageManager.NameNotFoundException;
import android.os.AsyncTask;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;

public class DownloadTrigsActivity extends Activity {

	private TextView 		mStatus;
	private ProgressBar 	mProgress;
	private Button 			mDownloadBtn;
	private Integer 		mDownloadCount = 0;
	private boolean 		mRunning = false;
	private static int 		mProgressMax = 10000; // value unimportant
	private int 			mAppVersion;
	
	private static final String TAG = "DownloadTrigsActivity";
	private enum DownloadStatus {OK, CANCELLED, ERROR};
	private enum ProgressType 	{UPDATE, NEWMAX};
	private AsyncTask<Void, ProgressType, DownloadStatus> mTask;

	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.download);
		
		try {
			mAppVersion = this.getPackageManager().getPackageInfo(this.getPackageName(), 0).versionCode;
		} catch (NameNotFoundException e) {
			Log.e(TAG,"Couldn't get versionCode!");
			mAppVersion = 99999;
		}		
		
		mStatus = (TextView) findViewById(R.id.downloadStatus);
		mStatus.setText(R.string.downloadIdleStatus);
		mProgress = (ProgressBar) findViewById(R.id.downloadProgress);
		mProgress.setMax(mProgressMax);
		mProgress.setProgress(0);
		mDownloadBtn = (Button) findViewById(R.id.btnDownload);

		mDownloadBtn.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View arg0) {
				if (mRunning == false) {
					mTask = new PopulateTrigsTask().execute();
				} else {
					mTask.cancel(true);
				}
			}
		});       

	}


	private class PopulateTrigsTask extends AsyncTask<Void, ProgressType, DownloadStatus> {
		@Override
		protected DownloadStatus doInBackground(Void... arg0) {

			DbHelper db = new DbHelper(DownloadTrigsActivity.this);
			String strLine;                
			int i=0;

			try {
				db.open();
				db.mDb.beginTransaction();

				URL url = new URL("http://www.trigpointinguk.com/trigs/down-android-trigs.php?appversion="+String.valueOf(mAppVersion));
				URLConnection ucon = url.openConnection();
				InputStream is = ucon.getInputStream();
				GZIPInputStream zis = new GZIPInputStream(new BufferedInputStream(is));
				BufferedReader br = new BufferedReader(new InputStreamReader(zis));

				if ((strLine = br.readLine()) != null) {
					mProgressMax = Integer.valueOf(strLine);
					Log.i(TAG, "Downloading " + mProgressMax + " trigs");
					publishProgress(ProgressType.NEWMAX);
				}
				
				db.deleteAll();

				while ((strLine = br.readLine()) != null && !strLine.trim().equals(""))   {
					//Log.v(TAG,strLine);
					String[] csv=strLine.split("\t");
					int id						= Integer.valueOf(csv[0]);
					String waypoint				= csv[1];
					String name					= csv[2];
					double lat					= Double.valueOf(csv[3]);
					double lon					= Double.valueOf(csv[4]);
					Trig.Physical type			= Trig.Physical.fromCode(csv[5]);
					String fb					= csv[6];
					Condition condition			= Condition.fromCode(csv[7]);
					Condition logged			= Condition.NOTLOGGED;
					Trig.Current current		= Trig.Current.fromCode(csv[8]);
					Trig.Historic historic		= Trig.Historic.fromCode(csv[9]);
					db.createTrig(id, name, waypoint, lat, lon, type, condition, logged, current, historic, fb);
					if (i++%10==9){
						mDownloadCount=i;
						if (isCancelled()) {
							return DownloadStatus.CANCELLED;
						} else {
							publishProgress(ProgressType.UPDATE);
						}
					}
				} 
				db.mDb.execSQL("create index if not exists latlon on trig (lat, lon)");
				db.mDb.setTransactionSuccessful();
			}catch (IOException e) {
				Log.e(TAG, "Error: " + e);
				return DownloadStatus.ERROR;
			} finally {
				db.mDb.endTransaction();
				db.close();
				mDownloadCount = i;
			}
			return DownloadStatus.OK;
		}
		@Override
		protected void onPreExecute() {
			mDownloadCount = 0;
			mStatus.setText(R.string.downloadInsertStatus);
			mDownloadBtn.setText(R.string.btnCancel);
			mRunning = true;
		}
		@Override
		protected void onProgressUpdate(ProgressType... progress) {
			switch (progress[0]) {
			case UPDATE:
				mProgress.setProgress(mDownloadCount);
				mStatus.setText("Inserted " + mDownloadCount + " trigs");
				break;
			case NEWMAX:
				mProgress.setMax(mProgressMax);
				break;
			}
		}
		@Override
		protected void onPostExecute(DownloadStatus arg0) {
			switch (arg0) {
			case OK:
				mStatus.setText("Finished downloading " + mDownloadCount +" trigs");
				mDownloadBtn.setText(R.string.btnDownloadFinished);
				mProgress.setProgress(mProgressMax);
				mDownloadBtn.setOnClickListener(new OnClickListener() {
					@Override
					public void onClick(View arg0) {
							DownloadTrigsActivity.this.finish();
					}
				});       
				break;
			case ERROR:
				mStatus.setText("Download failed!");
				mDownloadBtn.setText(R.string.btnDownload);
				mProgress.setProgress(0);
				break;
			case CANCELLED:
				mStatus.setText("Download Cancelled!");
				mDownloadBtn.setText(R.string.btnDownload);
				mProgress.setProgress(0);
				break;
			}
			mRunning = false;
		}
		@Override
		protected void onCancelled() {
			mProgress.setProgress(0);
			mStatus.setText("Download Cancelled");
			mDownloadBtn.setText(R.string.btnDownload);
			mRunning = false;
			super.onCancelled();
		}

	}
}
