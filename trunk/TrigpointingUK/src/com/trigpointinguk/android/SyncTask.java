package com.trigpointinguk.android;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLEncoder;
import java.util.zip.GZIPInputStream;

import android.app.ProgressDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.preference.PreferenceManager;
import android.util.Log;
import android.widget.Toast;



public class SyncTask extends AsyncTask<Void, Integer, Integer> {
	public static final String TAG ="SyncTask";
	private Context mCtx;
	private SharedPreferences mPrefs;
    private ProgressDialog mProgressDialog;
    private static boolean mLock = false;

	
	SyncTask(Context pCtx) {
		this.mCtx = pCtx;
	}
	
	protected Integer doInBackground(Void... arg0) {
		Log.d(TAG, "doInBackground");
		if (isCancelled()){return 0;}
		
        String strLine;                
		int i=0;
		String strUser;
		DbHelper db = null;
		
		try {
			strUser = URLEncoder.encode(mPrefs.getString("username", ""), "utf8");
		} catch (UnsupportedEncodingException e1) {
			e1.printStackTrace();
			return 0;
		}
		if (strUser.equals("")) {return 0;}
        
		try {
			URL url = new URL("http://www.trigpointinguk.com/trigs/down-android-mylogs.php?username="+strUser);
			Log.d(TAG, "Getting " + url);
            URLConnection ucon = url.openConnection();
            InputStream is = ucon.getInputStream();
            GZIPInputStream zis = new GZIPInputStream(new BufferedInputStream(is));
            BufferedReader br = new BufferedReader(new InputStreamReader(zis));

    		if (isCancelled()){return 0;}

    		
    		// Make sure only one SyncTask runs at a time
    		if (mLock) {
    			Log.i(TAG, "SyncTask already running");
    			this.cancel(true);
    			return 0;
    		}
    		mLock = true;

    		
    		db = new DbHelper(mCtx);
            db.open();
			db.mDb.beginTransaction();
            
            while ((strLine = br.readLine()) != null && !strLine.trim().equals(""))   {
            	//Log.i(TAG,strLine);
				String[] csv=strLine.split("\t");
				Condition logged		= Condition.fromCode(csv[0]);
				int id					= Integer.valueOf(csv[1]);
				db.updateTrigLog(id, logged);
				i++;
				if (isCancelled()){return 0;}
            }
			db.mDb.setTransactionSuccessful();
        } catch (Exception e) {
        	Log.d(TAG, "Error: " + e);
        	i=-1;
        } finally {
        	if (db != null) {
        		db.mDb.endTransaction();
            	db.close();
        		mLock = false;
        	}
        }
        
		return i;
	}
    protected void onPreExecute() {
		Log.d(TAG, "onPreExecute");
		
		// Check that we have a username, so that we can sync existing logs
		mPrefs = PreferenceManager.getDefaultSharedPreferences(mCtx);
		if (mPrefs.getString("username", "").equals("")) {
			Toast.makeText(mCtx, "Please add username to preferences!", Toast.LENGTH_LONG).show();
			this.cancel(true);
		} else {
			mProgressDialog = new ProgressDialog(mCtx);
			mProgressDialog.setMessage("Syncing with T:UK...");
			mProgressDialog.setIndeterminate(true);
			mProgressDialog.setCancelable(true);
			mProgressDialog.show();
		}
    }
    protected void onProgressUpdate(Integer... progress) {
    }
    protected void onPostExecute(Integer arg0) {
		Log.d(TAG, "onPostExecute " + arg0);
		if (!isCancelled()) {
			if (arg0 >= 0) {
				Toast.makeText(mCtx, "Synced " + arg0 + " logs", Toast.LENGTH_SHORT).show();
			} else {
				Toast.makeText(mCtx, "Error retrieving logs", Toast.LENGTH_SHORT).show();					
			}
		} else {
			Log.d(TAG, "cancelled " + arg0);
			Toast.makeText(mCtx, "Sync cancelled", Toast.LENGTH_SHORT).show();			
		}
		if (mProgressDialog != null) {mProgressDialog.dismiss();}
    }
}