package uk.trigpointing.android;

// import org.acra.ErrorReporter;

import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Resources.NotFoundException;
import android.database.SQLException;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.CompletableFuture;
import android.os.Bundle;
import androidx.preference.PreferenceManager;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.activity.OnBackPressedCallback;
import androidx.core.content.ContextCompat;
import uk.trigpointing.android.common.BaseActivity;
import uk.trigpointing.android.common.ClearCacheTask;
import uk.trigpointing.android.logging.SyncListener;
import uk.trigpointing.android.logging.SyncTask;
import uk.trigpointing.android.mapping.DownloadMapsActivity;
import uk.trigpointing.android.nearest.NearestActivity;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import uk.trigpointing.android.api.AuthPreferences;
import uk.trigpointing.android.api.Auth0Config;
import uk.trigpointing.android.api.TrigApiClient;
import uk.trigpointing.android.filter.Filter;
import coil.Coil;
import coil.ImageLoader;
import coil.request.ImageRequest;
import coil.request.ImageResult;
import coil.target.Target;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import android.graphics.drawable.Drawable;
import coil.memory.MemoryCache;
import coil.disk.DiskCache;
import java.util.Collections;

public class MainActivity extends BaseActivity implements SyncListener {
    public static final String     TAG ="MainActivity";
    private static final String RUNBEFORE = "RUNBEFORE";
    private static final String AUTO_SYNC_RUN = "AUTO_SYNC_RUN";
    private SharedPreferences     mPrefs;

    private TextView            mPillarCount;
    private TextView            mFbmCount;
    private TextView            mPassiveCount;
    private TextView            mIntersectedCount;
    private TextView            mUnsyncedCount;
    private TextView            mPhotosCount;
    private Button                mSyncBtn;
    private Button                mARViewBtn;
    private TextView            mUserName;
    private ImageView            mUserMapImage;
    
    // Auth0 authentication components
    private AuthPreferences authPreferences;
    private Auth0Config auth0Config;
    private TrigApiClient trigApiClient;
    
    // Modern activity result launchers
    private ActivityResultLauncher<Intent> nearestLauncher;
    private ActivityResultLauncher<Intent> mapLauncher;
    private ActivityResultLauncher<Intent> preferencesLauncher;
    
     @Override
    public void onCreate(Bundle savedInstanceState) {
        Log.i(TAG, "onCreate: Starting MainActivity");
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);
        
        Log.i(TAG, "onCreate: Setting up UI components");
        // Set up UI components
        mPillarCount = findViewById(R.id.countPillarText);
        mFbmCount = findViewById(R.id.countFbmText);
        mPassiveCount = findViewById(R.id.countPassiveText);
        mIntersectedCount = findViewById(R.id.countIntersectedText);
        mUnsyncedCount = findViewById(R.id.countUnsyncedText);
        mPhotosCount = findViewById(R.id.countPhotosText);
        mSyncBtn = findViewById(R.id.btnSync);
        mARViewBtn = findViewById(R.id.btnARView);
        mUserName = findViewById(R.id.txtUserName);
        mUserMapImage = findViewById(R.id.userMapImage);
        
        Log.i(TAG, "onCreate: Setting up preferences");
        mPrefs = getSharedPreferences("TrigpointingUK", MODE_PRIVATE);
        
        // Check if this is a fresh app start and reset logging status filter if needed
        resetLoggingStatusFilterOnAppStart();
        
        // Initialize Auth0 authentication components
        authPreferences = new AuthPreferences(this);
        auth0Config = new Auth0Config(this);
        trigApiClient = new TrigApiClient(this);
        
        // Reset auto sync flag on app startup
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        prefs.edit().putBoolean(AUTO_SYNC_RUN, false).apply();
        Log.i(TAG, "onCreate: Reset auto sync flag for new app session");
        
        Log.i(TAG, "onCreate: Setting up activity result launchers");
        setupActivityResultLaunchers();
        
        Log.i(TAG, "onCreate: Setting up click listeners");
        setupClickListeners();
        
        Log.i(TAG, "onCreate: Setting up back button handling");
        setupBackButtonHandling();
        
        Log.i(TAG, "onCreate: Updating user display");
        updateUserDisplay();
        if (authPreferences.isLoggedIn() && authPreferences.getApiUserId() == null) {
            Log.i(TAG, "onCreate: API user profile missing, fetching from server");
            fetchAndStoreApiUser();
        }
        
        Log.i(TAG, "onCreate: Populating counts");
        populateCounts();
        
        Log.i(TAG, "onCreate: Checking for OS API key");
        checkAndFetchOsApiKey();
        
        Log.i(TAG, "onCreate: MainActivity setup complete");
    }

    private void checkAndFetchOsApiKey() {
        CompletableFuture.runAsync(() -> {
            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
            String osApiKey = prefs.getString("os_api_key", "");

            if (osApiKey != null && !osApiKey.trim().isEmpty()) {
                Log.i(TAG, "OS API key already exists.");
                return;
            }

            Log.i(TAG, "OS API key is missing. Fetching from URL...");
            OkHttpClient client = new OkHttpClient();
            Request request = new Request.Builder()
                    .url("https://trigpointinguk-maps.s3.eu-west-1.amazonaws.com/OS_API_KEY.txt")
                    .build();

            try (Response response = client.newCall(request).execute()) {
                if (response.isSuccessful() && response.body() != null) {
                    String apiKey = response.body().string().trim();
                    if (!apiKey.isEmpty()) {
                        prefs.edit().putString("os_api_key", apiKey).apply();
                        Log.i(TAG, "OS API key fetched and saved successfully.");
                        runOnUiThread(() -> Toast.makeText(MainActivity.this, "OS API Key updated.", Toast.LENGTH_SHORT).show());
                    } else {
                        Log.e(TAG, "Fetched OS API key is empty.");
                        runOnUiThread(() -> Toast.makeText(MainActivity.this, "Failed to update OS API Key: Key file is empty.", Toast.LENGTH_LONG).show());
                    }
                } else {
                    Log.e(TAG, "Failed to fetch OS API key. Response code: " + response.code());
                    runOnUiThread(() -> Toast.makeText(MainActivity.this, "Failed to update OS API Key: Server error.", Toast.LENGTH_LONG).show());
                }
            } catch (IOException e) {
                Log.e(TAG, "Error checking or fetching OS API key", e);
                runOnUiThread(() -> Toast.makeText(MainActivity.this, "Failed to update OS API Key: Network error.", Toast.LENGTH_LONG).show());
            }
        }, executor);
    }

    private void setupActivityResultLaunchers() {
        nearestLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {}
        );
        
        mapLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {}
        );

        // Handle result from FilterActivity
        // Handle successful result
        ActivityResultLauncher<Intent> searchLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {}
        );
        
        preferencesLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                // Handle result from PreferencesActivity
                if (result.getResultCode() == RESULT_OK) {
                    // Refresh UI after preferences change
                    updateUserDisplay();
                    populateCounts();
                }
            }
        );
    }
    
    private void setupClickListeners() {
        final Button btnNearest = findViewById(R.id.btnNearest);
        btnNearest.setOnClickListener(arg0 -> {
            Intent i = new Intent(MainActivity.this, NearestActivity.class);
            nearestLauncher.launch(i);
        });
        mSyncBtn.setOnClickListener(arg0 -> doSync());

        final Button btnLeafletMap = findViewById(R.id.btnLeafletMap);
        btnLeafletMap.setOnClickListener(v -> {
            Intent i = new Intent(MainActivity.this, uk.trigpointing.android.mapping.LeafletMapActivity.class);
            mapLauncher.launch(i);
        });

        // AR View button
        mARViewBtn.setOnClickListener(v -> {
            Intent i = new Intent(MainActivity.this, uk.trigpointing.android.ar.SensorARActivity.class);
            startActivity(i);
        });

    }
    
    private void setupBackButtonHandling() {
        // Handle back button press to show exit confirmation dialog
        OnBackPressedCallback callback = new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                showExitConfirmationDialog();
                Log.i(TAG, "Back button pressed on main activity - showing exit confirmation");
            }
        };
        getOnBackPressedDispatcher().addCallback(this, callback);
        Log.i(TAG, "setupBackButtonHandling: Back button handling configured");
    }
    
    private void showExitConfirmationDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Exit Application");
        builder.setMessage("Are you sure you want to exit the application?");
        
        builder.setPositiveButton("Exit", (dialog, which) -> {
            Log.i(TAG, "User confirmed exit - finishing activity");
            finish();
        });
        
        builder.setNegativeButton("Stay", (dialog, which) -> {
            Log.i(TAG, "User chose to stay in the application");
            dialog.dismiss();
        });
        
        AlertDialog dialog = builder.create();
        dialog.show();
    }


    @Override
    protected void onSaveInstanceState(Bundle outState) {
        outState.putBoolean(RUNBEFORE, true);
        super.onSaveInstanceState(outState);
    }
    
    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (executor != null && !executor.isShutdown()) {
            executor.shutdown();
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.mainmenu, menu);

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        boolean devMode = prefs.getBoolean("dev_mode", false);
        
        // Check Auth0 authentication only
        boolean loggedIn = authPreferences.isLoggedIn();

        // These items are always visible, so no changes needed for them.
        // menu.findItem(R.id.action_settings).setVisible(true);
        // menu.findItem(R.id.action_about).setVisible(true);

        // These items are only visible in developer mode.
        menu.findItem(R.id.action_refresh).setVisible(devMode);
        menu.findItem(R.id.action_clearcache).setVisible(devMode);
        menu.findItem(R.id.action_exit).setVisible(devMode);

        menu.findItem(R.id.action_login).setVisible(!loggedIn);
        menu.findItem(R.id.action_logout).setVisible(loggedIn);

        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int itemId = item.getItemId();

        if (itemId == R.id.action_settings) {
            startActivity(new Intent(this, SettingsActivity.class));
            return true;
        } else if (itemId == R.id.action_about) {
            startActivity(new Intent(this, AboutActivity.class));
            return true;
        } else if (itemId == R.id.action_login) {
            showLoginDialog();
            return true;
        } else if (itemId == R.id.action_logout) {
            doLogout();
            return true;
        } else if (itemId == R.id.action_refresh) {
            startActivity(new Intent(this, DownloadTrigsActivity.class));
            return true;
        } else if (itemId == R.id.action_clearcache) {
            new ClearCacheTask(this).execute();
            return true;
        } else if (itemId == R.id.action_exit) {
            finish();
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    /**
     * Show Auth0 login - launches browser-based authentication
     */
    private void showLoginDialog() {
        Log.i(TAG, "showLoginDialog: Starting Auth0 universal login");
        
        auth0Config.login(new Auth0Config.Auth0Callback() {
            @Override
            public void onSuccess(com.auth0.android.result.Credentials credentials, com.auth0.android.result.UserProfile userProfile) {
                runOnUiThread(() -> {
                    try {
                        String userName = "Unknown";
                        if (userProfile != null && userProfile.getName() != null) {
                            userName = userProfile.getName();
                        }
                        Log.i(TAG, "Auth0 login successful for user: " + userName);
                        
                        // Store the Auth0 authentication data
                        authPreferences.storeAuth0Data(credentials, userProfile);
                        fetchAndStoreApiUser();
                        
                        // Update UI
                        updateUserDisplay();
                        invalidateOptionsMenu();
                        
                        Toast.makeText(MainActivity.this, "Login successful!", Toast.LENGTH_SHORT).show();
                        
                        // Start sync after successful login
                        doSync();
                    } catch (Exception e) {
                        Log.e(TAG, "Error in Auth0 login success callback", e);
                        Toast.makeText(MainActivity.this, "Login error: " + e.getMessage(), Toast.LENGTH_LONG).show();
                    }
                });
            }
            
            @Override
            public void onError(com.auth0.android.authentication.AuthenticationException error) {
                runOnUiThread(() -> {
                    Log.e(TAG, "Auth0 login failed", error);
                    Toast.makeText(MainActivity.this, "Login failed: " + error.getMessage(), Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    /**
     * Logout from Auth0 and clear local data
     */
    private void doLogout() {
        Log.i(TAG, "doLogout: Starting logout process");
        
        // Clear auth data immediately (synchronous)
        authPreferences.clearAuthData();
        
        // Also clear legacy username/password from default SharedPreferences
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        SharedPreferences.Editor editor = prefs.edit();
        editor.remove("username");
        editor.remove("plaintextpassword");
        editor.commit();  // Synchronous to ensure immediate clearing
        Log.i(TAG, "doLogout: Cleared legacy username/password");
        
        // Clear user logs from database
        DbHelper dbHelper = new DbHelper(this);
        dbHelper.open();
        dbHelper.clearUserLogs();
        dbHelper.close();
        
        // Update UI immediately
        updateUserDisplay();
        invalidateOptionsMenu();
        
        // Perform Auth0 logout (browser session cleanup)
        auth0Config.logout(new Auth0Config.LogoutCallback() {
            @Override
            public void onSuccess() {
                runOnUiThread(() -> {
                    Log.i(TAG, "Auth0 logout successful");
                    
                    // Update UI again to ensure it's refreshed after browser returns
                    updateUserDisplay();
                    invalidateOptionsMenu();
                    
                    Toast.makeText(MainActivity.this, "Logged out successfully", Toast.LENGTH_SHORT).show();
                });
            }
            
            @Override
            public void onError(com.auth0.android.authentication.AuthenticationException error) {
                runOnUiThread(() -> {
                    Log.w(TAG, "Auth0 logout failed (but local data cleared)", error);
                    
                    // Update UI even if Auth0 logout failed (local data is still cleared)
                    updateUserDisplay();
                    invalidateOptionsMenu();
                    
                    Toast.makeText(MainActivity.this, "Logged out (local session cleared)", Toast.LENGTH_SHORT).show();
                });
            }
        });
    }
 
    
    
    @Override
    protected void onResume() {
        Log.i(TAG, "onResume: Refreshing counts and user display");
        super.onResume();
        
        // Handle Auth0 callback if present
        if (getIntent().getData() != null) {
            Log.i(TAG, "onResume: Handling Auth0 callback with data: " + getIntent().getData());
            handleAuth0Callback(getIntent().getData());
        }
        
        invalidateOptionsMenu();
        updateUserDisplay();
        populateCounts();
        checkAndPopulateDatabase();
        checkAndPerformAutoSync();
    }
    
    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        Log.i(TAG, "onNewIntent: Received new intent with data: " + intent.getData());
        
        // Handle Auth0 callback
        if (intent.getData() != null) {
            Log.i(TAG, "onNewIntent: Handling Auth0 callback with data: " + intent.getData());
            handleAuth0Callback(intent.getData());
        }
    }


    private void doSync() {
        Log.i(TAG, "doSync");
        new SyncTask(MainActivity.this, MainActivity.this).execute(false);
    }
    

    @Override
    public void onSynced(int status) {
        Log.i(TAG, "onSynced");
        runOnUiThread(() -> {
            clearUserMapCache();
            populateCounts();
            updateUserDisplay();
        });
    }
    
    /**
     * Reset logging status filter to "Logged or not" on fresh app start.
     * This preserves the filter during session navigation but resets it when the app is truly restarted.
     */
    private void resetLoggingStatusFilterOnAppStart() {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        boolean isFreshStart = prefs.getBoolean("app_fresh_start", false);
        
        if (isFreshStart) {
            Log.i(TAG, "Fresh app start detected - resetting logging status filter to default");
            
            // Reset logging status filter to "Logged or not" (0)
            SharedPreferences.Editor editor = prefs.edit();
            editor.putInt(Filter.FILTERRADIO, 0); // Reset to "Logged or not"
            editor.putString(Filter.FILTERRADIOTEXT, "Logged or not"); // Update text for consistency
            editor.putString("leaflet_filter_found", "all"); // Update JavaScript compatibility value
            
            // Clear the fresh start flag so subsequent activity navigations don't reset the filter
            editor.putBoolean("app_fresh_start", false);
            editor.apply();
            
            Log.i(TAG, "Logging status filter reset to 'Logged or not' and fresh start flag cleared");
        } else {
            Log.d(TAG, "Not a fresh start - preserving current logging status filter");
        }
    }





    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    
    // Auth0 Methods
    
    /**
     * Handle Auth0 callback from browser redirect
     */
    private void handleAuth0Callback(android.net.Uri data) {
        Log.i(TAG, "handleAuth0Callback: Processing Auth0 callback with data: " + data);
        
        try {
            // Get the expected Auth0 domain from resources
            String expectedAuth0Domain = getString(R.string.auth0_domain);
            
            // Check if this is an Auth0 callback
            // The scheme will be "uk.trigpointing.android" for release or "uk.trigpointing.android.debug" for debug
            if (data.getScheme() != null && data.getScheme().startsWith("uk.trigpointing.android") &&
                data.getHost() != null && data.getHost().equals(expectedAuth0Domain)) {
                
                // Check if this is a logout callback (no code parameter) or login callback (has code parameter)
                String code = data.getQueryParameter("code");
                String error = data.getQueryParameter("error");
                
                if (code != null || error != null) {
                    // This is a login callback (has code or error)
                    Log.i(TAG, "handleAuth0Callback: Valid Auth0 login callback detected for domain: " + expectedAuth0Domain);
                    
                    // Create an Intent with the data and use WebAuthProvider.resume() to handle the callback
                    Intent intent = new Intent();
                    intent.setData(data);
                    com.auth0.android.provider.WebAuthProvider.resume(intent);
                    
                    Log.i(TAG, "handleAuth0Callback: Auth0 callback processed successfully");
                } else {
                    // This is a logout callback (no code parameter)
                    Log.i(TAG, "handleAuth0Callback: Logout callback detected, no action needed");
                    
                    // Still call resume in case Auth0 SDK needs to handle it
                    Intent intent = new Intent();
                    intent.setData(data);
                    com.auth0.android.provider.WebAuthProvider.resume(intent);
                }
                
                // Prevent re-processing the same callback on future resumes
                try {
                    getIntent().setData(null);
                } catch (Exception ignore) {
                }
            } else {
                Log.w(TAG, "handleAuth0Callback: Not an Auth0 callback, ignoring. Expected domain: " + expectedAuth0Domain + ", got: " + data.getHost());
            }
        } catch (Exception e) {
            Log.e(TAG, "handleAuth0Callback: Error processing Auth0 callback", e);
            Toast.makeText(this, "Error processing Auth0 callback: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }
    
    /**
     * Start Auth0 universal login flow
     */
    private void startAuth0Login() {
        Log.i(TAG, "Starting Auth0 universal login");
        
        try {
            auth0Config.login(new Auth0Config.Auth0Callback() {
                @Override
                public void onSuccess(com.auth0.android.result.Credentials credentials, com.auth0.android.result.UserProfile userProfile) {
                    runOnUiThread(() -> {
                        try {
                            String userName = "Unknown";
                            if (userProfile != null && userProfile.getName() != null) {
                                userName = userProfile.getName();
                            }
                            Log.i(TAG, "Auth0 login successful for user: " + userName);
                            
                            // Store the Auth0 authentication data
                            authPreferences.storeAuth0Data(credentials, userProfile);
                            
                            // Update UI
                            updateUserDisplay();
                            invalidateOptionsMenu();
                            
                            Toast.makeText(MainActivity.this, "Auth0 login successful!", Toast.LENGTH_SHORT).show();
                        } catch (Exception e) {
                            Log.e(TAG, "Error in Auth0 login success callback", e);
                            Toast.makeText(MainActivity.this, "Auth0 login error: " + e.getMessage(), Toast.LENGTH_LONG).show();
                        }
                    });
                }
                
                @Override
                public void onError(com.auth0.android.authentication.AuthenticationException error) {
                    runOnUiThread(() -> {
                        try {
                            Log.e(TAG, "Auth0 login failed", error);
                            Toast.makeText(MainActivity.this, "Auth0 login failed: " + error.getMessage(), Toast.LENGTH_LONG).show();
                        } catch (Exception e) {
                            Log.e(TAG, "Error in Auth0 login error callback", e);
                            Toast.makeText(MainActivity.this, "Auth0 login error: " + e.getMessage(), Toast.LENGTH_LONG).show();
                        }
                    });
                }
            });
        } catch (Exception e) {
            Log.e(TAG, "Error starting Auth0 login", e);
            Toast.makeText(this, "Error starting Auth0 login: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }
    
    /**
     * Show Auth0 user debug information
     */
    private void showAuth0UserDebug() {
        Log.i(TAG, "Showing Auth0 user debug information");
        
        try {
            if (!authPreferences.isAuth0LoggedIn()) {
                Toast.makeText(this, "Not logged in with Auth0", Toast.LENGTH_SHORT).show();
                return;
            }
        
        com.auth0.android.result.UserProfile userProfile = authPreferences.getAuth0UserProfile();
        String accessToken = authPreferences.getAuth0AccessToken();
        String idToken = authPreferences.getAuth0IdToken();
        String refreshToken = authPreferences.getAuth0RefreshToken();
        String tokenType = authPreferences.getAuth0TokenType();
        long expiresAt = authPreferences.getAuth0ExpiresAt();
        long loginTimestamp = authPreferences.getAuth0LoginTimestamp();
        
        StringBuilder debugInfo = new StringBuilder();
        debugInfo.append("Auth0 User Debug Information\n\n");
        
        if (userProfile != null) {
            debugInfo.append("User Profile:\n");
            debugInfo.append("  ID: ").append(userProfile.getId()).append("\n");
            debugInfo.append("  Name: ").append(userProfile.getName()).append("\n");
            debugInfo.append("  Email: ").append(userProfile.getEmail()).append("\n");
            debugInfo.append("  Nickname: ").append(userProfile.getNickname()).append("\n");
            debugInfo.append("  Picture: ").append(userProfile.getPictureURL()).append("\n");
            debugInfo.append("  Created: ").append(userProfile.getCreatedAt()).append("\n");
            debugInfo.append("  Updated: ").append("N/A").append("\n");
            debugInfo.append("  Email Verified: ").append(userProfile.isEmailVerified()).append("\n");
            debugInfo.append("  User Metadata: ").append(userProfile.getUserMetadata()).append("\n");
            debugInfo.append("  App Metadata: ").append(userProfile.getAppMetadata()).append("\n");
            debugInfo.append("\n");
        }
        
        debugInfo.append("Tokens:\n");
        debugInfo.append("  Access Token: ").append(accessToken != null ? accessToken.substring(0, Math.min(50, accessToken.length())) + "..." : "null").append("\n");
        debugInfo.append("  ID Token: ").append(idToken != null ? idToken.substring(0, Math.min(50, idToken.length())) + "..." : "null").append("\n");
        debugInfo.append("  Refresh Token: ").append(refreshToken != null ? refreshToken.substring(0, Math.min(50, refreshToken.length())) + "..." : "null").append("\n");
        debugInfo.append("  Token Type: ").append(tokenType).append("\n");
        debugInfo.append("  Expires At: ").append(new java.util.Date(expiresAt)).append("\n");
        debugInfo.append("  Login Timestamp: ").append(new java.util.Date(loginTimestamp)).append("\n");

        // Decode JWT access token (header/payload) for debugging audience/issuer
        if (accessToken != null && accessToken.contains(".")) {
            try {
                String[] parts = accessToken.split("\\.");
                if (parts.length >= 2) {
                    java.util.Base64.Decoder urlDecoder = java.util.Base64.getUrlDecoder();
                    String headerJson = new String(urlDecoder.decode(parts[0]), java.nio.charset.StandardCharsets.UTF_8);
                    String payloadJson = new String(urlDecoder.decode(parts[1]), java.nio.charset.StandardCharsets.UTF_8);
                    debugInfo.append("\nAccess Token Claims (decoded):\n");
                    debugInfo.append(payloadJson).append("\n");
                    debugInfo.append("Header: ").append(headerJson).append("\n");
                }
            } catch (Exception e) {
                Log.w(TAG, "Failed to decode access token payload", e);
            }
        }
        
        // Show in a dialog
        new AlertDialog.Builder(this)
                .setTitle("Auth0 User Debug")
                .setMessage(debugInfo.toString())
                .setPositiveButton("OK", null)
                .show();
        } catch (Exception e) {
            Log.e(TAG, "Error showing Auth0 user debug", e);
            Toast.makeText(this, "Error showing Auth0 user debug: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }
    
    /**
     * Call Auth0 API endpoint
     */
    private void callAuth0Api() {
        Log.i(TAG, "Calling Auth0 API endpoint");
        
        try {
            if (!authPreferences.isAuth0LoggedIn()) {
                Toast.makeText(this, "Not logged in with Auth0", Toast.LENGTH_SHORT).show();
                return;
            }
        
        String accessToken = authPreferences.getAuth0AccessToken();
        if (accessToken == null) {
            Toast.makeText(this, "No Auth0 access token available", Toast.LENGTH_SHORT).show();
            return;
        }
        // TODO: Remove this log before release. It prints sensitive token data.
        Log.i(TAG, "Auth0 access token (plaintext): " + accessToken);
        
        // Show progress
        Toast.makeText(this, "Calling Auth0 API...", Toast.LENGTH_SHORT).show();
        
        executor.execute(() -> {
            try {
                OkHttpClient client = new OkHttpClient();
                Request request = new Request.Builder()
                        .url("https://api.trigpointing.uk/v1/users/me")
                        .addHeader("Authorization", "Bearer " + accessToken)
                        .build();
                
                try (Response response = client.newCall(request).execute()) {
                    String responseBody = response.body().string();
                    
                    runOnUiThread(() -> {
                        Log.i(TAG, "Auth0 API call completed with code: " + response.code());
                        
                        StringBuilder result = new StringBuilder();
                        result.append("Auth0 API Call Result\n\n");
                        result.append("Status Code: ").append(response.code()).append("\n");
                        result.append("Response Body:\n").append(responseBody);
                        
                        new AlertDialog.Builder(MainActivity.this)
                                .setTitle("Auth0 API Response")
                                .setMessage(result.toString())
                                .setPositiveButton("OK", null)
                                .show();
                    });
                }
            } catch (Exception e) {
                Log.e(TAG, "Auth0 API call failed", e);
                runOnUiThread(() -> {
                    Toast.makeText(MainActivity.this, "Auth0 API call failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
            }
        });
        } catch (Exception e) {
            Log.e(TAG, "Error calling Auth0 API", e);
            Toast.makeText(this, "Error calling Auth0 API: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }
    
    /**
     * Perform Auth0 logout
     */
    private void performAuth0Logout() {
        Log.i(TAG, "Performing Auth0 logout");
        
        auth0Config.logout(new Auth0Config.LogoutCallback() {
            @Override
            public void onSuccess() {
                runOnUiThread(() -> {
                    Log.i(TAG, "Auth0 logout successful");
                    
                    // Clear Auth0 data
                    authPreferences.clearAuthData();
                    
                    // Update UI
                    updateUserDisplay();
                    invalidateOptionsMenu();
                    
                    Toast.makeText(MainActivity.this, "Auth0 logout successful", Toast.LENGTH_SHORT).show();
                });
            }
            
            @Override
            public void onError(com.auth0.android.authentication.AuthenticationException error) {
                runOnUiThread(() -> {
                    Log.e(TAG, "Auth0 logout failed", error);
                    Toast.makeText(MainActivity.this, "Auth0 logout failed: " + error.getMessage(), Toast.LENGTH_LONG).show();
                });
            }
        });
    }
    
    private void updateUserDisplay() {
        Log.i(TAG, "updateUserDisplay: Updating user display");
        try {
            View countSection = findViewById(R.id.count_section);
            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
            boolean devMode = prefs.getBoolean("dev_mode", false);
            
            String displayName;
            boolean isLoggedIn = false;
            
            // Check Auth0 authentication only (no legacy fallback)
            if (authPreferences.isLoggedIn()) {
                Log.i(TAG, "updateUserDisplay: User is logged in via Auth0");
                
                if (devMode) {
                    displayName = authPreferences.getDisplayNameWithId();
                    Log.i(TAG, "updateUserDisplay: Developer mode - showing name with ID: " + displayName);
                } else {
                    displayName = authPreferences.getDisplayName();
                    Log.i(TAG, "updateUserDisplay: Normal mode - showing name: " + displayName);
                }
                isLoggedIn = true;
            } else {
                Log.i(TAG, "updateUserDisplay: No authentication found");
                displayName = getString(R.string.not_logged_in_status);
                isLoggedIn = false;
            }
            
            mUserName.setText(displayName);
            
            if (isLoggedIn) {
                countSection.setVisibility(View.VISIBLE);
                mSyncBtn.setVisibility(View.VISIBLE);
                updateUserMap();
            } else {
                countSection.setVisibility(View.INVISIBLE);
                mSyncBtn.setVisibility(View.INVISIBLE);
                mUserMapImage.setVisibility(View.GONE);
            }
            
                    // AR View is available to all users
        mARViewBtn.setVisibility(View.VISIBLE);
            
        } catch (Exception e) {
            Log.e(TAG, "updateUserDisplay: Error updating user display", e);
            e.printStackTrace();
            mUserName.setText(getString(R.string.not_logged_in_status));
        }
    }
    
    private void updateUserMap() {
        Log.i(TAG, "updateUserMap: Updating user map image");
        try {
            // Load map if we have a persisted API user id, even if token has expired
            Integer userId = authPreferences.getApiUserId();
            if (userId != null && userId > 0) {
                String mapUrl = BuildConfig.TRIG_API_BASE + "/v1/users/" + userId + "/map?height=400";
                String cacheKey = getUserMapCacheKey(userId);
                Log.i(TAG, "updateUserMap: Loading map for user ID " + userId + " from URL: " + mapUrl);
                
                // Load the image using Coil - simplest approach
                ImageRequest request = new ImageRequest.Builder(this)
                        .data(mapUrl)
                        .memoryCacheKey(cacheKey)
                        .diskCacheKey(cacheKey)
                        .target(mUserMapImage) // Use ImageView directly as target - Coil will handle setting the drawable
                        .placeholder(android.R.drawable.ic_menu_mapmode) // Show placeholder while loading
                        .error(android.R.drawable.ic_dialog_alert) // Show error icon if loading fails
                        .build();
                
                // Make ImageView visible and load the image
                mUserMapImage.setVisibility(View.VISIBLE);
                mUserMapImage.setBackgroundColor(android.graphics.Color.TRANSPARENT);
                
                // Load the image
                ImageLoader imageLoader = Coil.imageLoader(this);
                imageLoader.enqueue(request);
                
                Log.d(TAG, "updateUserMap: Image loading initiated for URL: " + mapUrl);
                
                // Add click listener to open full map view
                mUserMapImage.setOnClickListener(v -> {
                    String fullMapUrl = BuildConfig.TRIG_API_BASE + "/v1/users/" + userId + "/map?height=1500&t=" + System.currentTimeMillis();
                    android.content.Intent intent = new android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(fullMapUrl));
                    startActivity(intent);
                });
            } else {
                Log.i(TAG, "updateUserMap: No persisted user ID, hiding map");
                mUserMapImage.setVisibility(View.GONE);
            }
        } catch (Exception e) {
            Log.e(TAG, "updateUserMap: Error updating user map", e);
            e.printStackTrace();
            // Hide map on any error - this is non-fatal
            mUserMapImage.setVisibility(View.GONE);
        }
    }
    
    private String getUserMapCacheKey(int userId) {
        return "user_map_" + userId;
    }
    
    private void clearUserMapCache() {
        Integer userId = authPreferences.getApiUserId();
        if (userId == null || userId <= 0) {
            return;
        }
        String cacheKey = getUserMapCacheKey(userId);
        ImageLoader loader = Coil.imageLoader(this);
        MemoryCache memoryCache = loader.getMemoryCache();
        if (memoryCache != null) {
            memoryCache.remove(new MemoryCache.Key(cacheKey, java.util.Collections.emptyMap()));
        }
        DiskCache diskCache = loader.getDiskCache();
        if (diskCache != null) {
            diskCache.remove(cacheKey);
        }
        mUserMapImage.setImageDrawable(null);
    }

    private void populateCounts() {
        Log.i(TAG, "populateCounts: Starting count population");
        // Show loading state
        runOnUiThread(() -> {
            try {
                Log.i(TAG, "populateCounts: Setting loading state");
                mPillarCount.setText("");
                mFbmCount.setText("");
                mPassiveCount.setText("");
                mIntersectedCount.setText("");
                mUnsyncedCount.setText("");
                mPhotosCount.setText("");
                                mSyncBtn.setTextColor(ContextCompat.getColor(this, android.R.color.primary_text_light));
            } catch (NotFoundException e) {
                Log.e(TAG, "populateCounts: Error setting loading state", e);
                e.printStackTrace();
            }
        });
        
        // Run database operations in background
        CompletableFuture.supplyAsync(() -> {
            Log.i(TAG, "populateCounts: Starting database operations");
            DbHelper mDb = new DbHelper(MainActivity.this);
            try {
                Log.i(TAG, "populateCounts: Opening database");
                mDb.open();
                Log.i(TAG, "populateCounts: Counting pillars");
                int nPillar = mDb.countLoggedPillars();
                Log.i(TAG, "populateCounts: Counting FBMs");
                int nFbm = mDb.countLoggedFbms();
                Log.i(TAG, "populateCounts: Counting passives");
                int nPassive = mDb.countLoggedPassives();
                Log.i(TAG, "populateCounts: Counting intersected");
                int nIntersected = mDb.countLoggedIntersecteds();
                Log.i(TAG, "populateCounts: Counting unsynced");
                int nUnsynced = mDb.countUnsynced();
                Log.i(TAG, "populateCounts: Counting photos");
                int nPhotos = mDb.countPhotos();
                Log.i(TAG, "populateCounts: Closing database");
                mDb.close();
                
                Log.i(TAG, "populateCounts: Database counts - Pillars: " + nPillar + ", FBMs: " + nFbm + ", Passives: " + nPassive + ", Intersected: " + nIntersected + ", Unsynced: " + nUnsynced + ", Photos: " + nPhotos);
                return new int[]{nPillar, nFbm, nPassive, nIntersected, nUnsynced, nPhotos};
            } catch (SQLException e) {
                Log.e(TAG, "populateCounts: SQLException during database operations", e);
                e.printStackTrace();
                return new int[]{0, 0, 0, 0, 0, 0};
            } catch (Exception e) {
                Log.e(TAG, "populateCounts: Unexpected exception during database operations", e);
                e.printStackTrace();
                return new int[]{0, 0, 0, 0, 0, 0};
            }
        }, executor).thenAcceptAsync(counts -> {
            // Update UI on main thread
            runOnUiThread(() -> {
                try {
                    Log.i(TAG, "populateCounts: Updating UI with counts");
                    int nPillar = counts[0];
                    int nFbm = counts[1];
                    int nPassive = counts[2];
                    int nIntersected = counts[3];
                    int nUnsynced = counts[4];
                    int nPhotos = counts[5];
                    
                    mPillarCount.setText(String.valueOf(nPillar));
                    mFbmCount.setText(String.valueOf(nFbm));
                    mPassiveCount.setText(String.valueOf(nPassive));
                    mIntersectedCount.setText(String.valueOf(nIntersected));
                    
                    if (nUnsynced > 0) {
                        mUnsyncedCount.setText(String.valueOf(nUnsynced));
                                        mSyncBtn.setTextColor(ContextCompat.getColor(this, R.color.syncNow));
                    }
                    
                    if (nPhotos > 0) {
                        mPhotosCount.setText(String.valueOf(nPhotos));
                    }
                    
                    Log.i(TAG, "populateCounts: UI update complete");
                } catch (Exception e) {
                    Log.e(TAG, "populateCounts: Error updating UI", e);
                    e.printStackTrace();
                }
            });
        }).exceptionally(throwable -> {
            Log.e(TAG, "populateCounts: Exception in async operation", throwable);
            throwable.printStackTrace();
            return null;
        });
    }
    
    private void checkAndPopulateDatabase() {
        Log.i(TAG, "checkAndPopulateDatabase: Checking if database needs population");
        
        CompletableFuture.supplyAsync(() -> {
            DbHelper mDb = new DbHelper(MainActivity.this);
            try {
                // Open read-only to avoid SQLITE_BUSY when another writer exists
                mDb.openReadable();
                boolean isPopulated = mDb.isTrigTablePopulated();
                mDb.close();
                return isPopulated;
            } catch (android.database.sqlite.SQLiteDatabaseLockedException locked) {
                Log.w(TAG, "checkAndPopulateDatabase: Database locked, will treat as populated for now to avoid contention");
                return true; // avoid kicking off population while locked
            } catch (Exception e) {
                Log.e(TAG, "checkAndPopulateDatabase: Error checking database", e);
                return false;
            }
        }, executor).thenAcceptAsync(isPopulated -> {
            if (!isPopulated) {
                Log.i(TAG, "checkAndPopulateDatabase: Database is empty, starting automatic population");
                runOnUiThread(() -> {
                    Toast.makeText(MainActivity.this, "Database is empty. Starting automatic download...", Toast.LENGTH_LONG).show();
                    Intent intent = new Intent(MainActivity.this, DownloadTrigsActivity.class);
                    startActivity(intent);
                });
            } else {
                Log.i(TAG, "checkAndPopulateDatabase: Database is already populated");
            }
        });
    }
    
    private void checkAndPerformAutoSync() {
        Log.i(TAG, "checkAndPerformAutoSync: Checking if auto sync is enabled");
        
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        boolean autoSyncEnabled = prefs.getBoolean("auto_sync", false);
        boolean autoSyncAlreadyRun = prefs.getBoolean(AUTO_SYNC_RUN, false);
        
        Log.i(TAG, "checkAndPerformAutoSync: Auto sync enabled: " + autoSyncEnabled + ", already run: " + autoSyncAlreadyRun);
        
        if (autoSyncEnabled && !autoSyncAlreadyRun) {
            Log.i(TAG, "checkAndPerformAutoSync: Auto sync is enabled and not yet run, starting sync");
            // Check if user is logged in with Auth0
            if (authPreferences.isLoggedIn()) {
                Log.i(TAG, "checkAndPerformAutoSync: User logged in, performing auto sync");
                // Mark that auto sync has been run
                prefs.edit().putBoolean(AUTO_SYNC_RUN, true).apply();
                new SyncTask(MainActivity.this, MainActivity.this).execute(false);
            } else {
                Log.i(TAG, "checkAndPerformAutoSync: No credentials found, skipping auto sync");
            }
        } else if (autoSyncEnabled) {
            Log.i(TAG, "checkAndPerformAutoSync: Auto sync is enabled but already run this session");
        } else {
            Log.i(TAG, "checkAndPerformAutoSync: Auto sync is disabled");
        }
    }

    private void fetchAndStoreApiUser() {
        if (trigApiClient == null || !authPreferences.isLoggedIn()) {
            return;
        }
        Log.i(TAG, "fetchAndStoreApiUser: Fetching user profile from API");
        trigApiClient.getCurrentUser(new TrigApiClient.ApiCallback<TrigApiClient.UserProfile>() {
            @Override
            public void onSuccess(TrigApiClient.UserProfile result) {
                if (result != null) {
                    authPreferences.storeApiUser(result);
                    runOnUiThread(() -> {
                        updateUserDisplay();
                        invalidateOptionsMenu();
                    });
                }
            }

            @Override
            public void onError(String errorMessage) {
                Log.w(TAG, "fetchAndStoreApiUser: Failed to fetch API user profile: " + errorMessage);
            }
        });
    }


}

    
    