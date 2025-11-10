package uk.trigpointing.android;

import java.io.IOException;

import uk.trigpointing.android.BuildConfig;
import uk.trigpointing.android.common.BaseActivity;

import android.annotation.SuppressLint;
import android.content.pm.PackageManager.NameNotFoundException;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.MenuItem;
import android.widget.ProgressBar;
import android.widget.TextView;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

import uk.trigpointing.android.logging.SyncTask;
import uk.trigpointing.android.logging.SyncListener;
import uk.trigpointing.android.types.Condition;
import uk.trigpointing.android.types.Trig;
import android.content.Intent;

public class DownloadTrigsActivity extends BaseActivity implements SyncListener {

    private TextView         mStatus;
    private ProgressBar     mProgress;
    private Integer         mDownloadCount = 0;
    private static int         mProgressMax = 10000; // value unimportant
    private int             mAppVersion;
    private Handler         mainHandler;
    // Retry/backoff state
    private int             retryDelaySeconds = 5; // initial delay for exponential backoff
    private int             countdownRemainingSeconds = 0;
    private Runnable         countdownRunnable;
    
    private static final String TAG = "DownloadTrigsActivity";
    private enum DownloadStatus {OK, CANCELLED, ERROR}


    @SuppressLint("SetTextI18n")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.download);
        
        // Enable back button in action bar
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }
        
        try {
            mAppVersion = this.getPackageManager().getPackageInfo(this.getPackageName(), 0).versionCode;
        } catch (NameNotFoundException e) {
            Log.e(TAG,"Couldn't get versionCode!");
            mAppVersion = 99999;
        }        
        
        mStatus = findViewById(R.id.downloadStatus);
        mStatus.setText("Starting download...");
        mProgress = findViewById(R.id.downloadProgress);
        mProgress.setMax(mProgressMax);
        mProgress.setProgress(0);
        
        // Initialize main handler
        mainHandler = new Handler(Looper.getMainLooper());
        
        // Automatically start the download
        CompletableFuture<DownloadStatus> mTask = downloadTrigs();

    }


    @SuppressLint("SetTextI18n")
    private CompletableFuture<DownloadStatus> downloadTrigs() {
        // Setup UI on main thread
        mDownloadCount = 0;
        mStatus.setText("Downloading trigpoint data...");

        ExecutorService executor = Executors.newSingleThreadExecutor();
        
        return CompletableFuture.supplyAsync(() -> {
            Log.i(TAG, "PopulateTrigsTask: Starting download");

            DbHelper db = new DbHelper(DownloadTrigsActivity.this);
            OkHttpClient client = new OkHttpClient();
            Gson gson = new Gson();
            TrigExportResponse exportResponse;

            try {
                String downloadUrl = BuildConfig.TRIG_API_BASE + "/v1/trigs/export";
                Log.i(TAG, "PopulateTrigsTask: Downloading from URL: " + downloadUrl);

                Request request = new Request.Builder()
                        .url(downloadUrl)
                        .get()
                        .build();

                String responseBody;
                try (Response response = client.newCall(request).execute()) {
                    if (!response.isSuccessful() || response.body() == null) {
                        Log.e(TAG, "PopulateTrigsTask: Failed to download trig export, HTTP " + response.code());
                        return DownloadStatus.ERROR;
                    }
                    responseBody = response.body().string();
                }

                exportResponse = gson.fromJson(responseBody, TrigExportResponse.class);
            } catch (IOException | JsonSyntaxException e) {
                Log.e(TAG, "PopulateTrigsTask: Error downloading or parsing export", e);
                return DownloadStatus.ERROR;
            }

            if (exportResponse == null || exportResponse.items == null || exportResponse.items.isEmpty()) {
                Log.e(TAG, "PopulateTrigsTask: Export response was empty");
                return DownloadStatus.ERROR;
            }

            int expectedTotal = exportResponse.total != null ? exportResponse.total : exportResponse.items.size();
            if (expectedTotal <= 0) {
                expectedTotal = exportResponse.items.size();
            }
            if (expectedTotal <= 0) {
                expectedTotal = 1;
            }
            mProgressMax = expectedTotal;
            final int progressMax = expectedTotal;
            mainHandler.post(() -> mProgress.setMax(progressMax));

            int insertedCount = 0;

            try {
                Log.i(TAG, "PopulateTrigsTask: Opening database");
                db.open();
                db.mDb.beginTransaction();

                Log.i(TAG, "PopulateTrigsTask: Deleting all existing data");
                db.deleteAll();

                for (TrigExportItem item : exportResponse.items) {
                    if (item == null) {
                        continue;
                    }

                    try {
                        if (item.wgs_lat == null || item.wgs_lat.trim().isEmpty()
                                || item.wgs_long == null || item.wgs_long.trim().isEmpty()) {
                            Log.w(TAG, "Skipping item with empty lat/lon: trigId=" + item.id);
                            continue;
                        }

                        double lat = Double.parseDouble(item.wgs_lat);
                        double lon = Double.parseDouble(item.wgs_long);

                        Trig.Physical physicalType = mapPhysicalType(item.physical_type);
                        Condition condition = Condition.fromCode(item.condition);

                        db.createTrig(
                                item.id,
                                item.name != null ? item.name : "",
                                item.waypoint != null ? item.waypoint : "",
                                lat,
                                lon,
                                physicalType,
                                condition,
                                Condition.TRIGNOTLOGGED,
                                Trig.Current.NONE,
                                Trig.Historic.UNKNOWN,
                                ""
                        );

                        insertedCount++;
                        if (insertedCount % 25 == 0 || insertedCount == progressMax) {
                            final int progress = insertedCount;
                            mainHandler.post(() -> {
                                mProgress.setProgress(progress);
                                mStatus.setText("Inserted " + progress + " trigs");
                            });
                        }
                    } catch (NumberFormatException nfe) {
                        Log.w(TAG, "Skipping item with invalid number format: trigId=" + item.id, nfe);
                    } catch (Exception ex) {
                        Log.w(TAG, "Skipping item due to exception: trigId=" + item.id, ex);
                    }
                }

                final int finalInsertedCount = insertedCount;
                mainHandler.post(() -> {
                    mProgress.setProgress(Math.min(finalInsertedCount, progressMax));
                    mStatus.setText("Inserted " + finalInsertedCount + " trigs");
                });

                db.mDb.execSQL("create index if not exists latlon on trig (lat, lon)");
                db.mDb.setTransactionSuccessful();
            } catch (Exception e) {
                Log.e(TAG, "PopulateTrigsTask: Unexpected error", e);
                return DownloadStatus.ERROR;
            } finally {
                if (db.mDb != null && db.mDb.inTransaction()) {
                    db.mDb.endTransaction();
                }
                db.close();
                mDownloadCount = insertedCount;
            }

            if (insertedCount == 0) {
                Log.e(TAG, "PopulateTrigsTask: No trig records inserted");
                return DownloadStatus.ERROR;
            }

            return DownloadStatus.OK;
        }, executor)
        .thenApplyAsync(result -> {
            switch (result) {
            case OK:
                mStatus.setText("Download complete! " + mDownloadCount + " trigpoints downloaded. Starting sync...");
                mProgress.setProgress(mProgressMax);
                // Start sync after successful download with auto-sync flag
                mainHandler.post(() -> new SyncTask(DownloadTrigsActivity.this, DownloadTrigsActivity.this).execute(true));
                break;
            case ERROR:
                mProgress.setProgress(0);
                // Schedule retry with exponential backoff and per-second countdown
                scheduleRetryWithCountdown();
                break;
            case CANCELLED:
                mStatus.setText("Download cancelled.");
                mProgress.setProgress(0);
                // Auto-close after 3 seconds
                mainHandler.postDelayed(DownloadTrigsActivity.this::finish, 3000);
                break;
            }
            return result;
        }, mainHandler::post);
    }

    private Trig.Physical mapPhysicalType(String physicalType) {
        if (physicalType == null) {
            return Trig.Physical.OTHER;
        }
        String normalized = physicalType.trim();
        for (Trig.Physical type : Trig.Physical.values()) {
            if (type.toString().equalsIgnoreCase(normalized)) {
                return type;
            }
        }
        if ("Active station".equalsIgnoreCase(normalized)) {
            return Trig.Physical.ACTIVE;
        }
        if ("Unknown - user added".equalsIgnoreCase(normalized)) {
            return Trig.Physical.USERADDED;
        }
        return Trig.Physical.OTHER;
    }

    private static class TrigExportResponse {
        java.util.List<TrigExportItem> items;
        Integer total;
        String generated_at;
        String cache_info;
    }

    private static class TrigExportItem {
        int id;
        String waypoint;
        String name;
        String status_name;
        String physical_type;
        String condition;
        String wgs_lat;
        String wgs_long;
        String osgb_gridref;
        String distance_km;
    }

    private void scheduleRetryWithCountdown() {
        // Initialize countdown
        countdownRemainingSeconds = retryDelaySeconds;
        updateRetryStatusText();

        // Cancel any existing countdown before starting a new one
        if (countdownRunnable != null) {
            mainHandler.removeCallbacks(countdownRunnable);
        }

        countdownRunnable = new Runnable() {
            @Override
            public void run() {
                countdownRemainingSeconds--;
                if (countdownRemainingSeconds > 0) {
                    updateRetryStatusText();
                    mainHandler.postDelayed(this, 1000);
                } else {
                    // Time to retry now
                    mStatus.setText(getString(R.string.retrying_download));
                    // Increase delay for the next potential retry (exponential backoff with 300s cap)
                    retryDelaySeconds = Math.min(retryDelaySeconds * 2, 300);
                    // Start the download again
                    downloadTrigs();
                }
            }
        };

        mainHandler.postDelayed(countdownRunnable, 1000);
    }

    @SuppressLint("SetTextI18n")
    private void updateRetryStatusText() {
        mStatus.setText("Download failed!  Will retry in " + countdownRemainingSeconds + " seconds");
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // Clean up any pending countdown callbacks to avoid leaks
        if (mainHandler != null && countdownRunnable != null) {
            mainHandler.removeCallbacks(countdownRunnable);
        }
    }
    
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            // Handle back button in action bar
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
    
    @SuppressLint("SetTextI18n")
    @Override
    public void onSynced(int status) {
        Log.i(TAG, "onSynced: Sync completed with status: " + status);
        
        mainHandler.post(() -> {
            Log.i(TAG, "onSynced: Updating UI and scheduling return to MainActivity");
            switch (status) {
            case SyncTask.SUCCESS:
                mStatus.setText("Sync complete! All data synchronised.");
                break;
            case SyncTask.NOROWS:
                mStatus.setText("Sync complete! No data to sync.");
                break;
            case SyncTask.ERROR:
                // For first-time users, this is expected - show appropriate message
                mStatus.setText("Sync skipped - Please login for a more personalised experience.");
                break;
            case SyncTask.CANCELLED:
                mStatus.setText("Sync cancelled.");
                break;
            default:
                mStatus.setText("Sync completed with unknown status.");
                break;
            }
            
            Log.i(TAG, "onSynced: Scheduling return to MainActivity in 1.5 seconds");
            // Return to MainActivity after a shorter delay (1.5 seconds instead of 3)
            mainHandler.postDelayed(() -> {
                Log.i(TAG, "onSynced: Returning to MainActivity now");
                finish();
                // Ensure we return to MainActivity
                Intent intent = new Intent(DownloadTrigsActivity.this, MainActivity.class);
                intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(intent);
            }, 1500);
        });
    }
}
