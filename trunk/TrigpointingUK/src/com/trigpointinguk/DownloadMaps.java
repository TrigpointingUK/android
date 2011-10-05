package com.trigpointinguk;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;
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
	private int mDownloadCount;
	private boolean mRunning=false;
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
				if (!mRunning) {
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
			int i=0; // not using mDownloadcount in loop for performance reasons

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
							i++;
						} 
						if (i % 10 == 0) {
							if (isCancelled()) {
								zis.close();
								mDownloadCount=i;
								return 4;
							} else {
								publishProgress(i);
							}
						}
					} 
					zis.close(); 
					mDownloadCount=i;
				} 
				catch (ZipException e) {
					Log.w(TAG, "Error: " + e);
					mDownloadCount = i;
					return 1;					
				}
				catch (FileNotFoundException e) {
					Log.w(TAG, "Error: " + e);
					mDownloadCount = i;
					return 3;										
				}
				catch (IOException e) {
					Log.w(TAG, "Error: " + e);
					Log.w(TAG, "Message: " + e.getMessage());
					Log.w(TAG, "Cause: " + e.getCause());
					mDownloadCount = i;
					if (e.getMessage().equals("No space left on device")) {
						return 5;
					}
					return 2;
				}
			}
			return 0;
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
			mDownloadCount = 0;
			mRunning = true;
		}
		@Override
		protected void onProgressUpdate(Integer... progress) {
			mProgress.setProgress(progress[0]);
			mStatus.setText("Downloaded " + progress[0] + " tiles");
		}
		@Override
		protected void onPostExecute(Integer arg0) {
			mProgress.setProgress(mDownloadCount);
			switch (arg0) {
			case 0:
				mStatus.setText("Finished downloading " + mDownloadCount +" tiles");
				break;
			case 1:
				mStatus.setText("Error - corrupt file! " + mDownloadCount +" tiles");
				break;
			case 3:
				mStatus.setText("Error - couldn't save! " + mDownloadCount +" tiles");
				break;
			case 4:
				mStatus.setText("Download Cancelled! " + mDownloadCount +" tiles");
				break;
			case 5:
				mStatus.setText("Error - out of space! " + mDownloadCount +" tiles");
				break;
			default:
				mStatus.setText("Error downloading! " + mDownloadCount + " tiles");
			}
			mDownloadBtn.setText(R.string.btnDownload);
			mTileSource.setEnabled(true);
			mRunning = false;
		}
		@Override
		protected void onCancelled() {
			mProgress.setProgress(mDownloadCount);
			mStatus.setText("Download Cancelled");
			mDownloadBtn.setText(R.string.btnDownload);
			mTileSource.setEnabled(true);
			mRunning = false;
			super.onCancelled();
		}

	}

}
