package com.trigpointinguk;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import android.app.Activity;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.TextView;

public class DownloadMaps extends Activity {
	private TextView mStatus;
	private ProgressBar mProgress;
	private Button mDownloadBtn;
	private static final int mProgressMax = 27000;
	private static final String TAG = "DownloadMaps";
	private AsyncTask <String, Integer, Integer> mTask;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.mapdownload);

		mStatus = (TextView) findViewById(R.id.downloadMapsStatus);
		mStatus.setText(R.string.downloadMapsIdleStatus);
		mProgress = (ProgressBar) findViewById(R.id.downloadMapsProgress);
		mProgress.setMax(mProgressMax);
		mProgress.setProgress(0);
		mDownloadBtn = (Button) findViewById(R.id.btnDownloadMaps);

		mDownloadBtn.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View arg0) {
				if (mTask == null) {
					final Spinner tileSource = (Spinner) findViewById(R.id.downloadMapsTileSource);  
					final int tilePos = tileSource.getSelectedItemPosition();
					final String tileURL = getResources().getStringArray(R.array.downloadMapsArrayValues)[tilePos];
					Log.i(TAG, tileURL);
					mTask = new PopulateMapsTask().execute(tileURL);
					mDownloadBtn.setText(R.string.btnCancel);
				} else {
					mTask.cancel(true);
					mTask = null;
					mDownloadBtn.setText(R.string.btnDownload);
				}
			}
		});       
	}

	
	


	@Override
	protected void onDestroy() {
		Log.i(TAG, "Destroyed");
		if (mTask != null) {
			mTask.cancel(true);
		}
		super.onDestroy();
	}





	private class PopulateMapsTask extends AsyncTask<String, Integer, Integer> {
		protected Integer doInBackground(String... arg) {
			String cacheDir = "/sdcard/osmdroid/tiles/";
			int i=0;

			try {
				URL url = new URL(arg[0]);
				URLConnection ucon = url.openConnection();
				InputStream is = ucon.getInputStream();
				ZipInputStream zis = new ZipInputStream(is); 
				ZipEntry ze; 

				byte[] buffer = new byte[1024];
				int length;

				while ((ze = zis.getNextEntry()) != null) { 
					Log.v(TAG, "Unzipping " + ze.getName()); 

					if(ze.isDirectory()) { 
						dirChecker(cacheDir + ze.getName()); 
					} else {
						FileOutputStream fout = new FileOutputStream(cacheDir + ze.getName()); 
						while ((length = zis.read(buffer))>0) {
							fout.write(buffer, 0, length);
						}			 
						zis.closeEntry(); 
						fout.close(); 
					} 
					if (++i % 10 == 0) {publishProgress(i);}
					if (isCancelled()) {
						zis.close();
						return i;
					}
				} 
				zis.close(); 
			} catch (IOException e) {
				Log.d(TAG, "Error: " + e);
			}
			return i;
		}
		private void dirChecker(String dir) { 
			File f = new File(dir); 
			if(!f.isDirectory()) { 
				f.mkdirs(); 
			} 
		} 

		@Override
		protected void onProgressUpdate(Integer... progress) {
			mProgress.setProgress(progress[0]);
			mStatus.setText("Downloaded " + progress[0] + " tiles");
		}
		@Override
		protected void onPostExecute(Integer arg0) {
			mProgress.setProgress(mProgressMax);
			mStatus.setText("Finished downloading " + arg0 +" tiles");
		}
		@Override
		protected void onPreExecute() {
			mStatus.setText(R.string.downloadMapsInsertStatus);
		}
		@Override
		protected void onCancelled() {
			mProgress.setProgress(0);
			mStatus.setText("Download Cancelled");
			super.onCancelled();
		}

	}

}
