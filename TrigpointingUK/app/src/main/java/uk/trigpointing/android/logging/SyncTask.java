package uk.trigpointing.android.logging;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLEncoder;
import java.util.zip.GZIPInputStream;
import java.nio.charset.StandardCharsets;
import org.json.JSONException;
import org.json.JSONObject;

import androidx.appcompat.app.AlertDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager.NameNotFoundException;
import android.database.Cursor;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import android.os.Handler;
import android.os.Looper;
import androidx.preference.PreferenceManager;
import android.util.Log;
import android.widget.Toast;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.LinearLayout;
import android.view.ViewGroup;
import android.util.TypedValue;

import uk.trigpointing.android.DbHelper;
import uk.trigpointing.android.R;
import uk.trigpointing.android.common.CountingMultipartEntity.ProgressListener;
import uk.trigpointing.android.common.ProgressRequestBody;
import uk.trigpointing.android.api.TrigApiClient;
import uk.trigpointing.android.api.AuthPreferences;
import okhttp3.FormBody;
import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import uk.trigpointing.android.types.Condition;



public class SyncTask implements ProgressListener {
    public static final String TAG ="SyncTask";
    private Context             mCtx;
    private SharedPreferences     mPrefs;
    private AlertDialog         progressDialog;
    private ProgressBar         progressBar;
    private TextView           progressText;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private boolean mIsAutoSyncAfterDownload = false;
    
    private void updateProgress(int type, int... values) {
        mainHandler.post(() -> {
            String message;
            if (progressDialog == null) {
                showDialog("");
            }
            switch (type) {
            case MAX:
                if (progressBar != null) {
                    progressBar.setIndeterminate(false);
                    progressBar.setMax(values[0]);
                    progressBar.setProgress(0);
                }
                break;
            case PROGRESS:
                if (progressBar != null) {
                    progressBar.setProgress(values[0]);
                }
                break;
            case MESSAGE:
                message = mCtx.getResources().getString(values[0]);
                if (progressText != null) {
                    progressText.setText(message);
                }
                break;
            case MESSAGECOUNT:
                message = "Uploading photo " + values[0] + " of " + values[1];
                if (progressText != null) {
                    progressText.setText(message);
                }
                break;
            case BLANKPROGRESS:
                if (progressBar != null) {
                    progressBar.setMax(1);
                }
                break;
            }
        });
    }

    private    DbHelper             mDb = null;
    private SyncListener        mSyncListener;
    private TrigApiClient       mApiClient;
    private AuthPreferences     mAuthPreferences;
    
    private static boolean         mLock = false;
    private int                 mAppVersion;
    private int                 mMax;        // Maximum count of things being synced, for progress bar
    private String                mErrorMessage;
    
    private int                    mActiveByteCount; // byte count from currently transferring photo
    private int                    mPreviousByteCount; // byte count from previously transferred photos
    
    private static final int     MAX             = 1;
    private static final int     PROGRESS         = 2;
    private static final int     MESSAGE            = 3;
    private static final int     BLANKPROGRESS    = 4;
    private static final int     MESSAGECOUNT    = 5;
    
    private static final String PREFS_LOGCOUNT  ="logCount";
    
    public static final int     SUCCESS     = 0;
    public static final int     NOROWS         = 1;
    public static final int     ERROR         = 2;
    public static final int     CANCELLED     = 3;
    
    
    
    public SyncTask(Context pCtx, SyncListener listener) {
        this.mCtx = pCtx;
        this.mSyncListener = listener;
        this.mApiClient = new TrigApiClient(pCtx);
        this.mAuthPreferences = new AuthPreferences(pCtx);
        try {
            mAppVersion = mCtx.getPackageManager().getPackageInfo(mCtx.getPackageName(), 0).versionCode;
        } catch (NameNotFoundException e) {
            Log.e(TAG,"Couldn't get versionCode!");
            mAppVersion = 99999;
        }
    }
    
    public void detach() {
        mCtx = null;
        mSyncListener = null;
        if (progressDialog != null) { progressDialog.dismiss(); }
    }
    
    public void attach(Context pCtx, SyncListener listener) {
        this.mCtx = pCtx;
        this.mSyncListener = listener;
        showDialog("Continuing sync");
    }
    
    public void execute(Long... trigId) {
        execute(false, trigId);
    }
    
    public void execute(boolean isAutoSyncAfterDownload, Long... trigId) {
        mIsAutoSyncAfterDownload = isAutoSyncAfterDownload;
        
        // Pre-execution logic (equivalent to onPreExecute)
        if (mCtx == null) {
            Toast.makeText(mCtx, "Sync failed!", Toast.LENGTH_LONG).show();
            if (mSyncListener != null) {
                mSyncListener.onSynced(ERROR);
            }
            return;
        }
        
        // Check that we have Auth0 authentication
        mPrefs = PreferenceManager.getDefaultSharedPreferences(mCtx);
        
        if (!mAuthPreferences.isLoggedIn()) {
            Log.i(TAG, "execute: Not logged in with Auth0, calling onSynced with ERROR status");
            // Only show toast if this isn't an automatic sync after download
            if (!mIsAutoSyncAfterDownload) {
                Toast.makeText(mCtx, R.string.toastPleaseLogin, Toast.LENGTH_LONG).show();
            }
            // Always call onSynced callback, even when not logged in
            if (mSyncListener != null) {
                mSyncListener.onSynced(ERROR);
            }
            return;
        }
        showDialog("Connecting to T:UK");
        mErrorMessage = "";
        
        CompletableFuture.supplyAsync(() -> {
            Log.d(TAG, "doInBackground");
            
            // Make sure only one SyncTask runs at a time
            if (mLock) {
                Log.i(TAG, "SyncTask already running");
                return ERROR;
            }
            mLock = true;

            // Open database connection
            mDb = new DbHelper(mCtx);
            mDb.open();
            
            try {
                if (ERROR == sendLogsToTUK(trigId)) {
                    return ERROR;
                }
                if (ERROR == sendPhotosToTUK(trigId)) {
                    return ERROR;
                }
                mDb.close();
                mDb.open();
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
        }, executor)
        .thenAcceptAsync(result -> {
            Log.d(TAG, "onPostExecute " + result);
            // Post-execution logic (equivalent to onPostExecute)
            if (result == SUCCESS) {
                Toast.makeText(mCtx, "Synced with TrigpointingUK " + mErrorMessage, Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(mCtx, "Error syncing with TrigpointingUK - " + mErrorMessage, Toast.LENGTH_LONG).show();                    
            }
            try {
                if (progressDialog != null) {progressDialog.dismiss();}
            } catch (Exception e) {
                Log.e(TAG, "Exception dismissing dialog - " + e.getMessage());
            }
            if (mSyncListener != null) {
                mSyncListener.onSynced(result);
            }
        }, r -> mainHandler.post(r));
    }
    

    
    
    
    
    Integer sendLogsToTUK(Long... trigId) {
        Log.d(TAG, "sendLogsToTUK");
        Long trig_id = null;
        
        if (trigId != null && trigId.length >= 1) {
            trig_id = trigId[0];
        }
        
        updateProgress(MESSAGE, R.string.syncToTUK);
        Cursor c = mDb.fetchLogs(trig_id);
        if (c==null) {
            return NOROWS;
        }
        
        updateProgress(MAX, c.getCount());
        updateProgress(PROGRESS, 0);
        
        int i=0;
        do {
            if (SUCCESS != sendLogToTUK(c)) {
                return ERROR;
            }
            updateProgress(PROGRESS, ++i);
        } while (c.moveToNext());
        
        c.close();
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

        updateProgress(MESSAGE, R.string.syncPhotosToTUK);
        Cursor c = mDb.fetchPhotos(trig_id);
        if (c==null) {
            return NOROWS;
        }
        
        // whiz through the cursor, totalling file sizes 
        int totalBytes=0;
        do {
            int photoIndex = c.getColumnIndex(DbHelper.PHOTO_PHOTO);
            if (photoIndex >= 0) {
                totalBytes += new File(c.getString(photoIndex)).length();
            }
        } while (c.moveToNext());
        // reset cursor
        c.moveToFirst();
        
        updateProgress(MAX, totalBytes);
        updateProgress(PROGRESS, 0);
        
        int i = 1;
        do {
            updateProgress(MESSAGECOUNT, i++, c.getCount());
            if (SUCCESS != sendPhotoToTUK(c)) {
                return ERROR;
            }
            mPreviousByteCount += mActiveByteCount;
            mActiveByteCount = 0;
        } while (c.moveToNext());
        
        c.close();
        return SUCCESS;
    }
    
    
    
    Integer sendLogToTUK(Cursor c) {
        Log.i(TAG, "sendLogToTUK");
        
        int logIdIndex = c.getColumnIndex(DbHelper.LOG_ID);
        if (logIdIndex < 0) return ERROR;
        long trigId = c.getInt(logIdIndex);
        
        // Get all column indices
        int yearIndex = c.getColumnIndex(DbHelper.LOG_YEAR);
        int monthIndex = c.getColumnIndex(DbHelper.LOG_MONTH);
        int dayIndex = c.getColumnIndex(DbHelper.LOG_DAY);
        int hourIndex = c.getColumnIndex(DbHelper.LOG_HOUR);
        int minutesIndex = c.getColumnIndex(DbHelper.LOG_MINUTES);
        int commentIndex = c.getColumnIndex(DbHelper.LOG_COMMENT);
        int gridrefIndex = c.getColumnIndex(DbHelper.LOG_GRIDREF);
        int fbIndex = c.getColumnIndex(DbHelper.LOG_FB);
        int scoreIndex = c.getColumnIndex(DbHelper.LOG_SCORE);
        int conditionIndex = c.getColumnIndex(DbHelper.LOG_CONDITION);
        
        // Check if any required columns are missing
        if (yearIndex < 0 || monthIndex < 0 || dayIndex < 0 || 
            hourIndex < 0 || minutesIndex < 0 || commentIndex < 0 || gridrefIndex < 0 || 
            fbIndex < 0 || scoreIndex < 0 || conditionIndex < 0) {
            return ERROR;
        }
        
        // Build API request using TrigApiClient
        TrigApiClient.LogCreateRequest request = new TrigApiClient.LogCreateRequest();
        request.trigId = trigId;
        request.date = String.format("%04d-%02d-%02d", 
            c.getInt(yearIndex), c.getInt(monthIndex), c.getInt(dayIndex));
        request.time = String.format("%02d:%02d:00", 
            c.getInt(hourIndex), c.getInt(minutesIndex));
        request.osgbGridref = c.getString(gridrefIndex);
        request.fbNumber = c.getString(fbIndex);
        request.condition = c.getString(conditionIndex);
        request.comment = c.getString(commentIndex);
        request.score = c.getInt(scoreIndex);
        request.source = "W"; // Web/Android source
        
        // Use CountDownLatch for synchronous behavior within async task
        final java.util.concurrent.CountDownLatch latch = new java.util.concurrent.CountDownLatch(1);
        final int[] result = {ERROR};
        final int[] serverLogId = {-1};
        
        mApiClient.createLog(request, new TrigApiClient.ApiCallback<TrigApiClient.LogResponse>() {
            @Override
            public void onSuccess(TrigApiClient.LogResponse response) {
                try {
                    Log.i(TAG, "Successfully created log on server - ID: " + response.id);
                    serverLogId[0] = response.id;
                    result[0] = SUCCESS;
                } catch (Exception e) {
                    Log.e(TAG, "Error processing log response", e);
                    mErrorMessage = "Error processing response: " + e.getMessage();
                    result[0] = ERROR;
                } finally {
                    latch.countDown();
                }
            }
            
            @Override
            public void onError(String errorMessage) {
                Log.e(TAG, "Failed to create log: " + errorMessage);
                mErrorMessage = errorMessage;
                result[0] = ERROR;
                latch.countDown();
            }
        });
        
        // Wait for API call to complete (with timeout)
        try {
            if (!latch.await(30, java.util.concurrent.TimeUnit.SECONDS)) {
                Log.e(TAG, "Log creation timed out");
                mErrorMessage = "Request timed out";
                return ERROR;
            }
        } catch (InterruptedException e) {
            Log.e(TAG, "Log creation interrupted", e);
            mErrorMessage = "Request interrupted";
            return ERROR;
        }
        
        if (result[0] == SUCCESS && serverLogId[0] > 0) {
            // Remove log from local database
            mDb.deleteLog(trigId);
            // Update photos for this trig with log id from server
            mDb.updatePhotos(trigId, serverLogId[0]);
            // Update local logged condition
            if (conditionIndex >= 0) {
                mDb.updateTrigLog(trigId, Condition.fromCode(c.getString(conditionIndex)));
            }
        }
        
        return result[0];
    }
    
    
    
    Integer sendPhotoToTUK(Cursor c) {
        Log.i(TAG, "sendPhotoToTUK");
        
        int photoIdIndex = c.getColumnIndex(DbHelper.PHOTO_ID);
        int photoPathIndex = c.getColumnIndex(DbHelper.PHOTO_PHOTO);
        int thumbPathIndex = c.getColumnIndex(DbHelper.PHOTO_ICON);
        
        if (photoIdIndex < 0 || photoPathIndex < 0 || thumbPathIndex < 0) {
            return ERROR;
        }
        
        Long photoId = c.getLong(photoIdIndex);
        String photoPath = c.getString(photoPathIndex);
        String thumbPath = c.getString(thumbPathIndex);

        // Get all photo column indices
        int tuklogIdIndex = c.getColumnIndex(DbHelper.PHOTO_TUKLOGID);
        int nameIndex = c.getColumnIndex(DbHelper.PHOTO_NAME);
        int descrIndex = c.getColumnIndex(DbHelper.PHOTO_DESCR);
        int subjectIndex = c.getColumnIndex(DbHelper.PHOTO_SUBJECT);
        int ispublicIndex = c.getColumnIndex(DbHelper.PHOTO_ISPUBLIC);
        
        if (tuklogIdIndex < 0 || nameIndex < 0 || descrIndex < 0 || 
            subjectIndex < 0 || ispublicIndex < 0) {
            return ERROR;
        }
        
        // Build API request using TrigApiClient
        TrigApiClient.PhotoUploadRequest request = new TrigApiClient.PhotoUploadRequest();
        request.logId = c.getLong(tuklogIdIndex);
        request.photoPath = photoPath;
        request.caption = c.getString(nameIndex);
        request.description = c.getString(descrIndex);
        
        // Map subject to type (single character)
        String subject = c.getString(subjectIndex);
        request.type = mapSubjectToType(subject);
        
        // Map ispublic to license
        int isPublic = c.getInt(ispublicIndex);
        request.license = isPublic == 1 ? "Y" : "N";
        
        // Use CountDownLatch for synchronous behavior within async task
        final java.util.concurrent.CountDownLatch latch = new java.util.concurrent.CountDownLatch(1);
        final int[] result = {ERROR};
        
        mApiClient.uploadPhoto(request, new TrigApiClient.ApiCallback<TrigApiClient.PhotoResponse>() {
            @Override
            public void onSuccess(TrigApiClient.PhotoResponse response) {
                try {
                    Log.i(TAG, "Successfully uploaded photo to server - ID: " + response.id);
                    result[0] = SUCCESS;
                } catch (Exception e) {
                    Log.e(TAG, "Error processing photo response", e);
                    mErrorMessage = "Error processing response: " + e.getMessage();
                    result[0] = ERROR;
                } finally {
                    latch.countDown();
                }
            }
            
            @Override
            public void onError(String errorMessage) {
                Log.e(TAG, "Failed to upload photo: " + errorMessage);
                mErrorMessage = errorMessage;
                result[0] = ERROR;
                latch.countDown();
            }
        });
        
        // Wait for API call to complete (with timeout)
        try {
            if (!latch.await(60, java.util.concurrent.TimeUnit.SECONDS)) {
                Log.e(TAG, "Photo upload timed out");
                mErrorMessage = "Request timed out";
                return ERROR;
            }
        } catch (InterruptedException e) {
            Log.e(TAG, "Photo upload interrupted", e);
            mErrorMessage = "Request interrupted";
            return ERROR;
        }
        
        if (result[0] == SUCCESS) {
            // Remove photo from local database
            mDb.deletePhoto(photoId);
            // Remove files from cache directory
            new File(photoPath).delete();
            new File(thumbPath).delete();
        }
        
        return result[0];
    }
    
    /**
     * Map legacy photo subject codes to new API type codes
     */
    private String mapSubjectToType(String subject) {
        if (subject == null || subject.isEmpty()) {
            return "O"; // Other
        }
        switch (subject.charAt(0)) {
            case 'T': return "T"; // Trigpoint
            case 'F': return "F"; // Flush bracket
            case 'L': return "L"; // Landscape
            case 'P': return "P"; // People
            default: return "O";  // Other
        }
    }
    
    @Override
    public void transferred(long num) {
        mActiveByteCount = (int) num;
                    updateProgress(PROGRESS, mPreviousByteCount + mActiveByteCount);
        Log.d(TAG, "Transferred bytes: " + num);
    }    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    Integer readLogsFromTUK() {
        Log.d(TAG, "readLogsFromTUK");

        String strLine;                
        int i=0;
        
        
        try {
            updateProgress(BLANKPROGRESS);
            updateProgress(MESSAGE, R.string.syncLogsFromTUK);
            updateProgress(MAX, mPrefs.getInt(PREFS_LOGCOUNT, 1));
            
            // Get username from Auth0 profile for legacy endpoint
            String username = mAuthPreferences.getAuth0UserName();
            if (username == null || username.isEmpty()) {
                username = "unknown";
            }
            
            URL url = new URL("https://trigpointing.uk/trigs/down-android-mylogs.php?username="+URLEncoder.encode(username)+"&appversion="+mAppVersion);
            Log.d(TAG, "Getting " + url);
            URLConnection ucon = url.openConnection();
            InputStream is = ucon.getInputStream();
            GZIPInputStream zis = new GZIPInputStream(new BufferedInputStream(is));
            BufferedReader br = new BufferedReader(new InputStreamReader(zis));

            mDb.mDb.beginTransaction();
            
            // blank out any existing logs;
            mDb.deleteAllTrigLogs();
            
            // first record contains log count
            if ((strLine=br.readLine()) != null) {
                mMax = Integer.parseInt(strLine);
                Log.i(TAG, "Log count from TUK = " + mMax);
                updateProgress(MAX, mMax);
            }
            // read log records records
            while ((strLine = br.readLine()) != null && !strLine.trim().equals(""))   {
                //Log.i(TAG,strLine);
                String[] csv=strLine.split("\t");
                Condition logged        = Condition.fromCode(csv[0]);
                int id                    = Integer.valueOf(csv[1]);
                mDb.updateTrigLog(id, logged);
                i++;
                // Cancellation check removed - use CompletableFuture.cancel() if needed
                updateProgress(PROGRESS, i);
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

        // store the log count to pre-populate the progress bar next time
        mPrefs.edit().putInt(PREFS_LOGCOUNT,i).apply();
        
        return SUCCESS;        
    }
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    

    
    
    protected void showDialog(String message) {
        // Build a simple dialog with a horizontal ProgressBar and a message
        LinearLayout container = new LinearLayout(mCtx);
        container.setOrientation(LinearLayout.VERTICAL);
        int paddingPx = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 24,
                mCtx.getResources().getDisplayMetrics());
        container.setPadding(paddingPx, paddingPx, paddingPx, paddingPx);

        progressText = new TextView(mCtx);
        progressText.setText(message);
        container.addView(progressText, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        progressBar = new ProgressBar(mCtx, null, android.R.attr.progressBarStyleHorizontal);
        progressBar.setIndeterminate(false);
        container.addView(progressBar, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        progressDialog = new AlertDialog.Builder(mCtx)
                .setView(container)
                .setCancelable(true)
                .create();
        progressDialog.show();
    }
    

    
    





}