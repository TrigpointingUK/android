package uk.trigpointing.android.mapping;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.content.Intent;
import android.content.SharedPreferences;
import androidx.preference.PreferenceManager;
import android.util.Log;
import android.webkit.GeolocationPermissions;
import android.webkit.JavascriptInterface;
import android.view.Menu;
import android.view.MenuItem;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.widget.Toast;
import android.database.Cursor;

import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.tabs.TabLayout;
import com.google.android.material.tabs.TabLayoutMediator;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.text.DecimalFormat;
import java.util.Objects;

import uk.trigpointing.android.common.BaseActivity;
import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.viewpager2.widget.ViewPager2;
import androidx.webkit.WebViewClientCompat;

import uk.trigpointing.android.DbHelper;
import uk.trigpointing.android.R;
import uk.trigpointing.android.common.FileCache;
import uk.trigpointing.android.DownloadTrigsActivity;
import uk.trigpointing.android.filter.Filter;
import uk.trigpointing.android.mapping.DownloadMapsActivity;

public class LeafletMapActivity extends BaseActivity {
    private static final String TAG = "LeafletMapActivity";
    private WebView webView;
    private static final int REQ_LOCATION = 2001;
    private DbHelper dbHelper;
    private File mTileCacheDir;
    private boolean isWebViewLoaded = false;

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.leaflet_map);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        mTileCacheDir = new File(getCacheDir(), "map_tiles");
        if (!mTileCacheDir.exists()) {
            mTileCacheDir.mkdirs();
        }

        try {
            dbHelper = new DbHelper(this);
            dbHelper.open();
        } catch (Exception e) {
            Log.e(TAG, "Error opening database", e);
            Toast.makeText(this, "Error opening database", Toast.LENGTH_SHORT).show();
        }

        webView = findViewById(R.id.leafletWebView);
        WebSettings ws = webView.getSettings();
        ws.setJavaScriptEnabled(true);
        ws.setDomStorageEnabled(true);
        ws.setBuiltInZoomControls(true);
        ws.setDisplayZoomControls(false);
        ws.setAllowFileAccess(true);
        ws.setAllowFileAccessFromFileURLs(true);
        ws.setAllowUniversalAccessFromFileURLs(true);
        ws.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        ws.setCacheMode(WebSettings.LOAD_DEFAULT);
        ws.setDatabaseEnabled(true);
        ws.setGeolocationEnabled(true);

        webView.addJavascriptInterface(new LeafletPreferencesInterface(), "AndroidPrefs");

        webView.setWebViewClient(new WebViewClientCompat() {
            @Override
            public WebResourceResponse shouldInterceptRequest(@NonNull WebView view, @NonNull WebResourceRequest request) {
                String url = request.getUrl().toString();
                if (url == null) return super.shouldInterceptRequest(view, request);

                if (url.contains("tile.openstreetmap.org") || url.contains("api.os.uk") || url.contains("server.arcgisonline.com")) {
                    try {
                        String domain = request.getUrl().getHost();
                        String path = request.getUrl().getPath();
                        if (domain == null || path == null) return super.shouldInterceptRequest(view, request);
                        File tileFile = new File(mTileCacheDir, domain + path);

                        if (tileFile.exists()) {
                            Log.d(TAG, "Serving tile from local cache: " + tileFile.getPath());
                            InputStream inputStream = new FileInputStream(tileFile);
                            String mimeType = getMimeType(url);
                            // Use null encoding for binary content to avoid incorrect charset headers
                            return new WebResourceResponse(mimeType, null, inputStream);
                        } else {
                             Log.d(TAG, "Tile not in cache, fetching from network: " + url);
                             return fetchAndCacheTile(url, tileFile);
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "Error serving tile from cache", e);
                    }
                }

                return super.shouldInterceptRequest(view, request);
            }
        });

        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onGeolocationPermissionsShowPrompt(String origin, GeolocationPermissions.Callback callback) {
                callback.invoke(origin, true, false);
            }

            @Override
            public boolean onConsoleMessage(android.webkit.ConsoleMessage consoleMessage) {
                Log.d(TAG, "WebView Console: " + consoleMessage.message() + " -- From line "
                        + consoleMessage.lineNumber() + " of "
                        + consoleMessage.sourceId());
                return super.onConsoleMessage(consoleMessage);
            }
        });

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        String osKey = prefs.getString("os_api_key", "");
        String leafletMapStyle = normalizeMapStyle(prefs.getString("leaflet_map_style", "OpenStreetMap"));
        // Always use the persisted preference for initial style
        String initialStyle = leafletMapStyle;
        Log.d(TAG, "Using persisted map style preference: " + leafletMapStyle);
        
        String url = buildLeafletUrl(osKey, initialStyle);
        Log.d(TAG, "Built URL: " + url);
        try {
            if (ensureLocationPermission()) {
                webView.loadUrl(url);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error loading Leaflet page", e);
            Toast.makeText(this, "Error loading Leaflet page", Toast.LENGTH_LONG).show();
        }
    }

    private String normalizeMapStyle(String value) {
        if (value == null || value.trim().isEmpty()) return "OpenStreetMap";
        switch (value) {
            case "OpenStreetMap":
            case "Satellite":
            case "OS Digital":
            case "OS Paper":
                return value;
            // Backward compatibility mappings
            case "ESRI.WorldImagery":
                return "Satellite";
            case "OS Outdoor (3857)":
            case "OS Outdoor":
                return "OS Digital";
            case "OS Leisure (27700)":
            case "OS Leisure":
                return "OS Paper";
            default:
                return "OpenStreetMap";
        }
    }

    private void showCacheStatus() {
        new Thread(() -> {
            long[] stats = getDirectoryStats(mTileCacheDir);
            long totalSize = stats[0];
            long fileCount = stats[1];

            DecimalFormat df = new DecimalFormat("#.##");
            String sizeInMB = df.format((double) totalSize / (1024 * 1024));
            String message = "Tiles: " + fileCount + "\nSize: " + sizeInMB + " MB";

            runOnUiThread(() -> {
                android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(LeafletMapActivity.this);
                builder.setTitle("Cache Status");
                builder.setMessage(message);
                builder.setPositiveButton("OK", null);
                builder.show();
            });
        }).start();
    }

    private long[] getDirectoryStats(File directory) {
        long size = 0;
        long count = 0;
        File[] children = (directory != null && directory.isDirectory()) ? directory.listFiles() : null;
        if (children != null) {
            for (File file : children) {
                if (file.isFile()) {
                    size += file.length();
                    count++;
                } else if (file.isDirectory()) {
                    long[] subDirStats = getDirectoryStats(file);
                    size += subDirStats[0];
                    count += subDirStats[1];
                }
            }
        }
        return new long[]{size, count};
    }

    private WebResourceResponse fetchAndCacheTile(String urlString, File tileFile) {
        HttpURLConnection connection = null;
        try {
            URL url = new URL(urlString);
            connection = (HttpURLConnection) url.openConnection();
            connection.setConnectTimeout(10000);
            connection.setReadTimeout(15000);

            // Set a custom User-Agent to comply with tile server policies
            connection.setRequestProperty("User-Agent", "TrigpointingUK-Android-App/1.0");

            connection.connect();

            if (connection.getResponseCode() == HttpURLConnection.HTTP_OK) {
                // Ensure parent directories exist
                File parentDir = tileFile.getParentFile();
                if (parentDir != null && !parentDir.exists()) {
                    parentDir.mkdirs();
                }

                // Stream network data to file with proper closure
                try (InputStream inputStream = connection.getInputStream();
                     FileOutputStream fileOutputStream = new FileOutputStream(tileFile)) {
                    byte[] buffer = new byte[8 * 1024];
                    int bufferLength;
                    while ((bufferLength = inputStream.read(buffer)) != -1) {
                        fileOutputStream.write(buffer, 0, bufferLength);
                    }
                    fileOutputStream.flush();
                }

                // Now that it's cached, serve it from the file
                InputStream cachedInputStream = new FileInputStream(tileFile);
                String mimeType = getMimeType(urlString);
                // Use null encoding for binary content
                return new WebResourceResponse(mimeType, null, cachedInputStream);
            }
        } catch (IOException e) {
            Log.e(TAG, "Error fetching and caching tile", e);
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
        return null; // Let WebView handle the failed request
    }
    
    private String getMimeType(String url) {
        if (url.endsWith(".png")) {
            return "image/png";
        } else if (url.endsWith(".jpg") || url.endsWith(".jpeg")) {
            return "image/jpeg";
        }
        return "application/octet-stream";
    }

    private String buildLeafletUrl(String osKey, String leafletMapStyle) {
        StringBuilder url = new StringBuilder("file:///android_asset/leaflet/index.html");
        boolean hasParams = false;

        if (osKey != null && !osKey.isEmpty()) {
            try {
                // Use String parameter instead of Charset for backward compatibility (API < 33)
                url.append("?os_key=").append(java.net.URLEncoder.encode(osKey, StandardCharsets.UTF_8.name()));
            } catch (java.io.UnsupportedEncodingException e) {
                // UTF-8 is always supported, this should never happen
                Log.e(TAG, "UTF-8 encoding not supported", e);
                url.append("?os_key=").append(osKey); // fallback without encoding
            }
            hasParams = true;
        }

        if (leafletMapStyle != null && !leafletMapStyle.isEmpty()) {
            url.append(hasParams ? "&" : "?");
            try {
                // Use String parameter instead of Charset for backward compatibility (API < 33)
                url.append("initial_style=").append(java.net.URLEncoder.encode(leafletMapStyle, StandardCharsets.UTF_8.name()));
            } catch (java.io.UnsupportedEncodingException e) {
                // UTF-8 is always supported, this should never happen
                Log.e(TAG, "UTF-8 encoding not supported", e);
                url.append("initial_style=").append(leafletMapStyle); // fallback without encoding
            }
        }

        return url.toString();
    }

    private boolean ensureLocationPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, REQ_LOCATION);
            return false;
        }
        return true;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_LOCATION) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
                String osKey = prefs.getString("os_api_key", "");
                String leafletMapStyle = normalizeMapStyle(prefs.getString("leaflet_map_style", "OpenStreetMap"));
                String url = buildLeafletUrl(osKey, leafletMapStyle);
                webView.loadUrl(url);
            } else {
                Toast.makeText(this, "Location permission denied; My location will be disabled.", Toast.LENGTH_LONG).show();
            }
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.leaflet_map_menu, menu);

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        boolean devMode = prefs.getBoolean("dev_mode", false);

        if (menu.findItem(R.id.menu_clear_cache) != null) {
            menu.findItem(R.id.menu_clear_cache).setVisible(devMode);
        }
        if (menu.findItem(R.id.menu_cache_status) != null) {
            menu.findItem(R.id.menu_cache_status).setVisible(devMode);
        }

        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        } else if (item.getItemId() == R.id.menu_cache_status) {
            showCacheStatus();
            return true;
        } else if (item.getItemId() == R.id.menu_clear_cache) {
            // Clear both WebView tile cache and static map images cache
            clearAllCaches();
            return true;
        } else if (item.getItemId() == R.id.menu_download_tiles) {
            Intent intent = new Intent(this, DownloadMapsActivity.class);
            startActivity(intent);
            return true;
        }
        return super.onOptionsItemSelected(item);
    }



    // Callback methods for tab fragments
    public void updateMapStyle(String style) {
        String safe = org.json.JSONObject.quote(style);
        webView.evaluateJavascript("if (typeof switchToLayer === 'function') switchToLayer(" + safe + ");", null);
        Log.d(TAG, "Updated map style to: " + style);
    }

    public void updateMarkerColor(String color) {
        String safe = org.json.JSONObject.quote(color);
        webView.evaluateJavascript("if (typeof updateMarkerColors === 'function') updateMarkerColors(" + safe + ");", null);
        Log.d(TAG, "Updated marker colour to: " + color);
    }

    public void updateTrigpointType(String type) {
        String safe = org.json.JSONObject.quote(type);
        webView.evaluateJavascript("if (typeof updateTrigpointType === 'function') updateTrigpointType(" + safe + ");", null);
        Log.d(TAG, "Updated trigpoint type to: " + type);
    }
    
    public void updateFilterFound(String found) {
        String safe = org.json.JSONObject.quote(found);
        webView.evaluateJavascript("if (typeof updateFilterFound === 'function') updateFilterFound(" + safe + ");", null);
        Log.d(TAG, "Updated filter found to: " + found);
    }
    
    public void updateSessionMapStyle(String style) {
        String safe = org.json.JSONObject.quote(style);
        webView.evaluateJavascript("sessionStorage.setItem('leaflet_session_map_style', " + safe + ");", null);
        Log.d(TAG, "Updated session map style to: " + style);
    }

    private String queryTrigpoints(double south, double west, double north, double east, String trigpointType, String filterFound, String colorScheme) {
        if (dbHelper == null) {
            Log.e(TAG, "Database helper not initialised");
            return "[]";
        }

        try {
            // Convert leaflet filter names to Filter constants
            setupFilterPreferences(trigpointType, filterFound);
            
            // Create bounding box for query
            BoundingBox bounds = new BoundingBox(north, east, south, west);
            
            // Query database using bounding box
            Cursor cursor = dbHelper.fetchTrigMapList(bounds);
            
            if (cursor == null) {
                return "[]";
            }

            JSONArray trigpoints = new JSONArray();
            
            // Cache column indices and use OrThrow for required columns
            int idIdx = cursor.getColumnIndexOrThrow(DbHelper.TRIG_ID);
            int nameIdx = cursor.getColumnIndexOrThrow(DbHelper.TRIG_NAME);
            int latIdx = cursor.getColumnIndexOrThrow(DbHelper.TRIG_LAT);
            int lonIdx = cursor.getColumnIndexOrThrow(DbHelper.TRIG_LON);
            int typeIdx = cursor.getColumnIndexOrThrow(DbHelper.TRIG_TYPE);
            int condIdx = cursor.getColumnIndexOrThrow(DbHelper.TRIG_CONDITION);
            int loggedIdx = cursor.getColumnIndexOrThrow(DbHelper.TRIG_LOGGED);
            int markedIdx = cursor.getColumnIndex(DbHelper.JOIN_MARKED);

            while (cursor.moveToNext()) {
                JSONObject trig = new JSONObject();
                
                trig.put("id", cursor.getLong(idIdx));
                trig.put("name", cursor.getString(nameIdx));
                trig.put("lat", cursor.getDouble(latIdx));
                trig.put("lon", cursor.getDouble(lonIdx));
                trig.put("type", cursor.getString(typeIdx));
                trig.put("condition", cursor.getString(condIdx));
                trig.put("logged", cursor.getString(loggedIdx));
                
                // Check if flagged/marked
                String marked = (markedIdx != -1) ? cursor.getString(markedIdx) : null;
                trig.put("flagged", marked != null);
                
                trigpoints.put(trig);
            }
            
            cursor.close();
            
            Log.d(TAG, "Returning " + trigpoints.length() + " trigpoints");
            return trigpoints.toString();
            
        } catch (Exception e) {
            Log.e(TAG, "Error querying trigpoints", e);
            return "[]";
        }
    }

    private void setupFilterPreferences(String trigpointType, String filterFound) {
        // Convert JavaScript filter names to Filter preference values
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        SharedPreferences.Editor editor = prefs.edit();
        
        // Set trigpoint type filter based on trigpointType parameter
        switch (trigpointType) {
            case "all":
                editor.putInt(Filter.FILTERTYPE, 6); // TYPESALL
                break;
            case "pillars":
                editor.putInt(Filter.FILTERTYPE, 0); // TYPESPILLAR
                break;
            case "pillarsfbm":
                editor.putInt(Filter.FILTERTYPE, 1); // TYPESPILLARFBM
                break;
            case "fbm":
                editor.putInt(Filter.FILTERTYPE, 2); // TYPESFBM
                break;
            case "passive":
                editor.putInt(Filter.FILTERTYPE, 3); // TYPESPASSIVE
                break;
            case "intersected":
                editor.putInt(Filter.FILTERTYPE, 4); // TYPESINTERSECTED
                break;
            case "nointersected":
                editor.putInt(Filter.FILTERTYPE, 5); // TYPESNOINTERSECTED
                break;
            default:
                editor.putInt(Filter.FILTERTYPE, 6); // TYPESALL
        }
        
        // Set filter found status based on filterFound parameter
        switch (filterFound) {
            case "all":
                editor.putInt(Filter.FILTERRADIO, 0); // Logged or not
                break;
            case "logged":
                editor.putInt(Filter.FILTERRADIO, 1); // Logged
                break;
            case "notlogged":
                editor.putInt(Filter.FILTERRADIO, 2); // Not Logged
                break;
            case "marked":
                editor.putInt(Filter.FILTERRADIO, 3); // Marked
                break;
            case "unsynced":
                editor.putInt(Filter.FILTERRADIO, 4); // Unsynced
                break;
            default:
                editor.putInt(Filter.FILTERRADIO, 0); // Logged or not
        }
        
        editor.apply();
    }

    @Override
    protected void onDestroy() {
        if (dbHelper != null) {
            dbHelper.close();
        }
        if (webView != null) {
            try { webView.loadUrl("about:blank"); } catch (Exception ignored) {}
            try { webView.stopLoading(); } catch (Exception ignored) {}
            webView.setWebChromeClient(null);
            webView.setWebViewClient(null);
            try { webView.destroy(); } catch (Exception ignored) {}
            webView = null;
        }
        super.onDestroy();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (webView != null) {
            webView.onResume();
            webView.resumeTimers();
        }
    }

    @Override
    protected void onPause() {
        if (webView != null) {
            webView.onPause();
            webView.pauseTimers();
        }
        super.onPause();
    }

    public class LeafletPreferencesInterface {
        @JavascriptInterface
        public void saveMapStyle(String mapStyle) {
            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(LeafletMapActivity.this);
            String normalized = normalizeMapStyle(mapStyle);
            prefs.edit().putString("leaflet_map_style", normalized).apply();
            Log.d(TAG, "Saved map style preference: " + normalized);
        }
        
        @JavascriptInterface
        public String getFilterFoundPreference() {
            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(LeafletMapActivity.this);
            int filterRadio = prefs.getInt(Filter.FILTERRADIO, 0); // Default to "Logged or not"
            String jsValue = convertFilterRadioToJsValue(filterRadio);
            Log.d(TAG, "Retrieved filter found preference: " + filterRadio + " -> " + jsValue);
            return jsValue;
        }
        
        /**
         * Convert Filter.FILTERRADIO integer value to JavaScript string for Leaflet map
         */
        private String convertFilterRadioToJsValue(int filterRadio) {
            switch (filterRadio) {
                case 0: return "all";       // Logged or not
                case 1: return "logged";    // Logged
                case 2: return "notlogged"; // Not Logged
                case 3: return "marked";    // Marked
                case 4: return "unsynced";  // Unsynced
                default: return "all";
            }
        }
        
        @JavascriptInterface
        public String getTrigpointTypePreference() {
            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(LeafletMapActivity.this);
            int filterType = prefs.getInt(Filter.FILTERTYPE, 6); // Default to "All Types"
            String jsType = convertFilterTypeToJsType(filterType);
            Log.d(TAG, "Retrieved trigpoint type preference: " + filterType + " -> " + jsType);
            return jsType;
        }
        
        /**
         * Convert Filter.FILTERTYPE integer value to JavaScript string for Leaflet map
         */
        private String convertFilterTypeToJsType(int filterType) {
            switch (filterType) {
                case 0: return "pillars";       // TYPESPILLAR
                case 1: return "pillarsfbm";    // TYPESPILLARFBM
                case 2: return "fbm";           // TYPESFBM
                case 3: return "passive";       // TYPESPASSIVE
                case 4: return "intersected";   // TYPESINTERSECTED
                case 5: return "nointersected"; // TYPESNOINTERSECTED
                case 6: return "all";           // TYPESALL
                default: return "all";
            }
        }
        
        @JavascriptInterface
        public void saveTrigpointTypePreference(String trigpointType) {
            // Convert JavaScript type back to Filter.FILTERTYPE integer
            int filterType = convertJsTypeToFilterType(trigpointType);
            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(LeafletMapActivity.this);
            prefs.edit().putInt(Filter.FILTERTYPE, filterType).apply();
            Log.d(TAG, "Saved trigpoint type preference: " + trigpointType + " -> " + filterType);
        }
        
        /**
         * Convert JavaScript string to Filter.FILTERTYPE integer value
         */
        private int convertJsTypeToFilterType(String jsType) {
            switch (jsType) {
                case "pillars": return 0;       // TYPESPILLAR
                case "pillarsfbm": return 1;    // TYPESPILLARFBM
                case "fbm": return 2;           // TYPESFBM
                case "passive": return 3;       // TYPESPASSIVE
                case "intersected": return 4;   // TYPESINTERSECTED
                case "nointersected": return 5; // TYPESNOINTERSECTED
                case "all": return 6;           // TYPESALL
                default: return 6;              // Default to "All Types"
            }
        }
        
        @JavascriptInterface
        public void saveFilterFoundPreference(String filterFound) {
            // Convert JavaScript value back to Filter.FILTERRADIO integer
            int filterRadio = convertJsValueToFilterRadio(filterFound);
            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(LeafletMapActivity.this);
            prefs.edit().putInt(Filter.FILTERRADIO, filterRadio).apply();
            Log.d(TAG, "Saved filter found preference: " + filterFound + " -> " + filterRadio);
        }
        
        /**
         * Convert JavaScript string to Filter.FILTERRADIO integer value
         */
        private int convertJsValueToFilterRadio(String jsValue) {
            switch (jsValue) {
                case "all": return 0;       // Logged or not
                case "logged": return 1;    // Logged
                case "notlogged": return 2; // Not Logged
                case "marked": return 3;    // Marked
                case "unsynced": return 4;  // Unsynced
                default: return 0;          // Default to "Logged or not"
            }
        }
        
        @JavascriptInterface
        public void getTrigpointData(double south, double west, double north, double east, String trigpointType, String filterFound, String colorScheme) {
            Log.d(TAG, String.format("getTrigpointData: bounds=(%.6f,%.6f,%.6f,%.6f) type=%s found=%s colour=%s", south, west, north, east, trigpointType, filterFound, colorScheme));
            
            // Run database query on background thread
            new Thread(() -> {
                try {
                    String trigpointsJson = queryTrigpoints(south, west, north, east, trigpointType, filterFound, colorScheme);
                    
                    // Return results to JavaScript on UI thread
                    runOnUiThread(() -> {
                        // Safely embed JSON as a JavaScript string literal to avoid breaking quotes/backslashes
                        String safeJson = org.json.JSONObject.quote(trigpointsJson);
                        webView.evaluateJavascript("displayTrigpointMarkers(" + safeJson + ");", null);
                    });
                } catch (Exception e) {
                    Log.e(TAG, "Error querying trigpoints", e);
                }
            }).start();
        }
        
        @JavascriptInterface
        public void openTrigDetails(long trigId) {
            Log.d(TAG, "Opening trig details for ID: " + trigId);
            Intent i = new Intent(LeafletMapActivity.this, uk.trigpointing.android.trigdetails.TrigDetailsActivity.class);
            i.putExtra(uk.trigpointing.android.DbHelper.TRIG_ID, trigId);
            startActivity(i);
        }
        
        @JavascriptInterface
        public void showCacheDialog(String message, String note) {
            runOnUiThread(() -> {
                android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(LeafletMapActivity.this);
                builder.setTitle("Cache Status");
                
                String fullMessage = message;
                if (note != null && !note.trim().isEmpty()) {
                    fullMessage += "\n\n" + note;
                }
                
                builder.setMessage(fullMessage);
                builder.setPositiveButton("OK", null);
                builder.show();
            });
        }
        
        @JavascriptInterface
        public void showDialog(String title, String message) {
            runOnUiThread(() -> {
                android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(LeafletMapActivity.this);
                builder.setTitle(title);
                builder.setMessage(message);
                builder.setPositiveButton("OK", null);
                builder.show();
            });
        }
        
        @JavascriptInterface
        public String getIconStyle() {
            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(LeafletMapActivity.this);
            return prefs.getString("map_icon_style", "medium");
        }
        

    }
    
    private void clearAllCaches() {
        // Clear our custom tile cache
        new Thread(() -> {
            int deletedCount = deleteRecursive(mTileCacheDir);
            Log.d(TAG, "Cleared " + deletedCount + " tile files");

            runOnUiThread(() -> {
                android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(LeafletMapActivity.this);
                builder.setTitle("Clear Cache");
                builder.setMessage("Cleared " + deletedCount + " cached map tiles.");
                builder.setPositiveButton("OK", null);
                builder.show();
            });
        }).start();

        // Also clear WebView's own caches
        webView.clearCache(true);
    }

    private int deleteRecursive(File fileOrDirectory) {
        int count = 0;
        if (fileOrDirectory.isDirectory()) {
            File[] children = fileOrDirectory.listFiles();
            if (children != null) {
                for (File child : children) {
                    count += deleteRecursive(child);
                }
            }
        }
        if (fileOrDirectory.delete()) {
            count++;
        }
        return count;
    }
    
}


