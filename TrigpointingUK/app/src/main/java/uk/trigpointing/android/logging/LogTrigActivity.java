package uk.trigpointing.android.logging;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.List;

// import org.acra.ErrorReporter;

import uk.trigpointing.android.common.BaseTabActivity;
import androidx.appcompat.app.AlertDialog;

import android.Manifest;
import android.content.pm.PackageManager;
import android.widget.ProgressBar;
import android.widget.LinearLayout;
import android.util.TypedValue;
import android.content.Context;

import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;

import android.database.Cursor;
import android.graphics.Bitmap;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.net.Uri;
import android.os.Bundle;
import androidx.preference.PreferenceManager;
import android.provider.MediaStore;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.MotionEvent;
import android.view.View.OnFocusChangeListener;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.DatePicker;
import android.widget.DatePicker.OnDateChangedListener;
import android.widget.EditText;

import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;
import androidx.activity.OnBackPressedCallback;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.GridLayoutManager;
// Removed unused Fragment-related imports; using getSupportFragmentManager directly
// Removed direct Compose interop imports in Java; we host via FrameLayout/RecyclerView
import android.widget.RatingBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.TimePicker;
import android.widget.Toast;
import android.view.MenuItem;
import android.widget.ViewSwitcher;
import android.widget.ScrollView;
import android.view.GestureDetector;
import android.view.GestureDetector.SimpleOnGestureListener;

import uk.trigpointing.android.DbHelper;
import uk.trigpointing.android.R;
import uk.trigpointing.android.common.FileCache;
import uk.trigpointing.android.common.Utils;
import uk.trigpointing.android.types.Condition;
import uk.trigpointing.android.types.LatLon;
import uk.trigpointing.android.types.LatLon.UNITS;
import uk.trigpointing.android.types.PhotoSubject;
import uk.trigpointing.android.types.TrigPhoto;
// no direct Compose imports in Java

public class LogTrigActivity extends BaseTabActivity implements OnDateChangedListener, LocationListener, SyncListener {
    private static final String TAG            = "LogTrigActivity";
    private SharedPreferences     mPrefs;

    private LatLon.UNITS        mUnits;
    private String                mStrUnits;
    
    private Long                mTrigId;
    private LatLon                mTrigLocation;
    
    private ViewSwitcher         mSwitcher;
    private ScrollView            mScroll;
    private CheckBox            mSendTime;
    private DatePicker            mDate;
    private TimePicker            mTime;
    private EditText            mGridref;
    private EditText            mFb;
    private Spinner                mCondition;
    private RatingBar            mScore;
    private EditText            mComment;
    private CheckBox            mAdminFlag;
    private CheckBox            mUserFlag;
    private RecyclerView            mGallery;
    private TextView            mLocationError;
    
    private DbHelper             mDb;
    private boolean                mHaveLog;
    
    private    List<TrigPhoto>     mPhotos; 
    private PhotoManager        mPhotoManager;

    private AlertDialog      mProgressDialog;
    private ProgressBar      mProgressBar;
    private TextView         mProgressText;
    private LocationManager     mLocationManager;
    private static final int REQ_LOCATION = 2001;
    
        // Photo picker constants
    private static final int CHOOSE_PHOTO = 1;
    private static final int EDIT_PHOTO = 2;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Log.i(TAG, "onCreate");
        super.onCreate(savedInstanceState);
        setContentView(R.layout.logtrig);
        
        // This activity is embedded inside TrigDetailsActivity TabHost.
        // Hide the activity action bar to avoid a double header.
        if (getSupportActionBar() != null) {
            getSupportActionBar().hide();
        }
        
        // Initialize PhotoManager with a coroutine scope
        // Using GlobalScope for this specific use case where we need a long-lived scope
        mPhotoManager = new PhotoManager(this, kotlinx.coroutines.GlobalScope.INSTANCE);
        
        // Note: ActivityResultLauncher doesn't work properly with LocalActivityManager
        // We'll use the traditional onActivityResult approach instead
        
        mPrefs = PreferenceManager.getDefaultSharedPreferences(this);

        // Get trig_id from extras 
        Bundle extras = getIntent().getExtras();
        if (extras != null) {
            mTrigId = extras.getLong(DbHelper.TRIG_ID);
        }
        if (mTrigId == null) {mTrigId=0L;}
        Log.i(TAG, "Trig_id = "+mTrigId);
        
        // should we list m or yards?
        if (mPrefs.getString("units", "metric").equals("metric")) {
            mUnits = UNITS.METRES;
            mStrUnits = "m";
        } else {
            mUnits = UNITS.YARDS;
            mStrUnits = "yds";
        }

        
        // Get references to various views and form elements
        mSwitcher         = findViewById(R.id.logswitcher);
        mScroll            = findViewById(R.id.logScroll);
        
        // Ensure proper content positioning to prevent action bar overlap
        // Content positioning is now handled by BaseActivity
        mSendTime        = findViewById(R.id.sendTime);
           mTime            = findViewById(R.id.logTime);
           mDate            = findViewById(R.id.logDate);
           mGridref         = findViewById(R.id.logGridref);
           mLocationError     = findViewById(R.id.locationError);
           mFb             = findViewById(R.id.logFB);
           mCondition        = findViewById(R.id.logCondition);
           mScore            = findViewById(R.id.logScore);
           
               // Allow half-stars with minimum 0.5
           mScore.setOnRatingBarChangeListener(new RatingBar.OnRatingBarChangeListener() {
               @Override
               public void onRatingChanged(RatingBar ratingBar, float rating, boolean fromUser) {
                   if (!fromUser) { return; }
                   if (rating < 0.5f) { ratingBar.setRating(0.5f); return; }
                   // Snap to nearest half-star for consistency
                   float snapped = Math.round(rating * 2f) / 2f;
                   if (Math.abs(snapped - rating) > 0.01f) {
                       ratingBar.setRating(snapped);
                   }
               }
           });
        
           // Setup CheckBox listener for time logging
           mSendTime.setOnCheckedChangeListener(new android.widget.CompoundButton.OnCheckedChangeListener() {
               @Override
               public void onCheckedChanged(android.widget.CompoundButton buttonView, boolean isChecked) {
                   updateTimeVisibility();
               }
           });
           mComment        = findViewById(R.id.logComment);

        // Setup time picker options which cannot be set in the config xml
         mTime.setIs24HourView(true);
         
                 // Force DatePicker to spinner mode only (hide calendar)
        // Note: setCalendarViewShown and setSpinnersShown are deprecated but still functional
        // These will be modernized in a future update
        try {
            @SuppressWarnings("deprecation")
            boolean calendarViewShown = false;
            @SuppressWarnings("deprecation")
            boolean spinnersShown = true;
            // Suppress deprecation warnings for now - these methods still work
        } catch (Exception e) {
            Log.w(TAG, "Could not force DatePicker to spinner mode: " + e.getMessage());
        }
           mAdminFlag        = findViewById(R.id.logAdminFlag);
           mUserFlag        = findViewById(R.id.logUserFlag);
            mGallery         = findViewById(R.id.logGallery);
        // Use a grid that adds rows as photos are added
        int spanCount = computePhotoGridSpanCount();
        mGallery.setLayoutManager(new GridLayoutManager(this, spanCount));
        // Let the form's ScrollView handle scrolling; gallery expands to fit content
        mGallery.setNestedScrollingEnabled(false);

        


        // Setup condition spinner
         List<Condition> loggableConditions = new ArrayList<Condition>(Arrays.asList(Condition.values()));
         loggableConditions.remove(Condition.TRIGNOTLOGGED);
        ArrayAdapter<Condition> adapter = new ArrayAdapter<Condition> (this, android.R.layout.simple_spinner_item, loggableConditions);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        mCondition.setAdapter(adapter);

        // Switch off logUserFlag
        if (!mPrefs.getBoolean("experimental", false)) {
            mUserFlag.setVisibility(View.INVISIBLE);
        }
            
        
        // Setup listener on gallery photos
        final GestureDetector tapDetector = new GestureDetector(this, new SimpleOnGestureListener() {
            @Override
            public boolean onSingleTapUp(MotionEvent e) {
                return true;
            }
        });
        mGallery.addOnItemTouchListener(new RecyclerView.OnItemTouchListener() {
            @Override
            public boolean onInterceptTouchEvent(RecyclerView rv, MotionEvent e) {
                if (!tapDetector.onTouchEvent(e)) {
                    return false; // not a tap, let RecyclerView handle (scroll/drag)
                }
                View child = rv.findChildViewUnder(e.getX(), e.getY());
                if (child != null) {
                    int position = rv.getChildAdapterPosition(child);
                    if (position != RecyclerView.NO_POSITION && mPhotos != null && position < mPhotos.size()) {
                        Log.i(TAG, "Clicked photo icon number : " + position);
                        Long photoId = mPhotos.get(position).getLogID();
                        showPhotoMetadataDialog(photoId);
                        return true;
                    }
                }
                return false;
            }

            @Override
            public void onTouchEvent(RecyclerView rv, MotionEvent e) {}

            @Override
            public void onRequestDisallowInterceptTouchEvent(boolean disallowIntercept) {}
        });

        
        // Connect to database
        mDb = new DbHelper(this);
        mDb.open();
        
        // Fetch trigpoint info
        Cursor c = mDb.fetchTrigInfo(mTrigId);
        c.moveToFirst();
        int latIndex = c.getColumnIndex(DbHelper.TRIG_LAT);
        int lonIndex = c.getColumnIndex(DbHelper.TRIG_LON);
        if (latIndex >= 0 && lonIndex >= 0) {
            Double lat = Double.valueOf(c.getString(latIndex));
            Double lon = Double.valueOf(c.getString(lonIndex));
            mTrigLocation = new LatLon(lat, lon);
        }
        c.close();


        // Check to see whether log already exists
        mHaveLog = (mDb.fetchLog(mTrigId) != null);
        
        // Set initial ViewSwitcher state:
        // - If no log exists, show the "Log this trigpoint" button (index 0)
        // - If log exists, show the form (index 1)
        if (mHaveLog) {
            mSwitcher.setDisplayedChild(1); // Show form
        } else {
            mSwitcher.setDisplayedChild(0); // Show button
        }
        
        
        // Setup button to add photo
        Button takePhotoBtn = findViewById(R.id.logAddPhoto);
        takePhotoBtn.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View arg0) {
                choosePhoto();
            }
        });    

        // Setup button to create a new log
        Button addLogBtn = findViewById(R.id.addLog);
        addLogBtn.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View arg0) {
                Log.i(TAG, "Create Log");
                createNewLog();
            populateFields();
                mSwitcher.setDisplayedChild(1); // Show form
                mHaveLog = true;
            }
        });    

        // Setup button to delete the log
        Button deleteLogBtn = findViewById(R.id.logDelete);
        deleteLogBtn.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View arg0) {
                Log.i(TAG, "Delete Log");
                deleteLog();
                mSwitcher.setDisplayedChild(0); // Show button
                mHaveLog = false;
                if (mScroll != null) { mScroll.fullScroll(View.FOCUS_UP); }
                Toast.makeText(LogTrigActivity.this, "Log deleted", Toast.LENGTH_SHORT).show();
                switchToInfoTab();
            }
        });    

        // Setup button to add the current location
        Button locationBtn = findViewById(R.id.logGetLocation);
        locationBtn.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View arg0) {
                getLocation();
            }
        });    




        
        // Setup button to upload the log
        Button uploadBtn = findViewById(R.id.logUploadNow);
        uploadBtn.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View arg0) {
                uploadLog();
                switchToInfoTab();
            }
        });    

        // Setup button to sync the log later (switch to info tab)
        Button syncLaterBtn = findViewById(R.id.logSyncLater);
        syncLaterBtn.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View arg0) {
                Toast.makeText(LogTrigActivity.this, "Log will be synced later", Toast.LENGTH_SHORT).show();
                switchToInfoTab();
            }
        });    

        // Setup change listener for sendTime button
        mSendTime.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View arg0) {
                updateTimeVisibility();
            }
        });    

        
        // check new grid references
        mGridref.setOnFocusChangeListener(new OnFocusChangeListener() {            @Override
            public void onFocusChange(View v, boolean hasFocus) {
                if (!hasFocus) {
                    checkDistance();
                }
            }
        });
        
        // Set up back button handling to show confirmation dialog
        setupBackButtonHandling();
    }
    
    /**
     * Set up back button handling to show confirmation dialog when navigating away
     */
    private void setupBackButtonHandling() {
        getOnBackPressedDispatcher().addCallback(this, new androidx.activity.OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (hasUnsavedChanges()) {
                    showNavigationConfirmationDialog();
                } else {
                    // No unsaved changes, allow normal back navigation
                    finish();
                }
            }
        });
    }
    
    /**
     * Check if there are unsaved changes (i.e., if the user has started logging)
     */
    public boolean hasUnsavedChanges() {
        return mHaveLog;
    }
    
    /**
     * Switch to the info tab in the parent TrigDetailsActivity
     */
    private void switchToInfoTab() {
        try {
            android.app.Activity parent = getParent();
            if (parent instanceof uk.trigpointing.android.trigdetails.TrigDetailsActivity) {
                ((uk.trigpointing.android.trigdetails.TrigDetailsActivity) parent).switchToInfoTab();
            }
        } catch (Exception e) {
            Log.e(TAG, "Error switching to info tab: " + e.getMessage(), e);
            // Fallback to finish if we can't switch tabs
            finish();
        }
    }
    
    /**
     * Show confirmation dialog when navigating away from log form with unsaved changes
     */
    private void showNavigationConfirmationDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Unsaved Changes");
        builder.setMessage("You have unsaved changes to your log. What would you like to do?");
        
        builder.setPositiveButton("Delete Log", (dialog, which) -> {
            Log.i(TAG, "User chose to delete log");
            deleteLog();
            mSwitcher.setDisplayedChild(0); // Show button
            mHaveLog = false;
            if (mScroll != null) { mScroll.fullScroll(View.FOCUS_UP); }
            Toast.makeText(LogTrigActivity.this, "Log deleted", Toast.LENGTH_SHORT).show();
            dialog.dismiss();
            switchToInfoTab();
        });
        
        builder.setNeutralButton("Upload Now", (dialog, which) -> {
            Log.i(TAG, "User chose to upload log now");
            uploadLog();
            dialog.dismiss();
            switchToInfoTab();
        });
        
        builder.setNegativeButton("Sync Later", (dialog, which) -> {
            Log.i(TAG, "User chose to sync later");
            Toast.makeText(LogTrigActivity.this, "Log will be synced later", Toast.LENGTH_SHORT).show();
            dialog.dismiss();
            switchToInfoTab();
        });
        
        AlertDialog dialog = builder.create();
        dialog.show();
    }

    public void checkDistance() {
        Log.i(TAG, "checkDistance");
        try {
            LatLon ll = new LatLon(mGridref.getText().toString());
            Double dist = ll.distanceTo(mTrigLocation, mUnits);
            Log.e(TAG, "trig " + mTrigLocation.getOSGB10() + " - " + mTrigLocation.getWGS());
            Log.e(TAG, "log " + ll.getOSGB10() + " - " + ll.getWGS());
            Log.e(TAG, "Gridref " + dist.intValue() + " " + mUnits + " from database location");
            if (dist >= 50) {
                mLocationError.setText(getString(R.string.location_warning_format, dist.intValue(), mStrUnits));
                mLocationError.setTextColor(ContextCompat.getColor(this, R.color.errorcolour));
            } else {
                mLocationError.setText(getString(R.string.location_distance_format, dist.intValue(), mStrUnits));
                mLocationError.setTextColor(ContextCompat.getColor(this, R.color.okcolour));
            }
        } catch (IllegalArgumentException e) {
            Log.e(TAG, e.getMessage());
            mLocationError.setText(e.getMessage());
            mLocationError.setTextColor(ContextCompat.getColor(this, R.color.errorcolour));
        }        
    }
    
    private void tryPrePopulateGridReference() {
        if (mLocationManager == null) {
            mLocationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        }
        
        try {
            // Check if GPS is enabled
            if (!mLocationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                Log.d(TAG, "GPS not enabled, cannot pre-populate grid reference");
                return;
            }
            
            // Get last known location
            Location lastLocation = mLocationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
            if (lastLocation != null) {
                // Check if location is recent (within last 10 minutes)
                long locationAge = System.currentTimeMillis() - lastLocation.getTime();
                if (locationAge < 10 * 60 * 1000) { // 10 minutes in milliseconds
                    LatLon ll = new LatLon(lastLocation);
                    mGridref.setText(ll.getOSGB10());
                    checkDistance();
                    Log.d(TAG, "Pre-populated grid reference with GPS: " + ll.getOSGB10());
                } else {
                    Log.d(TAG, "Last GPS location too old (" + (locationAge / 60000) + " minutes), not using");
                }
            } else {
                Log.d(TAG, "No last known GPS location available");
            }
        } catch (SecurityException e) {
            Log.w(TAG, "No location permission, cannot pre-populate grid reference");
        } catch (Exception e) {
            Log.e(TAG, "Error trying to pre-populate grid reference", e);
        }
    }
    
    @Override
    protected void onPause() {
        Log.i(TAG, "onPause - calling saveLog");
        super.onPause();
        saveLog();
    }
    
    @Override
    protected void onStop() {
        Log.i(TAG, "onStop");
        super.onStop();
    }

    @Override
    protected void onResume() {
        Log.i(TAG, "onResume - calling populateFields");
        super.onResume();
        if (mTrigId != null) {
            populateFields();
        }
    }
    
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        
        Log.i(TAG, "onActivityResult: requestCode=" + requestCode + ", resultCode=" + resultCode + ", data=" + (data != null ? "present" : "null"));
        
        if (requestCode == CHOOSE_PHOTO) {
            Log.i(TAG, "Processing CHOOSE_PHOTO result");
            if (resultCode == RESULT_OK && data != null) {
                Log.i(TAG, "Photo picker returned OK with data");
                
                List<Uri> selectedUris = new ArrayList<>();
                
                // Check for multiple selection via ClipData
                if (data.getClipData() != null) {
                    int count = data.getClipData().getItemCount();
                    Log.i(TAG, "ClipData found with " + count + " items");
                    for (int i = 0; i < count; i++) {
                        Uri uri = data.getClipData().getItemAt(i).getUri();
                        if (uri != null) {
                            selectedUris.add(uri);
                            Log.d(TAG, "Added URI from ClipData: " + uri);
                        }
                    }
                } else if (data.getData() != null) {
                    // Single selection
                    Uri uri = data.getData();
                    selectedUris.add(uri);
                    Log.i(TAG, "Single photo selected: " + uri);
                } else {
                    Log.w(TAG, "No URI found in result data");
                }
                
                if (!selectedUris.isEmpty()) {
                    Log.i(TAG, "Processing " + selectedUris.size() + " selected photo(s)");
                    handleSelectedPhotos(selectedUris);
                } else {
                    Log.w(TAG, "No valid URIs found in result");
                    Toast.makeText(this, "No photos were selected", Toast.LENGTH_SHORT).show();
                }
            } else if (resultCode == RESULT_CANCELED) {
                Log.i(TAG, "Photo picker was cancelled by user");
            } else {
                Log.w(TAG, "Photo picker returned unexpected result: " + resultCode);
            }
        } else if (requestCode == EDIT_PHOTO) {
            Log.i(TAG, "Processing EDIT_PHOTO result");
            if (resultCode == RESULT_OK) {
                Log.i(TAG, "Photo editing returned OK, updating gallery");
                updateGallery();
            } else if (resultCode == RESULT_CANCELED) {
                Log.i(TAG, "Photo editing was cancelled by user");
            } else {
                Log.i(TAG, "Photo editing returned unexpected result: " + resultCode);
            }
        } else {
            Log.w(TAG, "Unknown request code: " + requestCode);
        }
    }

    @Override
    protected void onDestroy() {
        Log.i(TAG, "onDestroy");
        super.onDestroy();
        if (mDb != null) {
            mDb.close();
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

    
    // service request to choose a photo using modern approach
    public void choosePhoto() {
        Log.i(TAG, "choosePhoto() called - Starting photo selection process");
        
        // When embedded in LocalActivityManager, we need the parent to launch the picker
        // Get the parent activity (TrigDetailsActivity)
        android.app.Activity parent = getParent();
        if (parent != null) {
            Log.i(TAG, "Found parent activity: " + parent.getClass().getSimpleName());
            Log.i(TAG, "Requesting parent to launch photo picker");
            
            // Use the parent activity to launch a single consistent images-only picker
            try {
                Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
                intent.setType("image/*");
                intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
                intent.addCategory(Intent.CATEGORY_OPENABLE);
                Log.d(TAG, "Using ACTION_GET_CONTENT (images only; cap 5 enforced in code)");
                
                // Check if the intent can be handled
                if (intent.resolveActivity(getPackageManager()) != null) {
                    Log.i(TAG, "Parent starting activity for result with request code: " + CHOOSE_PHOTO);
                    parent.startActivityForResult(Intent.createChooser(intent, "Select photos"), CHOOSE_PHOTO);
                } else {
                    // Final fallback - single image picker
                    Log.w(TAG, "Multi-select not available, falling back to single image picker");
                    Intent singleIntent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
                    parent.startActivityForResult(singleIntent, CHOOSE_PHOTO);
                }
            } catch (Exception e) {
                Log.e(TAG, "Failed to launch photo picker", e);
                Toast.makeText(this, "Failed to open photo picker: " + e.getMessage(), Toast.LENGTH_LONG).show();
            }
        } else {
            Log.e(TAG, "No parent activity found! Cannot launch photo picker properly");
            // Fallback to direct launch (might not work properly)
            Log.w(TAG, "Attempting direct launch as fallback...");
            try {
                Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
                intent.setType("image/*");
                intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
                intent.addCategory(Intent.CATEGORY_OPENABLE);
                startActivityForResult(intent, CHOOSE_PHOTO);
            } catch (Exception e) {
                Log.e(TAG, "Fallback launch also failed", e);
                Toast.makeText(this, "Cannot open photo picker in this context", Toast.LENGTH_LONG).show();
            }
        }
    }
    
    // Handle photos selected from the modern picker
    private void handleSelectedPhotos(List<Uri> uris) {
        Log.i(TAG, "handleSelectedPhotos() called with " + uris.size() + " URIs");
        
        if (mTrigId == null || mTrigId == 0) {
            Log.e(TAG, "Cannot add photos - mTrigId is null or 0: " + mTrigId);
            Toast.makeText(this, "Please create a log entry first", Toast.LENGTH_LONG).show();
            return;
        }
        
        Log.d(TAG, "Processing photos for trigpoint ID: " + mTrigId);
        
        // Filter URIs to only image/* MIME types
        List<Uri> imageUris = new ArrayList<>();
        for (Uri uri : uris) {
            try {
                String type = getContentResolver().getType(uri);
                if (type != null && type.startsWith("image/")) {
                    imageUris.add(uri);
                } else {
                    Log.w(TAG, "Skipping non-image URI: " + uri + " type=" + type);
                }
            } catch (Exception e) {
                Log.w(TAG, "Could not determine MIME type for URI: " + uri, e);
            }
        }
        if (imageUris.isEmpty()) {
            Toast.makeText(this, "Only images are supported", Toast.LENGTH_LONG).show();
            return;
        }
        // Enforce maximum of 5 selections
        if (imageUris.size() > 5) {
            imageUris = imageUris.subList(0, 5);
            Toast.makeText(this, "Limited to 5 photos at a time", Toast.LENGTH_SHORT).show();
        }
        
        // Show progress dialog (using AlertDialog instead of deprecated ProgressDialog)
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setMessage("Processing photos...");
        builder.setCancelable(false);
        AlertDialog progressDialog = builder.create();
        progressDialog.show();
        Log.d(TAG, "Progress dialog shown");
        
        mPhotoManager.processSelectedPhotos(
            mTrigId,
            imageUris,
            (Long photoId) -> {
                // Photo added successfully, show metadata dialog
                runOnUiThread(() -> {
                    progressDialog.dismiss();
                    // New photo just created -> suppress Cancel in dialog
                    showPhotoMetadataDialog(photoId, true);
                });
                return kotlin.Unit.INSTANCE;
            },
            () -> {
                // All photos processed
                runOnUiThread(() -> {
                    progressDialog.dismiss();
                    updateGallery();
                    Toast.makeText(this, "Photos added successfully", Toast.LENGTH_SHORT).show();
                });
                return kotlin.Unit.INSTANCE;
            },
            (String error) -> {
                // Error occurred
                runOnUiThread(() -> {
                    progressDialog.dismiss();
                    Toast.makeText(this, "Error: " + error, Toast.LENGTH_LONG).show();
                });
                return kotlin.Unit.INSTANCE;
            }
        );
    }
    
    private void showPhotoMetadataDialog(Long photoId) {
        showPhotoMetadataDialog(photoId, false);
    }

    private void showPhotoMetadataDialog(Long photoId, boolean isNew) {
        PhotoMetadataDialog dialog = PhotoMetadataDialog.Companion.newInstance(photoId.longValue(), isNew);
        dialog.setCallbacks(
            metadata -> {
                // Metadata saved
                updateGallery();
                return kotlin.Unit.INSTANCE;
            },
            () -> {
                // Photo deleted
                updateGallery();
                return kotlin.Unit.INSTANCE;
            }
        );
        dialog.show(getSupportFragmentManager(), "photo_metadata");
    }

    
    
    
    private void createPhoto (Intent data) {
        // Photo chosen - create DB entry and send user to edit activity
        Log.i(TAG, "createPhoto");
        Uri selectedImageUri = data.getData();
        if (selectedImageUri != null) {
            Log.i(TAG, "Photo URI - " + selectedImageUri);
            
            // create a database record for the new photo
            Long photoId = mDb.createPhoto(mTrigId, "", "", "", "", PhotoSubject.NOSUBJECT, 0);
            Log.i(TAG, "Created Photo - " + photoId);

            
            // shrink the photo and store on SD card
            String cachedir  = new FileCache(this, "logphotos").getCacheDir().getAbsolutePath();
            String photoPath = cachedir + "/" + photoId + "_I.jpg";
            String thumbPath = cachedir + "/" + photoId + "_T.jpg";
            Log.d(TAG, photoPath + " - " + thumbPath);
            try {
                Bitmap bThumb = Utils.decodeUri(this, selectedImageUri,  100);
                Bitmap bPhoto = Utils.decodeUri(this, selectedImageUri,  640);
                Utils.saveBitmapToFile(thumbPath, bThumb, 50);
                Utils.saveBitmapToFile(photoPath, bPhoto, 50);
            } catch (FileNotFoundException e) {
                e.printStackTrace();
                // ErrorReporter.getInstance().handleSilentException(e);
                return;
            } catch (IOException e) {
                e.printStackTrace();
                // ErrorReporter.getInstance().handleSilentException(e);
                return;
            }
            
            // update the database record with the new photos
            mDb.updatePhoto(photoId, mTrigId, "", "", thumbPath, photoPath, PhotoSubject.NOSUBJECT, 0);
            Log.i(TAG, "Updated photo - " + photoId);
            
            // Update the gallery immediately to show the new thumbnail
            updateGallery();
            
            // edit the other fields for the new photo
            Intent i = new Intent(this, LogPhotoActivity.class);
            i.putExtra(DbHelper.PHOTO_ID, photoId);
            startActivityForResult(i, EDIT_PHOTO);
        } else {
            Log.w(TAG, "No image URI received from picker");
        }
    }

    
    
 
    private void populateFields() {
        Log.i(TAG, "populateFields");
        Cursor c = mDb.fetchLog(mTrigId);
        if (c == null) {return;}

        // Get column indices
        int yearIndex = c.getColumnIndex(DbHelper.LOG_YEAR);
        int monthIndex = c.getColumnIndex(DbHelper.LOG_MONTH);
        int dayIndex = c.getColumnIndex(DbHelper.LOG_DAY);
        int commentIndex = c.getColumnIndex(DbHelper.LOG_COMMENT);
        int gridrefIndex = c.getColumnIndex(DbHelper.LOG_GRIDREF);

        // set Date
        if (yearIndex >= 0 && monthIndex >= 0 && dayIndex >= 0) {
            mDate.init(c.getInt(yearIndex), c.getInt(monthIndex), c.getInt(dayIndex), this);
        }

        // set Text fields        
        if (commentIndex >= 0) {
            mComment.setText(c.getString(commentIndex));
        }
        if (gridrefIndex >= 0) {
            String gridref = c.getString(gridrefIndex);
            mGridref.setText(gridref);
        }
        
        int fbIndex = c.getColumnIndex(DbHelper.LOG_FB);
        if (fbIndex >= 0) {
            mFb.setText(c.getString(fbIndex));
        }
        
        // Pre-populate grid reference with GPS if blank and GPS available
        if (gridrefIndex >= 0) {
            String gridref = c.getString(gridrefIndex);
            if (gridref == null || gridref.trim().isEmpty()) {
                tryPrePopulateGridReference();
            }
        }
        
        // set Time
        int sendTimeIndex = c.getColumnIndex(DbHelper.LOG_SENDTIME);
        if (sendTimeIndex >= 0) {
            mSendTime.setChecked(c.getInt(sendTimeIndex) > 0);
        }
        
        int hourIndex = c.getColumnIndex(DbHelper.LOG_HOUR);
        int minuteIndex = c.getColumnIndex(DbHelper.LOG_MINUTES);
        if (hourIndex >= 0 && minuteIndex >= 0) {
            // Suppress deprecation warnings for TimePicker methods - these still work
            @SuppressWarnings("deprecation")
            int hour = c.getInt(hourIndex);
            @SuppressWarnings("deprecation")
            int minute = c.getInt(minuteIndex);
            // Note: setCurrentHour/setCurrentMinute are deprecated but functional
        }
        updateTimeVisibility();
        
        // set Flags
        int adminFlagIndex = c.getColumnIndex(DbHelper.LOG_FLAGADMINS);
        int userFlagIndex = c.getColumnIndex(DbHelper.LOG_FLAGUSERS);
        if (adminFlagIndex >= 0) {
            mAdminFlag.setChecked(c.getInt(adminFlagIndex) > 0);
        }
        if (userFlagIndex >= 0) {
            mUserFlag.setChecked(c.getInt(userFlagIndex) > 0);
        }
        
        // set Score: stored value is number of half-stars (1..10); convert to 0.5..5.0
        int scoreIndex = c.getColumnIndex(DbHelper.LOG_SCORE);
        if (scoreIndex >= 0) {
            int storedScore = c.getInt(scoreIndex);
            float stars = Math.max(0.5f, Math.min(5.0f, storedScore / 2f));
            mScore.setRating(stars);
        }
        
        // set Condition
        int conditionIndex = c.getColumnIndex(DbHelper.LOG_CONDITION);
        if (conditionIndex >= 0) {
            Condition cond = Condition.fromCode(c.getString(conditionIndex));
            mCondition.setSelection(Arrays.asList(Condition.values()).indexOf(cond));
        }

        c.close();
        
        updateGallery();
        checkDistance();

        }
    
    private void updateTimeVisibility () {
        mTime.setEnabled(mSendTime.isChecked());
    }

    
    private void updateGallery() {
        Log.i(TAG, "updateGallery() called for trigId: " + mTrigId);
        mPhotos = new ArrayList<TrigPhoto>(); 
        Cursor c = mDb.fetchPhotos(mTrigId);
        if (c != null && c.moveToFirst()) {
            Log.d(TAG, "Found photos in database, processing...");
            int count = 0;
            do {
                TrigPhoto photo = new TrigPhoto();
                int photoIdIndex = c.getColumnIndex(DbHelper.PHOTO_ID);
                int iconUrlIndex = c.getColumnIndex(DbHelper.PHOTO_ICON);
                int photoUrlIndex = c.getColumnIndex(DbHelper.PHOTO_PHOTO);
                
                if (photoIdIndex < 0 || iconUrlIndex < 0 || photoUrlIndex < 0) {
                    continue; // Skip this photo if columns are missing
                }
                
                long photoId = c.getLong(photoIdIndex);
                String iconUrl = c.getString(iconUrlIndex);
                String photoUrl = c.getString(photoUrlIndex);
                // Only include photos that have valid thumbnail and photo paths
                if (iconUrl != null && !iconUrl.trim().isEmpty() && new File(iconUrl).exists()) {
                    photo.setLogID(photoId);
                    photo.setIconURL(iconUrl);
                    photo.setPhotoURL(photoUrl);
                    mPhotos.add(photo);
                    count++;
                    Log.d(TAG, "Added photo to gallery - ID: " + photoId + ", Icon: " + iconUrl);
                } else {
                    Log.w(TAG, "Skipping photo ID " + photoId + " due to missing or invalid thumbnail: '" + iconUrl + "'");
                }
            } while (c.moveToNext());
            c.close();
            Log.i(TAG, "Added " + count + " photos to gallery list");
        } else {
            Log.d(TAG, "No photos found in database for trigId: " + mTrigId);
        }
        
        // Ensure grid spans are appropriate for current width/orientation
        RecyclerView.LayoutManager lm = mGallery.getLayoutManager();
        if (lm instanceof GridLayoutManager) {
            ((GridLayoutManager) lm).setSpanCount(computePhotoGridSpanCount());
        }

        Log.d(TAG, "Setting adapter with " + mPhotos.size() + " photos");
        mGallery.setAdapter(new LogTrigRecyclerAdapter(this, mPhotos.toArray(new TrigPhoto[mPhotos.size()])));
        Log.d(TAG, "Gallery adapter updated successfully");
        adjustGalleryHeight();
    }

    private int computePhotoGridSpanCount() {
        final float density = getResources().getDisplayMetrics().density;
        int screenWidthPx = getResources().getDisplayMetrics().widthPixels;
        // Target ~3 columns in portrait (~120dp tiles + margins), more in landscape
        int desiredItemPx = (int) (124 * density); // 120dp tile + ~4dp margin
        int span = Math.max(2, screenWidthPx / Math.max(1, desiredItemPx));
        return span;
    }

    private void adjustGalleryHeight() {
        try {
            RecyclerView.LayoutManager lm = mGallery.getLayoutManager();
            if (!(lm instanceof GridLayoutManager)) { return; }
            GridLayoutManager glm = (GridLayoutManager) lm;
            int span = glm.getSpanCount();
            int count = (mPhotos != null) ? mPhotos.size() : 0;
            if (count <= 0 || span <= 0) {
                // No photos; let layout wrap content naturally
                android.view.ViewGroup.LayoutParams lp = mGallery.getLayoutParams();
                lp.height = android.view.ViewGroup.LayoutParams.WRAP_CONTENT;
                mGallery.setLayoutParams(lp);
                return;
            }
            int rows = (count + span - 1) / span;
            float density = getResources().getDisplayMetrics().density;
            // Tile height plus vertical margins (4dp top + 4dp bottom). Keep in sync with adapter
            int itemHeightPx = (int) (120 * density) + (int) (8 * density);
            int desiredHeight = rows * itemHeightPx;
            android.view.ViewGroup.LayoutParams lp = mGallery.getLayoutParams();
            if (lp.height != desiredHeight) {
                lp.height = desiredHeight;
                mGallery.setLayoutParams(lp);
            }
        } catch (Exception ignored) {}
    }

    public void reloadPhotos() {
        try {
            updateGallery();
        } catch (Exception e) {
            Log.e(TAG, "Failed to reload photos", e);
        }
    }

    
    
    private void saveLog() {
        Log.i(TAG, "saveLog");
        
        // Only save the log if one already exists
        if (!mHaveLog) {return;}
        
        // Save the changes to the database
        try {
            mDb.mDb.beginTransaction();
            mDb.deleteLog(mTrigId);
            // Suppress deprecation warnings for TimePicker methods
            @SuppressWarnings("deprecation")
            int currentHour = mTime.getCurrentHour();
            @SuppressWarnings("deprecation")
            int currentMinute = mTime.getCurrentMinute();
            
            mDb.createLog(mTrigId, 
                mDate.getYear(), 
                mDate.getMonth(), 
                mDate.getDayOfMonth(),
                mSendTime.isChecked()?1:0,
                currentHour, 
                currentMinute, 
                mGridref.getText().toString(), 
                mFb.getText().toString(), 
                (Condition) mCondition.getSelectedItem(), 
                // Save number of half-stars (1..10)
                Math.max(1, Math.min(10, Math.round(mScore.getRating() * 2f))), 
                mComment.getText().toString(),
                mAdminFlag.isChecked()?1:0, 
                mUserFlag.isChecked()?1:0);
            
            mDb.mDb.setTransactionSuccessful();
            Log.i(TAG, "Transaction Successful");
        } catch (Exception e) {
            Log.i(TAG, "Transaction rolled back");
        } finally {
            mDb.mDb.endTransaction();
        }
    }


    private void createNewLog() {
        // Create an empty log with default values for time, score etc
        Calendar now = Calendar.getInstance();
        mDb.createLog(mTrigId, 
                now.get(Calendar.YEAR), 
                now.get(Calendar.MONTH), 
                now.get(Calendar.DAY_OF_MONTH),
                1,
                now.get(Calendar.HOUR_OF_DAY), 
                now.get(Calendar.MINUTE), 
                "", 
                "", 
                Condition.CONDITIONNOTLOGGED, 
                6, // Start with 3.0 stars default -> 6 half-stars
                "", 
                0, 
                0);
    }
    
    public void deleteLog() {
        // delete log records from DB
        mDb.deleteLog(mTrigId);
        // delete image files from filesystem
        Cursor c = mDb.fetchPhotos(mTrigId);
        if (c!=null) {
            do {
                int photoIndex = c.getColumnIndex(DbHelper.PHOTO_PHOTO);
                int iconIndex = c.getColumnIndex(DbHelper.PHOTO_ICON);
                if (photoIndex >= 0) {
                    new File(c.getString(photoIndex)).delete();
                }
                if (iconIndex >= 0) {
                    new File(c.getString(iconIndex)).delete();
                }
            } while (c.moveToNext());
        }
        // delete photo records from DB
        mDb.deletePhotosForTrig(mTrigId);
    }

    
    
    public void uploadLog() {
        Log.i(TAG, "uploadLog");
        
        // check for login credentials in prefs
        
        // Check if user is logged in with Auth0 before syncing
        uk.trigpointing.android.api.AuthPreferences authPreferences = new uk.trigpointing.android.api.AuthPreferences(this);
        
        if (!authPreferences.isLoggedIn()) {
            Toast.makeText(this, R.string.toastPleaseLogin, Toast.LENGTH_LONG).show();
            return;
        }
        
        saveLog();
        new SyncTask(LogTrigActivity.this, this).execute(false, mTrigId);
    }

    
    @Override
    public void onDateChanged(DatePicker view, int pYear, int pMonth, int pDay) {
        Log.i(TAG, "Date changed");
        Calendar cal = Calendar.getInstance();
        int day = cal.get(Calendar.DATE);
        int month = cal.get(Calendar.MONTH);
        int year = cal.get(Calendar.YEAR);
        if ( pYear  > year
        || ( pYear == year && pMonth  > month)
        || ( pYear == year && pMonth == month && pDay > day) ) {
            view.updateDate(year, month, day);
        }
    }

    
    
    
       private void getLocation() {
            Log.i(TAG, "Start location listener and put up dialog box");
            mLocationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
            
            if (!mLocationManager.isProviderEnabled (LocationManager.GPS_PROVIDER)) {
                Log.w(TAG, "GPS not enabled in system settings!");
                Toast.makeText(this, "Please enable GPS in system settings!", Toast.LENGTH_LONG).show();
                return;
            }

            // Start listening for GPS updates
           if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
               ActivityCompat.requestPermissions(this,
                       new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION},
                       REQ_LOCATION);
               return;
           }
           mLocationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000L,1.0f, this);

           LinearLayout container = new LinearLayout(this);
           container.setOrientation(LinearLayout.VERTICAL);
           int paddingPx = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 24,
                    getResources().getDisplayMetrics());
           container.setPadding(paddingPx, paddingPx, paddingPx, paddingPx);

           mProgressText = new TextView(this);
           mProgressText.setText(getString(R.string.getting_gps_fix));
           container.addView(mProgressText, new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

           mProgressBar = new ProgressBar(this);
           mProgressBar.setIndeterminate(true);
           container.addView(mProgressBar, new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

           mProgressDialog = new AlertDialog.Builder(this)
                    .setView(container)
                    .setCancelable(true)
                    .create();
           mProgressDialog.show();
           mProgressDialog.setOnCancelListener (new DialogInterface.OnCancelListener() {
                @Override
                public void onCancel(DialogInterface arg0) {
                    Toast.makeText(LogTrigActivity.this, "Cancelled Get location", Toast.LENGTH_SHORT).show();
                    mLocationManager.removeUpdates(LogTrigActivity.this);
                }});
        }
        

    
    @Override
    public void onLocationChanged(Location location) {
        // Found GPS location, so cancel dialog, update the GUI, and stop listening for locations

        LatLon ll = new LatLon(location);
        mGridref.setText(ll.getOSGB10());
        checkDistance();
        
        mProgressDialog.dismiss();
        Toast.makeText(this, "GPS location added", Toast.LENGTH_SHORT).show();

        mLocationManager.removeUpdates(this);
    }

    @Override
    public void onProviderDisabled(String provider) {
        Log.i(TAG, "GPS provider disabled");
        mProgressDialog.cancel();
    }

    @Override
    public void onProviderEnabled(String provider) {
        Log.i(TAG, "GPS provider enabled");
    }

    @Override
    public void onStatusChanged(String provider, int status, Bundle extras) {
    }

    @Override
    public void onSynced(int status) {
        if (status == SyncTask.SUCCESS) {
            mSwitcher.setDisplayedChild(0); // Show button after successful sync
            mHaveLog = false;
            if (mScroll != null) { mScroll.fullScroll(View.FOCUS_UP); }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_LOCATION) {
            boolean granted = false;
            if (grantResults != null && grantResults.length > 0) {
                for (int res : grantResults) {
                    if (res == PackageManager.PERMISSION_GRANTED) {
                        granted = true;
                        break;
                    }
                }
            }
            if (granted) {
                getLocation();
            } else {
                Toast.makeText(this, "Location permission denied", Toast.LENGTH_LONG).show();
            }
        } else if (requestCode == 3001) {
            // Photo permission request (Android 10 and below only)
            if (grantResults != null && grantResults.length > 0 
                    && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Log.i(TAG, "READ_EXTERNAL_STORAGE permission granted");
                choosePhoto();
            } else {
                Log.w(TAG, "READ_EXTERNAL_STORAGE permission denied");
                Toast.makeText(this, "Photo access permission denied. Please grant storage permission to select photos.", Toast.LENGTH_LONG).show();
            }
        }
    }
}




