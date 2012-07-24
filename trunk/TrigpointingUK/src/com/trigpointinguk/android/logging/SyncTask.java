package com.trigpointinguk.android.logging;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.GZIPInputStream;

import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.NameValuePair;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.mime.MultipartEntity;
import org.apache.http.entity.mime.content.FileBody;
import org.apache.http.entity.mime.content.StringBody;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.message.BasicNameValuePair;
import org.json.JSONException;
import org.json.JSONObject;

import android.app.ProgressDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager.NameNotFoundException;
import android.database.Cursor;
import android.os.AsyncTask;
import android.preference.PreferenceManager;
import android.util.Log;
import android.widget.Toast;

import com.trigpointinguk.android.DbHelper;
import com.trigpointinguk.android.R;
import com.trigpointinguk.android.common.CountingMultipartEntity;
import com.trigpointinguk.android.common.CountingMultipartEntity.ProgressListener;
import com.trigpointinguk.android.types.Condition;



public class SyncTask extends AsyncTask<Long, Integer, Integer> implements ProgressListener {
	public static final String TAG ="SyncTask";
	private Context 			mCtx;
	private SharedPreferences 	mPrefs;
    private ProgressDialog 		mProgressDialog;

	private	DbHelper 			mDb = null;
	private SyncListener		mSyncListener;
	
    private static boolean 		mLock = false;
    private int 				mAppVersion;
    private int 				mMax;		// Maximum count of things being synced, for progress bar
    private String				mUsername;
    private String				mPassword;
    private String				mErrorMessage;
    
    private int					mActiveByteCount; // byte count from currently transferring photo
    private int					mPreviousByteCount; // byte count from previously transferred photos
    
    private static final int 	MAX 			= 1;
    private static final int 	PROGRESS 		= 2;
    private static final int 	MESSAGE			= 3;
    private static final int 	BLANKPROGRESS	= 4;
    private static final int 	MESSAGECOUNT	= 5;
    
    
    public static final int 	SUCCESS 	= 0;
    public static final int 	NOROWS 		= 1;
    public static final int 	ERROR 		= 2;
    public static final int 	CANCELLED 	= 3;
    
    
    
	public SyncTask(Context pCtx, SyncListener listener) {
		this.mCtx = pCtx;
		this.mSyncListener = listener;
		try {
			mAppVersion = mCtx.getPackageManager().getPackageInfo(mCtx.getPackageName(), 0).versionCode;
		} catch (NameNotFoundException e) {
			Log.e(TAG,"Couldn't get versionCode!");
			mAppVersion = 99999;
		}
	}
	
	
	
	protected Integer doInBackground(Long... trigId) {
		Log.d(TAG, "doInBackground");
		if (isCancelled()){return CANCELLED;}
		
		// Make sure only one SyncTask runs at a time
		if (mLock) {
			Log.i(TAG, "SyncTask already running");
			this.cancel(true);
			return ERROR;
		}
		mLock = true;

		// Open database connection
		mDb = new DbHelper(mCtx);
        mDb.open();
		
		try {
			// Get details from Prefs
			mUsername = mPrefs.getString("username", "");
			mPassword = mPrefs.getString("plaintextpassword", "");
			if (mUsername.equals("")) {return ERROR;}
			if (mPassword.equals("")) {return ERROR;}

			if (ERROR == sendLogsToTUK(trigId)) {
				return ERROR;
			}
			if (ERROR == sendPhotosToTUK(trigId)) {
				return ERROR;
			}
			if (trigId.length == 0) {
				if (ERROR == readLogsFromTUK()) {
					return ERROR;
				}
			}
		} finally {
			mDb.close();
			mLock = false;		
		}

		return SUCCESS;
	}
	
	
	
	
	Integer sendLogsToTUK(Long... trigId) {
		Log.d(TAG, "sendLogsToTUK");
		Long trig_id = null;
		
		if (trigId != null && trigId.length >= 1) {
			trig_id = trigId[0];
		}
		
		publishProgress(MESSAGE, R.string.syncToTUK);
		Cursor c = mDb.fetchLogs(trig_id);
		if (c==null) {
			return NOROWS;
		}
		
		publishProgress(MAX, c.getCount());
		publishProgress(PROGRESS, 0);
		
		int i=0;
		do {
			if (SUCCESS != sendLogToTUK(c)) {
				return ERROR;
			}
			publishProgress(PROGRESS, ++i);
		} while (c.moveToNext());
			
		return SUCCESS;
	}
	
	
	Integer sendPhotosToTUK(Long... trigId) {
		Log.d(TAG, "sendPhotosToTUK");
		Long trig_id = null;
		mPreviousByteCount = 0;
		mActiveByteCount = 0;
		
		if (trigId != null && trigId.length >= 1) {
			trig_id = trigId[0];
		}

		publishProgress(MESSAGE, R.string.syncPhotosToTUK);
		Cursor c = mDb.fetchPhotos(trig_id);
		if (c==null) {
			return NOROWS;
		}
		
		// whiz through the cursor, totalling file sizes 
		int totalBytes=0;
		do {
			totalBytes += new File(c.getString(c.getColumnIndex(DbHelper.PHOTO_PHOTO))).length();
		} while (c.moveToNext());
		// reset cursor
		c.moveToFirst();
		
		publishProgress(MAX, totalBytes);
		publishProgress(PROGRESS, 0);
		
		int i = 1;
		do {
			publishProgress(MESSAGECOUNT, i++, c.getCount());
			if (SUCCESS != sendPhotoToTUK(c)) {
				return ERROR;
			}
			mPreviousByteCount += mActiveByteCount;
			mActiveByteCount = 0;
		} while (c.moveToNext());
			
		return SUCCESS;
	}
	
	
	
	Integer sendLogToTUK(Cursor c) {
		Log.i(TAG, "sendLogToTUK");
		
		long trigId = c.getInt(c.getColumnIndex(DbHelper.LOG_ID));
		
		// Set up post variables
	    List <NameValuePair> nameValuePairs = new ArrayList <NameValuePair> (10);
	    nameValuePairs.add(new BasicNameValuePair("username"	, mUsername));
	    nameValuePairs.add(new BasicNameValuePair("password"	, mPassword));
	    nameValuePairs.add(new BasicNameValuePair("id"			, c.getString(c.getColumnIndex(DbHelper.LOG_ID))));
    	nameValuePairs.add(new BasicNameValuePair("year"		, c.getString(c.getColumnIndex(DbHelper.LOG_YEAR))));
    	nameValuePairs.add(new BasicNameValuePair("month"		, c.getString(c.getColumnIndex(DbHelper.LOG_MONTH))));
    	nameValuePairs.add(new BasicNameValuePair("day"			, c.getString(c.getColumnIndex(DbHelper.LOG_DAY))));
    	nameValuePairs.add(new BasicNameValuePair("hour"		, c.getString(c.getColumnIndex(DbHelper.LOG_HOUR))));
    	nameValuePairs.add(new BasicNameValuePair("minutes"		, c.getString(c.getColumnIndex(DbHelper.LOG_MINUTES))));
    	nameValuePairs.add(new BasicNameValuePair("comment"		, c.getString(c.getColumnIndex(DbHelper.LOG_COMMENT))));
    	nameValuePairs.add(new BasicNameValuePair("gridref"		, c.getString(c.getColumnIndex(DbHelper.LOG_GRIDREF))));
    	nameValuePairs.add(new BasicNameValuePair("fb"			, c.getString(c.getColumnIndex(DbHelper.LOG_FB))));
    	nameValuePairs.add(new BasicNameValuePair("adminflag"	, c.getString(c.getColumnIndex(DbHelper.LOG_FLAGADMINS))));
    	nameValuePairs.add(new BasicNameValuePair("userflag"	, c.getString(c.getColumnIndex(DbHelper.LOG_FLAGUSERS))));
    	nameValuePairs.add(new BasicNameValuePair("score"		, c.getString(c.getColumnIndex(DbHelper.LOG_SCORE))));
    	nameValuePairs.add(new BasicNameValuePair("condition"	, c.getString(c.getColumnIndex(DbHelper.LOG_CONDITION))));
    	nameValuePairs.add(new BasicNameValuePair("appversion"  , String.valueOf(mAppVersion)));
	    
		try {
			// Send the request
			HttpClient client = new DefaultHttpClient();
			HttpPost post = new HttpPost( "http://www.trigpointinguk.com/trigs/android-sync-log.php" );
		    post.setEntity(new UrlEncodedFormEntity(nameValuePairs));
		    Log.d(TAG, nameValuePairs.toString());
		    HttpResponse response = client.execute(post);
		    
		    // Get the response
		    InputStream ips  = response.getEntity().getContent();
	        BufferedReader buf = new BufferedReader(new InputStreamReader(ips,"UTF-8"));
	        
	        // Handle HTTP errors
	        if(response.getStatusLine().getStatusCode()!=HttpStatus.SC_OK) {
	            Log.e(TAG, "RC error - " + response.getStatusLine().getReasonPhrase());
	            return ERROR;
	        }

	        // Single line JSON response from T:UK server
	        String reply = buf.readLine();
	        buf.close();
	        ips.close();
	        Log.d(TAG, "Reply from T:UK - " + reply);
	        if (reply == null || reply == "") {
	        	Log.e(TAG, "No response received from T:UK");
	        	return ERROR;
	        }

	        // Parse the JSON response
	        try {
				JSONObject jo = new JSONObject(reply);
				int status = jo.getInt("status");
				mErrorMessage = jo.getString("msg");
				Log.i(TAG, "Status=" + status + ", msg=" + mErrorMessage);
				
				if (status != 0) {
					return ERROR;
				}
				int logId = jo.getInt("log_id");
				Log.i(TAG, "Successfully inserted log into T:UK - " + logId);
				// remove log from database
				mDb.deleteLog(trigId);
				// update photos for this trig with log id from T:UK
				mDb.updatePhotos(trigId, logId);
				// update local logged condition
				mDb.updateTrigLog(trigId, Condition.fromCode(c.getString(c.getColumnIndex(DbHelper.LOG_CONDITION))));
			} catch (JSONException e1) {
				e1.printStackTrace();
				return ERROR;
			}

		} catch (ClientProtocolException e) {
            return ERROR;
		} catch (IOException e) {
            return ERROR;
		} catch (NumberFormatException e) {
			Log.e(TAG, "Unable to convert log_id received from T:UK into integer");
			return ERROR;
		}

		return SUCCESS;
	}
	
	
	
	Integer sendPhotoToTUK(Cursor c) {
		Log.i(TAG, "sendPhotoToTUK");
		
		Long	photoId 	= c.getLong  (c.getColumnIndex(DbHelper.PHOTO_ID));
    	String 	photoPath 	= c.getString(c.getColumnIndex(DbHelper.PHOTO_PHOTO));
    	String 	thumbPath 	= c.getString(c.getColumnIndex(DbHelper.PHOTO_ICON));
		
		try {
			// Send the request
			HttpClient client = new DefaultHttpClient();
			HttpPost post = new HttpPost( "http://www.trigpointinguk.com/trigs/android-sync-photo.php" );
		    
		    //MultipartEntity entity = new MultipartEntity(HttpMultipartMode.BROWSER_COMPATIBLE);
			MultipartEntity entity = new CountingMultipartEntity(this);
    	    entity.addPart("username"	, new StringBody(mUsername));
    	    entity.addPart("password"	, new StringBody(mPassword));
    	    entity.addPart("photoid"	, new StringBody(photoId.toString()));
    	    entity.addPart("tlog_id"	, new StringBody(c.getString(c.getColumnIndex(DbHelper.PHOTO_TUKLOGID))));
    	    entity.addPart("trig"		, new StringBody(c.getString(c.getColumnIndex(DbHelper.PHOTO_TRIG))));
    	    entity.addPart("name"		, new StringBody(c.getString(c.getColumnIndex(DbHelper.PHOTO_NAME))));
    	    entity.addPart("descr"		, new StringBody(c.getString(c.getColumnIndex(DbHelper.PHOTO_DESCR))));
    	    entity.addPart("subject"	, new StringBody(c.getString(c.getColumnIndex(DbHelper.PHOTO_SUBJECT))));
    	    entity.addPart("ispublic"	, new StringBody(c.getString(c.getColumnIndex(DbHelper.PHOTO_ISPUBLIC))));
            entity.addPart("photo"		, new FileBody(new File (photoPath), "image/jpeg"));
        	entity.addPart("appversion" , new StringBody(String.valueOf(mAppVersion)));


            post.setEntity(entity);
            HttpResponse response = client.execute(post);
            
		    // Get the response
		    InputStream ips  = response.getEntity().getContent();
	        BufferedReader buf = new BufferedReader(new InputStreamReader(ips,"UTF-8"));
	        
	        // Handle HTTP errors
	        if(response.getStatusLine().getStatusCode()!=HttpStatus.SC_OK) {
	            Log.e(TAG, "RC error - " + response.getStatusLine().getReasonPhrase());
	            return ERROR;
	        }

	        // Single line JSON response from T:UK server
	        String reply = buf.readLine();
	        buf.close();
	        ips.close();
	        Log.i(TAG, "Reply from T:UK - " + reply);
	        if (reply == null || reply == "") {
	        	Log.e(TAG, "No response received from T:UK");
	        	return ERROR;
	        }

	        // Parse the JSON response
	        try {
				JSONObject jo = new JSONObject(reply);
				int status = jo.getInt("status");
				mErrorMessage = jo.getString("msg");
				Log.i(TAG, "Status=" + status + ", msg=" + mErrorMessage);
				if (status != 0) {
					return ERROR;
				}
				int tukPhotoId = jo.getInt("photo_id");
				Log.i(TAG, "Successfully uploaded photo to T:UK - " + tukPhotoId);
				// remove log from database
				mDb.deletePhoto(photoId);
				// remove files from cachedir
				new File (photoPath).delete();
				new File (thumbPath).delete();
	        } catch (JSONException e1) {
				e1.printStackTrace();
				return ERROR;
			}

		} catch (ClientProtocolException e) {
            return ERROR;
		} catch (IOException e) {
            return ERROR;
		} catch (NumberFormatException e) {
			Log.e(TAG, "Unable to convert tukPhotoId received from T:UK into integer");
			return ERROR;
		}

		return SUCCESS;
	}
	
	@Override
	public void transferred(long num) {
		mActiveByteCount = (int) num;
		publishProgress(PROGRESS, mPreviousByteCount + mActiveByteCount);
		Log.d(TAG, "Transferred bytes: " + num);
	}	
	
	
	
	
	
	
	
	
	
	
	
	
	
	
	
	
	
	
	
	
	
	
	
	
	Integer readLogsFromTUK() {
		Log.d(TAG, "readLogsFromTUK");

        String strLine;                
		int i=0;
		
		try {
			publishProgress(BLANKPROGRESS);
			publishProgress(MESSAGE, R.string.syncLogsFromTUK);
			URL url = new URL("http://www.trigpointinguk.com/trigs/down-android-mylogs.php?username="+URLEncoder.encode(mUsername)+"&appversion="+mAppVersion);
			Log.d(TAG, "Getting " + url);
            URLConnection ucon = url.openConnection();
            InputStream is = ucon.getInputStream();
            GZIPInputStream zis = new GZIPInputStream(new BufferedInputStream(is));
            BufferedReader br = new BufferedReader(new InputStreamReader(zis));

			mDb.mDb.beginTransaction();
            
			// first record contains log count
			if ((strLine=br.readLine()) != null) {
				mMax = Integer.parseInt(strLine);
				Log.i(TAG, "Log count from TUK = " + mMax);
				publishProgress(MAX, mMax);
			}
			// read log records records
            while ((strLine = br.readLine()) != null && !strLine.trim().equals(""))   {
            	//Log.i(TAG,strLine);
				String[] csv=strLine.split("\t");
				Condition logged		= Condition.fromCode(csv[0]);
				int id					= Integer.valueOf(csv[1]);
				mDb.updateTrigLog(id, logged);
				i++;
				if (isCancelled()){return CANCELLED;}
				publishProgress(PROGRESS, i);
            }
			mDb.mDb.setTransactionSuccessful();
        } catch (Exception e) {
        	Log.d(TAG, "Error: " + e);
        	i=-1;
        	mErrorMessage = e.getMessage();
        	return ERROR;
        } finally {
        	if (mDb != null && mDb.mDb.inTransaction()) {
        		mDb.mDb.endTransaction();
        	}
        }
        
		return SUCCESS;		
	}
	
	
	
	
	
	
	
	
	
	
	
	
	
	
	
	
    protected void onPreExecute() {
		Log.d(TAG, "onPreExecute");
		
		// Check that we have a username and password, so that we can sync existing logs
		mPrefs = PreferenceManager.getDefaultSharedPreferences(mCtx);
		if (mPrefs.getString("username", "").equals("")) {
			Toast.makeText(mCtx, R.string.toastAddUsername, Toast.LENGTH_LONG).show();
			this.cancel(true);
			return;
		} 
		if (mPrefs.getString("plaintextpassword", "").equals("")) {
			Toast.makeText(mCtx, R.string.toastAddPassword, Toast.LENGTH_LONG).show();
			this.cancel(true);
			return;
		} 
		
		mProgressDialog = new ProgressDialog(mCtx);
		mProgressDialog.setIndeterminate(false);
		mProgressDialog.setCancelable(true);
		mProgressDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
		mProgressDialog.setMessage("Connecting to T:UK");
		mProgressDialog.show();

		mErrorMessage = "";
    }
    
    protected void onProgressUpdate(Integer... progress) {
    	String message;
    	switch (progress[0]) {
    	case MAX:
        	Log.d(TAG, "Max progress set to " + progress[1]);
        	mProgressDialog.setIndeterminate(false);
        	mProgressDialog.setMax(progress[1]);
        	mProgressDialog.setProgress(0);
    		break;
    	case PROGRESS:
        	Log.d(TAG, "Progress set to " + progress[1]);
        	mProgressDialog.setProgress(progress[1]);
        	break;
    	case MESSAGE:
    		message = mCtx.getResources().getString(progress[1]);
    		Log.d(TAG, "Progress message set to " + message);
    		mProgressDialog.setMessage(message);
    		break;
    	case MESSAGECOUNT:
    		message = "Uploading photo " + progress[1] + " of " + progress[2];
    		Log.d(TAG, "Progress message set to " + message);
    		mProgressDialog.setMessage(message);
    		break;
    	case BLANKPROGRESS:
    		mProgressDialog.setMax(1);
    		break;
    	}
    }
    
    
    protected void onPostExecute(Integer status) {
		Log.d(TAG, "onPostExecute " + status);
		if (!isCancelled()) {
			if (status == SUCCESS) {
				Toast.makeText(mCtx, "Synced with TrigpointingUK", Toast.LENGTH_SHORT).show();
			} else {
				Toast.makeText(mCtx, "Error syncing with TrigpointingUK - " + mErrorMessage, Toast.LENGTH_LONG).show();					
			}
		} else {
			Log.d(TAG, "cancelled ");
			Toast.makeText(mCtx, "Sync cancelled", Toast.LENGTH_SHORT).show();			
		}
		if (mProgressDialog != null) {mProgressDialog.dismiss();}
		if (mSyncListener != null) {
			mSyncListener.onSynced(status);
		}
    }




}