package com.trigpointinguk.android;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.net.URLConnection;
import java.util.zip.GZIPInputStream;

import com.trigpointinguk.android.types.Condition;
import com.trigpointinguk.android.types.Trig;

import android.app.Activity;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;

public class DownloadTrigsActivity extends Activity {

	private TextView mStatus;
	private ProgressBar mProgress;
	private Button mDownloadBtn;
	private Integer mDownloadCount = 0;
	private boolean mRunning = false;
	private static int mProgressMax;
	private static final String TAG = "DownloadTrigsActivity";
	private enum DownloadStatus {OK, CANCELLED, ERROR};
	private AsyncTask <Void, Integer, DownloadStatus> mTask;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.download);
		
		mProgressMax=Integer.parseInt(getResources().getString(R.string.downloadMaxTrigs));
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


	private class PopulateTrigsTask extends AsyncTask<Void, Integer, DownloadStatus> {
		@Override
		protected DownloadStatus doInBackground(Void... arg0) {

			DbHelper db = new DbHelper(DownloadTrigsActivity.this);
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
						if (isCancelled()) {
							mDownloadCount=i;
							return DownloadStatus.CANCELLED;
						} else {
							publishProgress(i);
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
		protected void onProgressUpdate(Integer... progress) {
			mProgress.setProgress(progress[0]);
			mStatus.setText("Inserted " + progress[0] + " trigs");
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
