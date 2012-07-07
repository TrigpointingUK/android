package com.trigpointinguk.android;

import android.app.ProgressDialog;
import android.content.Context;
import android.os.AsyncTask;
import android.util.Log;
import android.widget.Toast;

import com.trigpointinguk.android.common.FileCache;


public class ClearCacheTask extends AsyncTask<Void, Integer, Integer> {
	public static final String TAG ="ClearCacheTask";
	private Context mCtx;
    private ProgressDialog mProgressDialog;

	
    ClearCacheTask(Context pCtx) {
		this.mCtx = pCtx;
	}
	
	protected Integer doInBackground(Void... arg0) {
		Log.d(TAG, "doInBackground");
		FileCache cache;
		int c = 0;
		
		cache = new FileCache(mCtx, "bitmaps");
		c += cache.clear();
		if (isCancelled()){return 0;}
		
		cache = new FileCache(mCtx, "images");
		c += cache.clear();
		if (isCancelled()){return 0;}
		
		cache = new FileCache(mCtx, "strings");
		c += cache.clear();
		if (isCancelled()){return 0;}
		
		return c;
	}
    protected void onPreExecute() {
		Log.d(TAG, "onPreExecute");

		mProgressDialog = new ProgressDialog(mCtx);
		mProgressDialog.setMessage("Clearing cache");
		mProgressDialog.setIndeterminate(true);
		mProgressDialog.setCancelable(false);
		mProgressDialog.show();
    }
    protected void onProgressUpdate(Integer... progress) {
    }
    protected void onPostExecute(Integer arg0) {
		Log.d(TAG, "onPostExecute " + arg0);
		if (!isCancelled()) {
			Toast.makeText(mCtx, "Cleared "+arg0+" cache files", Toast.LENGTH_LONG).show();
		} else {
			Toast.makeText(mCtx, "Cancelled!", Toast.LENGTH_LONG).show();					
		}
		if (mProgressDialog != null) {mProgressDialog.dismiss();}
    }
}