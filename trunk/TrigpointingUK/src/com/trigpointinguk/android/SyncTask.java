package com.trigpointinguk.android;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.IOException;
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

	
	SyncTask(Context pCtx) {
		this.mCtx = pCtx;
	}
	
	protected Integer doInBackground(Void... arg0) {
		Log.d(TAG, "doInBackground");
		if (isCancelled()){return 0;}
		
		DbHelper db = new DbHelper(mCtx);
        String strLine;                
		int i=0;
		String strUser;
		
		try {
			strUser = URLEncoder.encode(mPrefs.getString("username", ""), "utf8");
		} catch (UnsupportedEncodingException e1) {
			e1.printStackTrace();
			return 0;
		}
		if (strUser.equals("")) {return 0;}
        
		try {
			db.open();
			db.mDb.beginTransaction();
			URL url = new URL("http://www.trigpointinguk.com/trigs/down-android-mylogs.php?username="+strUser);
            URLConnection ucon = url.openConnection();
            InputStream is = ucon.getInputStream();
            GZIPInputStream zis = new GZIPInputStream(new BufferedInputStream(is));
            BufferedReader br = new BufferedReader(new InputStreamReader(zis));
            
            while ((strLine = br.readLine()) != null && !strLine.trim().equals(""))   {
            	Log.i(TAG,strLine);
				String[] csv=strLine.split("\t");
				Condition logged		= Condition.fromLetter(csv[0]);
				int id					= Integer.valueOf(csv[1]);
				db.updateTrigLog(id, logged);
				i++;
            }
			db.mDb.setTransactionSuccessful();
        } catch (Exception e) {
        	Log.d(TAG, "Error: " + e);
        	i=-1;
        } finally {
        	db.mDb.endTransaction();
        	db.close();
        }
        
		return i;
	}
    protected void onPreExecute() {
		Log.d(TAG, "onPreExecute");
		mPrefs = PreferenceManager.getDefaultSharedPreferences(mCtx);

		if (mPrefs.getString("username", "").equals("")) {
			Toast.makeText(mCtx, "Please add username to preferences!", Toast.LENGTH_LONG).show();
			this.cancel(true);
		} else {
			mProgressDialog = new ProgressDialog(mCtx);
			mProgressDialog.setMessage("Syncing with T:UK...");
			mProgressDialog.setIndeterminate(true);
			mProgressDialog.setCancelable(false);
			mProgressDialog.show();
		}
    }
    protected void onProgressUpdate(Integer... progress) {
    }
    protected void onPostExecute(Integer arg0) {
		Log.d(TAG, "onPostExecute " + arg0);
		if (!isCancelled()) {
			if (arg0 >= 0) {
				Toast.makeText(mCtx, "Synced " + arg0 + " logs", Toast.LENGTH_LONG).show();
			} else {
				Toast.makeText(mCtx, "Error retrieving logs", Toast.LENGTH_LONG).show();					
			}
		}
		if (mProgressDialog != null) {mProgressDialog.dismiss();}
    }
}