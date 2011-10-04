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
	private Spinner mTileSource;
	private int mProgressMax=100;
	private static final String TAG = "DownloadMaps";
	private AsyncTask <String, Integer, Integer> mTask;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.mapdownload);

		mStatus 		= (TextView)	findViewById(R.id.downloadMapsStatus);
		mProgress 		= (ProgressBar)	findViewById(R.id.downloadMapsProgress);
		mDownloadBtn	= (Button)		findViewById(R.id.btnDownloadMaps);
		mTileSource 	= (Spinner)		findViewById(R.id.downloadMapsTileSource);  

		mStatus.setText(R.string.downloadMapsIdleStatus);
		mProgress.setProgress(0);

		mDownloadBtn.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View arg0) {
				if (mTask == null) {
					// find which item is selected
					final int tilePos = mTileSource.getSelectedItemPosition();
					// get the list of URLs
					final String tileURL = getResources().getStringArray(R.array.downloadMapsArrayValues)[tilePos];
					// get the number of tiles
					mProgressMax = getResources().getIntArray(R.array.downloadMapsArrayTiles)[tilePos];
					Log.i(TAG, tileURL);
					Log.i(TAG, mProgressMax + " Tiles");
					mTask = new PopulateMapsTask().execute(tileURL);
				} else {
					mTask.cancel(true);
					mTask = null;
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

			String[] files = arg[0].split(",");
			for (String file : files) {
				try {
					Log.i(TAG, "Getting : " + file);
					URL url = new URL(file);
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
						if (++i % 10 == 0) {
							if (isCancelled()) {
								zis.close();
								return i;
							} else {
								publishProgress(i);
							}
						}
					} 
					zis.close(); 
				} catch (IOException e) {
					Log.w(TAG, "Error: " + e);
				}
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
		protected void onPreExecute() {
			mStatus.setText(R.string.downloadMapsInsertStatus);
			mDownloadBtn.setText(R.string.btnCancel);
			mTileSource.setEnabled(false);
			mProgress.setMax(mProgressMax);
			mProgress.setProgress(0);
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
			mDownloadBtn.setText(R.string.btnDownload);
			mTileSource.setEnabled(true);
		}
		@Override
		protected void onCancelled() {
			mProgress.setProgress(0);
			mStatus.setText("Download Cancelled");
			mDownloadBtn.setText(R.string.btnDownload);
			mTileSource.setEnabled(true);
			super.onCancelled();
		}

	}

}
