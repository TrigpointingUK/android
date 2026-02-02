package uk.trigpointing.android.ar;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.SurfaceTexture;
import android.graphics.drawable.Drawable;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.CaptureResult;
import android.hardware.camera2.CameraMetadata;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Handler;
import android.os.Looper;
import android.util.AttributeSet;
import android.util.Size;
import android.util.SizeF;
import android.util.Range;
import android.graphics.Rect;
import android.util.Log;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.Surface;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.preference.PreferenceManager;
import android.content.SharedPreferences;

import java.util.ArrayList;
import java.util.List;

import uk.trigpointing.android.DbHelper;
import uk.trigpointing.android.R;
import uk.trigpointing.android.common.BaseActivity;
import uk.trigpointing.android.filter.Filter;
import uk.trigpointing.android.types.Trig;
import uk.trigpointing.android.trigdetails.TrigDetailsActivity;

/**
 * Sensor-based AR Activity that uses pure compass and sensor data
 * to display trigpoint icons overlaid on camera view.
 * 
 * This approach prioritizes magnetic compass bearings over visual tracking
 * and works even when the camera is covered.
 */
public class SensorARActivity extends BaseActivity implements SensorEventListener, LocationListener {
    
    private static final String TAG = "SensorARActivity";
    private static final int CAMERA_PERMISSION_REQUEST = 1001;
    private static final int LOCATION_PERMISSION_REQUEST = 1002;
    private static final int COMBINED_PERMISSION_REQUEST = 1003;
    private static final double MAX_DISTANCE_METERS = 5000; // 5km max distance
    
    // Camera2 components
    private Camera2Preview cameraPreview;
    private String selectedCameraId;
    private boolean selectedIsLogical;
    private CameraDevice cameraDevice;
    private CameraCaptureSession captureSession;
    private CaptureRequest.Builder previewRequestBuilder;
    private Size previewSize;
    private HandlerThread cameraThread;
    private Handler cameraHandler;
    private float originalCameraHfovDeg = 60f; // original camera horizontal FOV from characteristics (never modified)
    private float originalCameraVfovDeg = 45f; // original camera vertical FOV from characteristics (never modified)
    private float cameraHfovDeg = 60f; // effective camera horizontal FOV (after zoom/crop adjustments)
    private float cameraVfovDeg = 45f; // effective camera vertical FOV (after zoom/crop adjustments)
    private float baseFovXDeg = 60f; // mapped to on-screen X
    private float baseFovYDeg = 45f; // mapped to on-screen Y
    private int lastPreviewRotationDeg = 0;
    private CameraCharacteristics activeCharacteristics;
    
    // Sensor components
    private SensorManager sensorManager;
    private Sensor rotationSensor;
    private Sensor magneticSensor;
    private Sensor accelerometerSensor;
    
    // Orientation data
    private float[] rotationMatrix = new float[9];
    private float[] orientationAngles = new float[3];
    private float currentAzimuth = 0.0f; // Compass bearing in degrees
    private float currentPitch = 0.0f;   // Tilt up/down
    private float currentRoll = 0.0f;    // Tilt left/right
    
    // Location and database
    private LocationManager locationManager;
    private Location currentLocation;
    private DbHelper dbHelper;
    private List<AROverlayView.TrigpointData> nearbyTrigpoints = new ArrayList<>();
    
    // UI components
    private AROverlayView overlayView;
    private Button narrowerBtn;
    private Button widerBtn;
    
    // Activity lifecycle flag
    private volatile boolean isDestroyed = false;
    
    // Handler for auto-repeat on FOV calibration buttons
    private final Handler arFovRepeatHandler = new Handler(Looper.getMainLooper());

    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_sensor_ar);
        
        Log.i(TAG, "onCreate: Starting Sensor AR Activity");
        
        // Set up action bar with back arrow
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle("AR View");
        }
        
        // Initialize UI components
        cameraPreview = findViewById(R.id.camera_preview);
        overlayView = findViewById(R.id.ar_overlay);
        if (overlayView != null) {
            overlayView.setOnTrigpointClickListener(trigId -> {
                try {
                    Intent i = new Intent(SensorARActivity.this, TrigDetailsActivity.class);
                    i.putExtra(DbHelper.TRIG_ID, trigId);
                    startActivity(i);
                } catch (Exception e) {
                    Toast.makeText(SensorARActivity.this, "Unable to open trig details", Toast.LENGTH_LONG).show();
                }
            });
            
            // Listen for compass orientation changes to reposition buttons
            overlayView.setOnCompassOrientationChangeListener(this::updateButtonPositions);
        }
        
        // Calibration buttons with auto-repeat on press-and-hold
        narrowerBtn = findViewById(R.id.ar_narrower);
        widerBtn = findViewById(R.id.ar_wider);
        if (narrowerBtn != null) {
            setupAutoRepeatButton(narrowerBtn, -0.02f);
        }
        if (widerBtn != null) {
            setupAutoRepeatButton(widerBtn, +0.02f);
        }
        
        // Initialize sensors
        sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        if (sensorManager != null) {
            rotationSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR);
            magneticSensor = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);
            accelerometerSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        }
        
        // Initialize database helper
        try {
            dbHelper = new DbHelper(this);
            dbHelper.open();
            Log.i(TAG, "Database helper initialized and opened successfully");
        } catch (Exception e) {
            Log.e(TAG, "Failed to initialize database helper", e);
            dbHelper = null;
        }
        
        // Check permissions and start
        if (hasPermissions()) {
            boolean hasCamera = getPackageManager().hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY);
            if (hasCamera) {
                setupCameraPreviewCallbacks();
            } else {
                // Device has no camera - show message and continue with location-only AR
                Toast.makeText(this, "Device has no camera - AR will work with location and compass only", Toast.LENGTH_LONG).show();
            }
            startLocationServices();
        } else {
            requestPermissions();
        }
    }
    
    @Override
    protected void onResume() {
        super.onResume();
        
        // Register sensor listeners
        if (sensorManager != null) {
            if (rotationSensor != null) {
                sensorManager.registerListener(this, rotationSensor, SensorManager.SENSOR_DELAY_GAME);
            }
            if (magneticSensor != null) {
                sensorManager.registerListener(this, magneticSensor, SensorManager.SENSOR_DELAY_GAME);
            }
            if (accelerometerSensor != null) {
                sensorManager.registerListener(this, accelerometerSensor, SensorManager.SENSOR_DELAY_GAME);
            }
        }
        
        // Resume camera (only if device has camera)
        boolean hasCamera = getPackageManager().hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY);
        if (hasPermissions() && hasCamera) {
            startBackgroundThread();
            if (cameraPreview != null) {
                setupCameraPreviewCallbacks();
                if (cameraPreview.isAvailable()) {
                    onPreviewSurfaceAvailable(cameraPreview.getSurfaceTexture(), cameraPreview.getWidth(), cameraPreview.getHeight());
                }
            }
        }
    }
    
    @Override
    protected void onPause() {
        super.onPause();
        
        // Stop any FOV auto-repeat callbacks
        arFovRepeatHandler.removeCallbacksAndMessages(null);

        // Unregister sensor listeners
        if (sensorManager != null) {
            sensorManager.unregisterListener(this);
        }
        
        // Close Camera2
        closeCamera();
        stopBackgroundThread();
    }
    
    @Override
    protected void onDestroy() {
        super.onDestroy();
        
        // Set flag to prevent further database access
        isDestroyed = true;
        
        if (dbHelper != null) {
            try {
                dbHelper.close();
                Log.i(TAG, "Database helper closed successfully");
            } catch (Exception e) {
                Log.e(TAG, "Error closing database helper", e);
            }
        }
    }
    
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            // Handle back arrow click
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
    
    private boolean hasPermissions() {
        // Check if device has camera hardware
        boolean hasCamera = getPackageManager().hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY);
        
        // Location permission is always required
        boolean hasLocation = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
        
        // Camera permission only required if device has camera
        boolean hasCameraPermission = !hasCamera || 
            ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED;
        
        return hasLocation && hasCameraPermission;
    }
    
    private void requestPermissions() {
        // Check if device has camera hardware
        boolean hasCamera = getPackageManager().hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY);
        
        // Build permission list based on device capabilities
        List<String> permissionsToRequest = new ArrayList<>();
        permissionsToRequest.add(Manifest.permission.ACCESS_FINE_LOCATION);
        
        if (hasCamera) {
            permissionsToRequest.add(Manifest.permission.CAMERA);
        }
        
        // Use combined request code since we're requesting multiple permissions
        ActivityCompat.requestPermissions(this,
            permissionsToRequest.toArray(new String[0]),
            COMBINED_PERMISSION_REQUEST);
    }
    
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        
        if (requestCode == COMBINED_PERMISSION_REQUEST) {
            boolean hasCamera = getPackageManager().hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY);
            boolean locationGranted = false;
            boolean cameraGranted = !hasCamera; // If no camera, consider "granted"
            
            // Check which permissions were granted
            for (int i = 0; i < permissions.length; i++) {
                if (permissions[i].equals(Manifest.permission.ACCESS_FINE_LOCATION)) {
                    locationGranted = grantResults[i] == PackageManager.PERMISSION_GRANTED;
                } else if (permissions[i].equals(Manifest.permission.CAMERA)) {
                    cameraGranted = grantResults[i] == PackageManager.PERMISSION_GRANTED;
                }
            }
            
            if (locationGranted && cameraGranted) {
                if (hasCamera) {
                    setupCameraPreviewCallbacks();
                } else {
                    // Device has no camera - show message and continue with location-only AR
                    Toast.makeText(this, "Device has no camera - AR will work with location and compass only", Toast.LENGTH_LONG).show();
                }
                startLocationServices();
            } else {
                String message = hasCamera ? 
                    "Location and camera permissions are required for AR" : 
                    "Location permission is required for AR";
                Toast.makeText(this, message, Toast.LENGTH_LONG).show();
                finish();
            }
        }
    }
    
    private void setupCameraPreviewCallbacks() {
        if (cameraPreview == null) return;
        cameraPreview.setSurfaceEventsListener(new Camera2Preview.SurfaceEventsListener() {
            @Override
            public void onSurfaceAvailable(SurfaceTexture surface, int width, int height) {
                onPreviewSurfaceAvailable(surface, width, height);
            }
            @Override
            public void onSurfaceSizeChanged(SurfaceTexture surface, int width, int height) {
                applyPreviewTransform();
            }
            @Override
            public void onSurfaceDestroyed() {
                closeCamera();
            }
        });
    }

    private void onPreviewSurfaceAvailable(SurfaceTexture surface, int width, int height) {
        try {
            if (selectedCameraId == null) {
                selectWidestBackCamera();
            }
            openCamera(surface, width, height);
        } catch (Exception e) {
            Log.e(TAG, "Error starting camera preview", e);
            Toast.makeText(this, "Failed to start camera", Toast.LENGTH_SHORT).show();
        }
    }

    private void startBackgroundThread() {
        if (cameraThread != null) return;
        cameraThread = new HandlerThread("CameraBackground");
        cameraThread.start();
        cameraHandler = new Handler(cameraThread.getLooper());
    }

    private void stopBackgroundThread() {
        if (cameraThread != null) {
            cameraThread.quitSafely();
            try {
                cameraThread.join();
            } catch (InterruptedException ignored) {}
            cameraThread = null;
            cameraHandler = null;
        }
    }

    private void selectWidestBackCamera() throws CameraAccessException {
        CameraManager cm = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
        if (cm == null) return;
        // First preference: pick the widest NON-LOGICAL physical back camera id if available
        String bestPhysicalId = null;
        float bestPhysicalHFov = 0f, bestPhysicalVFov = 0f;
        for (String id : cm.getCameraIdList()) {
            CameraCharacteristics cc = cm.getCameraCharacteristics(id);
            Integer facing = cc.get(CameraCharacteristics.LENS_FACING);
            if (facing == null || facing != CameraCharacteristics.LENS_FACING_BACK) continue;
            int[] caps = cc.get(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES);
            boolean isLogical = false;
            if (caps != null) {
                for (int c : caps) if (c == CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_LOGICAL_MULTI_CAMERA) { isLogical = true; break; }
            }
            if (isLogical) continue; // skip logical for first pass
            SizeF sensorSize = cc.get(CameraCharacteristics.SENSOR_INFO_PHYSICAL_SIZE);
            float[] focalLengths = cc.get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS);
            if (sensorSize == null || focalLengths == null || focalLengths.length == 0) continue;
            float minFocal = focalLengths[0];
            for (float f : focalLengths) if (f < minFocal) minFocal = f;
            float hFov = (float) Math.toDegrees(2.0 * Math.atan((sensorSize.getWidth() / 2.0) / minFocal));
            float vFov = (float) Math.toDegrees(2.0 * Math.atan((sensorSize.getHeight() / 2.0) / minFocal));
            if (hFov > bestPhysicalHFov) { bestPhysicalHFov = hFov; bestPhysicalVFov = vFov; bestPhysicalId = id; }
        }
        if (bestPhysicalId != null) {
            selectedCameraId = bestPhysicalId;
            selectedIsLogical = false;
            originalCameraHfovDeg = bestPhysicalHFov;
            originalCameraVfovDeg = bestPhysicalVFov;
            cameraHfovDeg = bestPhysicalHFov;
            cameraVfovDeg = bestPhysicalVFov;
            return;
        }

        // Fallback: choose logical with widest computed FOV
        String bestId = null;
        float bestHFov = 0f, bestVFov = 0f;
        for (String id : cm.getCameraIdList()) {
            CameraCharacteristics cc = cm.getCameraCharacteristics(id);
            Integer facing = cc.get(CameraCharacteristics.LENS_FACING);
            if (facing == null || facing != CameraCharacteristics.LENS_FACING_BACK) continue;
            SizeF sensorSize = cc.get(CameraCharacteristics.SENSOR_INFO_PHYSICAL_SIZE);
            float[] focalLengths = cc.get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS);
            if (sensorSize == null || focalLengths == null || focalLengths.length == 0) continue;
            float minFocal = focalLengths[0];
            for (float f : focalLengths) if (f < minFocal) minFocal = f;
            float hFov = (float) Math.toDegrees(2.0 * Math.atan((sensorSize.getWidth() / 2.0) / minFocal));
            float vFov = (float) Math.toDegrees(2.0 * Math.atan((sensorSize.getHeight() / 2.0) / minFocal));
            if (hFov > bestHFov) { bestHFov = hFov; bestVFov = vFov; bestId = id; }
        }
        selectedCameraId = bestId;
        selectedIsLogical = true;
        if (bestId != null) { 
            originalCameraHfovDeg = bestHFov; 
            originalCameraVfovDeg = bestVFov;
            cameraHfovDeg = bestHFov; 
            cameraVfovDeg = bestVFov; 
        }
    }

    private void openCamera(SurfaceTexture surfaceTexture, int viewWidth, int viewHeight) throws CameraAccessException {
        CameraManager cm = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
        if (cm == null || selectedCameraId == null) return;
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) return;

        CameraCharacteristics cc = cm.getCameraCharacteristics(selectedCameraId);
        StreamConfigurationMap map = cc.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
        if (map != null) {
            Size[] choices = map.getOutputSizes(SurfaceTexture.class);
            previewSize = chooseOptimalSize(choices, viewWidth, viewHeight);
        }
        if (previewSize == null && map != null) {
            Size[] any = map.getOutputSizes(SurfaceTexture.class);
            if (any != null && any.length > 0) previewSize = any[0];
        }
        if (previewSize == null) previewSize = new Size(viewWidth, viewHeight);

        surfaceTexture.setDefaultBufferSize(previewSize.getWidth(), previewSize.getHeight());
        cm.openCamera(selectedCameraId, new CameraDevice.StateCallback() {
            @Override
            public void onOpened(@NonNull CameraDevice camera) {
                cameraDevice = camera;
                try {
                    activeCharacteristics = cc;
                    createCameraPreviewSession(new Surface(surfaceTexture), cc);
                } catch (Exception e) {
                    Log.e(TAG, "Failed to create preview session", e);
                }
            }
            @Override
            public void onDisconnected(@NonNull CameraDevice camera) {
                camera.close();
                cameraDevice = null;
            }
            @Override
            public void onError(@NonNull CameraDevice camera, int error) {
                camera.close();
                cameraDevice = null;
                Toast.makeText(SensorARActivity.this, "Camera error", Toast.LENGTH_SHORT).show();
            }
        }, cameraHandler);
    }

    private void createCameraPreviewSession(Surface surface, CameraCharacteristics cc) throws CameraAccessException {
        if (cameraDevice == null) return;
        previewRequestBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
        previewRequestBuilder.addTarget(surface);

        // Base auto controls
        previewRequestBuilder.set(CaptureRequest.CONTROL_MODE, CaptureRequest.CONTROL_MODE_AUTO);
        previewRequestBuilder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON);

        // Infinity focus preference
        setInfinityFocus(previewRequestBuilder, cc);

        // Prefer the widest native FoV on logical multi-camera devices
        applyWidestZoomIfAvailable(previewRequestBuilder, cc);

        // Enable lens distortion correction if supported to avoid fisheye warping on ultra-wide
        enableDistortionCorrection(previewRequestBuilder, cc);

        cameraDevice.createCaptureSession(java.util.Arrays.asList(surface), new CameraCaptureSession.StateCallback() {
            @Override
            public void onConfigured(@NonNull CameraCaptureSession session) {
                captureSession = session;
                try {
                    CaptureRequest previewRequest = previewRequestBuilder.build();
                    captureSession.setRepeatingRequest(previewRequest, new CameraCaptureSession.CaptureCallback() {
                        @Override
                        public void onCaptureCompleted(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request, @NonNull android.hardware.camera2.TotalCaptureResult result) {
                            updateEffectiveFovFromResult(result);
                        }
                    }, cameraHandler);
                } catch (CameraAccessException e) {
                    Log.e(TAG, "Failed to start repeating request", e);
                }
                runOnUiThread(() -> {
                    // Map camera FOV to on-screen axes depending on display orientation and preview buffer orientation
                computeBaseFovsForCurrentOrientation();
                applyPreviewTransform();
                    updateOverlayFovFromBase();
                });
            }
            @Override
            public void onConfigureFailed(@NonNull CameraCaptureSession session) {
                Toast.makeText(SensorARActivity.this, "Preview config failed", Toast.LENGTH_SHORT).show();
            }
        }, cameraHandler);
    }

    private void applyWidestZoomIfAvailable(CaptureRequest.Builder builder, CameraCharacteristics cc) {
        try {
            if (Build.VERSION.SDK_INT >= 30) {
                Range<Float> zoomRange = cc.get(CameraCharacteristics.CONTROL_ZOOM_RATIO_RANGE);
                if (zoomRange != null) {
                    Float lower = zoomRange.getLower();
                    if (lower != null && lower < 1.0f) {
                        builder.set(CaptureRequest.CONTROL_ZOOM_RATIO, lower);
                        // With logical multi-camera, this should select the ultra-wide module
                    }
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "Unable to apply widest zoom ratio", e);
        }
    }

    private void enableDistortionCorrection(CaptureRequest.Builder builder, CameraCharacteristics cc) {
        try {
            int[] modes = cc.get(CameraCharacteristics.DISTORTION_CORRECTION_AVAILABLE_MODES);
            if (modes != null) {
                boolean hasOn = false;
                for (int m : modes) if (m == CameraMetadata.DISTORTION_CORRECTION_MODE_HIGH_QUALITY || m == CameraMetadata.DISTORTION_CORRECTION_MODE_FAST) hasOn = true;
                if (hasOn) {
                    builder.set(CaptureRequest.DISTORTION_CORRECTION_MODE, CameraMetadata.DISTORTION_CORRECTION_MODE_HIGH_QUALITY);
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "Unable to enable distortion correction", e);
        }
    }

    private void updateEffectiveFovFromResult(android.hardware.camera2.TotalCaptureResult result) {
        try {
            // Use active crop region and zoom ratio to refine our effective FOV, ensuring the overlay centers correctly
            Rect activeArray = activeCharacteristics != null ? activeCharacteristics.get(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE) : null;
            Float zoomRatio = null;
            if (Build.VERSION.SDK_INT >= 30) {
                zoomRatio = result.get(CaptureResult.CONTROL_ZOOM_RATIO);
            }
            Rect crop = result.get(CaptureResult.SCALER_CROP_REGION);
            if (activeArray == null) return;

            int cropW, cropH;
            if (crop != null) {
                cropW = Math.max(1, crop.width());
                cropH = Math.max(1, crop.height());
            } else {
                cropW = activeArray.width();
                cropH = activeArray.height();
            }

            // If zoom ratio is < 1, it indicates wider than 1x on logical multi-camera
            // Effective FOV scales inversely with zoom ratio and proportionally with crop to sensor size
            float wScale = (float) activeArray.width() / (float) cropW;
            float hScale = (float) activeArray.height() / (float) cropH;
            float ratio = (zoomRatio != null && zoomRatio > 0f) ? (1f / zoomRatio) : 1f;

            // Use ORIGINAL camera FOV values to avoid exponential compounding
            float effectiveH = originalCameraHfovDeg * wScale * ratio;
            float effectiveV = originalCameraVfovDeg * hScale * ratio;

            cameraHfovDeg = effectiveH;
            cameraVfovDeg = effectiveV;

            runOnUiThread(() -> {
                computeBaseFovsForCurrentOrientation();
                updateOverlayFovFromBase();
            });
        } catch (Exception e) {
            // Be tolerant; overlay still uses base mapping
        }
    }

    private void setInfinityFocus(CaptureRequest.Builder builder, CameraCharacteristics cc) {
        try {
            int[] afModes = cc.get(CameraCharacteristics.CONTROL_AF_AVAILABLE_MODES);
            Float minFocus = cc.get(CameraCharacteristics.LENS_INFO_MINIMUM_FOCUS_DISTANCE);
            boolean supportsManual = false;
            if (afModes != null) {
                for (int m : afModes) if (m == CaptureRequest.CONTROL_AF_MODE_OFF) supportsManual = true;
            }
            if (supportsManual && minFocus != null) {
                builder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_OFF);
                builder.set(CaptureRequest.LENS_FOCUS_DISTANCE, 0.0f); // infinity in diopters
            } else {
                // Fallback to continuous picture or EDOF
                if (afModes != null) {
                    boolean hasContinuous = false, hasEdof = false;
                    for (int m : afModes) {
                        if (m == CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE) hasContinuous = true;
                        if (m == CaptureRequest.CONTROL_AF_MODE_EDOF) hasEdof = true;
                    }
                    if (hasContinuous) builder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
                    else if (hasEdof) builder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_EDOF);
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "Unable to set infinity focus", e);
        }
    }

    private void closeCamera() {
        try {
            if (captureSession != null) {
                captureSession.close();
                captureSession = null;
            }
            if (cameraDevice != null) {
                cameraDevice.close();
                cameraDevice = null;
            }
        } catch (Exception ignored) {}
    }

    private Size chooseOptimalSize(Size[] choices, int viewWidth, int viewHeight) {
        if (choices == null || choices.length == 0) return null;
        double targetRatio = (double) Math.max(viewWidth, viewHeight) / (double) Math.min(viewWidth, viewHeight);
        Size best = choices[0];
        double bestScore = Double.MAX_VALUE;
        for (Size s : choices) {
            int w = s.getWidth();
            int h = s.getHeight();
            double ratio = (double) Math.max(w, h) / (double) Math.min(w, h);
            double ratioScore = Math.abs(ratio - targetRatio);
            // Prefer sizes not exceeding 1920x1080 to reduce load, but accept larger if closer ratio
            double sizePenalty = (w > 1920 || h > 1080) ? 0.2 : 0.0;
            double score = ratioScore + sizePenalty;
            if (score < bestScore) { bestScore = score; best = s; }
        }
        return best;
    }

    private void applyPreviewTransform() {
        if (cameraPreview == null || previewSize == null) return;
        int vw = cameraPreview.getWidth();
        int vh = cameraPreview.getHeight();
        int bw = previewSize.getWidth();
        int bh = previewSize.getHeight();
        // We render sensor-aligned overlay independently; center-crop the preview buffer
        int rotationDegrees = lastPreviewRotationDeg;
        cameraPreview.applyTransform(vw, vh, bw, bh, rotationDegrees);
    }

    private void updateOverlayFovFromBase() {
        if (overlayView == null) return;
        overlayView.setFieldOfViewDegrees(getEffectiveFovForScreenWidth(), getEffectiveFovForScreenHeight());
        // Ensure compass snapping scale matches trig overlay after FOV update
        loadNearbyTrigpoints();
    }
    
    private void startLocationServices() {
        locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        
        if (locationManager == null) {
            Log.e(TAG, "LocationManager is not available");
            return;
        }
        
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            Log.e(TAG, "Location permission not granted");
            return;
        }
        
        try {
            // Check which providers are available and enabled
            boolean gpsAvailable = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER);
            boolean networkAvailable = locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER);
            
            Log.i(TAG, "GPS provider available: " + gpsAvailable + ", Network provider available: " + networkAvailable);
            
            // Request location updates from available providers
            if (gpsAvailable) {
                try {
                    locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000, 1, this);
                    Log.i(TAG, "GPS location updates requested");
                } catch (IllegalArgumentException e) {
                    Log.w(TAG, "GPS provider not available: " + e.getMessage());
                }
            }
            
            if (networkAvailable) {
                try {
                    locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 1000, 1, this);
                    Log.i(TAG, "Network location updates requested");
                } catch (IllegalArgumentException e) {
                    Log.w(TAG, "Network provider not available: " + e.getMessage());
                }
            }
            
            // Try to get last known location from available providers
            Location lastKnown = null;
            if (gpsAvailable) {
                try {
                    lastKnown = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
                } catch (IllegalArgumentException e) {
                    Log.w(TAG, "GPS provider not available for last known location: " + e.getMessage());
                }
            }
            
            if (lastKnown == null && networkAvailable) {
                try {
                    lastKnown = locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
                } catch (IllegalArgumentException e) {
                    Log.w(TAG, "Network provider not available for last known location: " + e.getMessage());
                }
            }
            
            if (lastKnown != null) {
                onLocationChanged(lastKnown);
            } else {
                Log.i(TAG, "No last known location available");
            }
            
            Log.i(TAG, "Location services started successfully");
            
        } catch (SecurityException e) {
            Log.e(TAG, "Location permission denied", e);
        } catch (Exception e) {
            Log.e(TAG, "Unexpected error starting location services", e);
        }
    }
    
    @Override
    public void onLocationChanged(Location location) {
        if (isDestroyed) {
            return;
        }
        
        currentLocation = location;
        Log.i(TAG, "New location: " + location.getLatitude() + ", " + location.getLongitude());
        loadNearbyTrigpoints();
    }
    
    @Override
    public void onProviderEnabled(String provider) {}
    
    @Override
    public void onProviderDisabled(String provider) {}
    
    @Override
    public void onStatusChanged(String provider, int status, Bundle extras) {}
    
    private void loadNearbyTrigpoints() {
        if (currentLocation == null || dbHelper == null || isDestroyed) {
            return;
        }
        
        new Thread(() -> {
            try {
                // Check again if activity was destroyed while thread was starting
                if (isDestroyed) {
                    Log.i(TAG, "loadNearbyTrigpoints: Activity destroyed, aborting database query");
                    return;
                }
                
                double lat = currentLocation.getLatitude();
                double lon = currentLocation.getLongitude();
                
                // Query database for nearby trigpoints with user's filter preferences
                Log.i(TAG, "loadNearbyTrigpoints: Applying user's trigpoint type filters");
                Cursor cursor = dbHelper.fetchTrigList(currentLocation);
                List<AROverlayView.TrigpointData> trigpoints = new ArrayList<>();
                
                if (cursor != null && cursor.moveToFirst()) {
                    // First pass: collect more candidates than needed to ensure we have
                    // enough bearings spanning what landscape can display. We'll keep
                    // at most 10 nearest after bearing filtering.
                    List<AROverlayView.TrigpointData> candidates = new ArrayList<>();
                    do {
                        long id = cursor.getLong(0);
                        String name = cursor.getString(1);
                        double trigLat = cursor.getDouble(2);
                        double trigLon = cursor.getDouble(3);
                        String type = cursor.getString(4);
                        String condition = cursor.getString(5);
                        
                        // Calculate distance
                        Location trigLocation = new Location("trigpoint");
                        trigLocation.setLatitude(trigLat);
                        trigLocation.setLongitude(trigLon);
                        float distance = currentLocation.distanceTo(trigLocation);
                        
                        // Only include trigpoints within max distance
                        if (distance <= MAX_DISTANCE_METERS) {
                            AROverlayView.TrigpointData trigData = new AROverlayView.TrigpointData(id, name, trigLat, trigLon, type, condition);
                            candidates.add(trigData);
                        }
                        
                    } while (cursor.moveToNext());
                    cursor.close();

                    // Second pass: pick up to 10 nearest among those whose bearings
                    // lie within the maximum FOV that could be displayed at any screen rotation.
                    // Use diagonal FOV for optimal coverage across all orientations.
                    final float maxHorizontalFovDeg = overlayView != null ? overlayView.getDiagonalFieldOfViewDegrees() : 90f;
                    // Sort by distance ascending
                    candidates.sort((a, b) -> {
                        Location la = new Location("a"); la.setLatitude(a.getLat()); la.setLongitude(a.getLon());
                        Location lb = new Location("b"); lb.setLatitude(b.getLat()); lb.setLongitude(b.getLon());
                        return Float.compare(currentLocation.distanceTo(la), currentLocation.distanceTo(lb));
                    });
                    for (AROverlayView.TrigpointData t : candidates) {
                        if (trigpoints.size() >= 10) break;
                        float bearing = currentLocation.bearingTo(new Location("tmp") {{ setLatitude(t.getLat()); setLongitude(t.getLon()); }});
                        if (bearing < 0) bearing += 360f;
                        float rel = bearing - currentAzimuth;
                        while (rel > 180f) rel -= 360f;
                        while (rel < -180f) rel += 360f;
                        if (Math.abs(rel) <= maxHorizontalFovDeg / 2f) {
                            trigpoints.add(t);
                        }
                    }
                }
                
                // Update UI on main thread (if activity still exists)
                runOnUiThread(() -> {
                    if (!isDestroyed && overlayView != null) {
                        nearbyTrigpoints = trigpoints;
                        overlayView.setCurrentLocation(currentLocation);
                        overlayView.updateTrigpoints(trigpoints);
                        Log.i(TAG, "Loaded " + trigpoints.size() + " nearby trigpoints");
                    } else {
                        Log.i(TAG, "loadNearbyTrigpoints: Activity destroyed, skipping UI update");
                    }
                });
                
            } catch (Exception e) {
                Log.e(TAG, "Error loading nearby trigpoints", e);
            }
        }).start();
    }

    // No dynamic limit – keep stable list of 10 nearest for a clean UI
    
    @Override
    public void onSensorChanged(SensorEvent event) {
        if (isDestroyed) {
            return;
        }
        
        if (event.sensor.getType() == Sensor.TYPE_ROTATION_VECTOR) {
            // Build rotation matrix from the rotation vector
            SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values);

            // Derive camera heading: project the camera-forward vector (device -Z) into world X/Y (east/north)
            // rotationMatrix is row-major: [0..2; 3..5; 6..8]
            float fx = -rotationMatrix[2];  // world X component of camera-forward
            float fy = -rotationMatrix[5];  // world Y component of camera-forward
            float fz = -rotationMatrix[8];  // world Z component of camera-forward (not used for heading)

            // Heading relative to magnetic north, increasing eastward
            float headingRad = (float) Math.atan2(fx, fy);
            float headingDeg = (float) Math.toDegrees(headingRad);
            if (headingDeg < 0) headingDeg += 360f;
            currentAzimuth = headingDeg;

            // Compute camera elevation above horizon using forward vector
            float horizontalMag = (float) Math.sqrt(fx * fx + fy * fy);
            float elevationRad = (float) Math.atan2(fz, horizontalMag); // -90..+90, + up
            float elevationDeg = (float) Math.toDegrees(elevationRad);
            currentPitch = elevationDeg; // reuse field to carry elevation to overlay
            
            // Compute roll so we can rotate overlay to keep horizon level regardless of device rotation
            // Project world-up (0,0,1) into the camera/image plane and measure its angle vs device screen-up
            float rx = rotationMatrix[0], ry = rotationMatrix[3], rz = rotationMatrix[6]; // device +X in world
            float ux = rotationMatrix[1], uy = rotationMatrix[4], uz = rotationMatrix[7]; // device +Y in world (screen up)
            // forward vector f already computed above: (fx, fy, fz)
            float dotUf = 0f * fx + 0f * fy + 1f * fz; // = fz
            float projUx = 0f - dotUf * fx;
            float projUy = 0f - dotUf * fy;
            float projUz = 1f - dotUf * fz;
            // Express projected world-up in device screen basis
            float upInScreenX = projUx * rx + projUy * ry + projUz * rz; // component along device X (right)
            float upInScreenY = projUx * ux + projUy * uy + projUz * uz; // component along device Y (up)
            float rollRad = (float) Math.atan2(upInScreenX, upInScreenY); // angle to rotate so projected up aligns with screen up
            currentRoll = (float) Math.toDegrees(rollRad);

            // Update overlay with new orientation
            if (overlayView != null) {
                overlayView.updateOrientation(currentAzimuth, currentPitch, currentRoll);
            }
        }
    }
    
    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
        // Handle sensor accuracy changes if needed
    }

    private void adjustArFovScale(float delta) {
        try {
            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
            float scale = prefs.getFloat("ar_fov_scale", 1.0f);
            scale += delta;
            // Clamp defensively (allow down to 10% for narrow calibration)
            if (scale < 0.1f) scale = 0.1f;
            if (scale > 1.5f) scale = 1.5f;
            prefs.edit().putFloat("ar_fov_scale", scale).apply();
            if (overlayView != null) {
                overlayView.setFieldOfViewDegrees(getEffectiveFovForScreenWidth(), getEffectiveFovForScreenHeight());
                // Reload trigpoints with new FOV to ensure candidate selection uses updated values
                loadNearbyTrigpoints();
            }
        } catch (Exception e) {
            Log.w(TAG, "Failed to adjust AR FOV scale", e);
        }
    }

    private void setupAutoRepeatButton(Button button, float delta) {
        final Runnable[] repeatTaskHolder = new Runnable[1];
        button.setOnTouchListener((v, event) -> {
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    // Immediate adjustment on press
                    adjustArFovScale(delta);
                    // Start repeating after a short delay
                    repeatTaskHolder[0] = new Runnable() {
                        @Override public void run() {
                            adjustArFovScale(delta);
                            arFovRepeatHandler.postDelayed(this, 60);
                        }
                    };
                    arFovRepeatHandler.postDelayed(repeatTaskHolder[0], 300);
                    v.setPressed(true);
                    break;
                case MotionEvent.ACTION_UP:
                    v.setPressed(false);
                    if (repeatTaskHolder[0] != null) {
                        arFovRepeatHandler.removeCallbacks(repeatTaskHolder[0]);
                        repeatTaskHolder[0] = null;
                    }
                    // Call performClick for accessibility on completed touch
                    v.performClick();
                    break;
                case MotionEvent.ACTION_CANCEL:
                    v.setPressed(false);
                    if (repeatTaskHolder[0] != null) {
                        arFovRepeatHandler.removeCallbacks(repeatTaskHolder[0]);
                        repeatTaskHolder[0] = null;
                    }
                    break;
            }
            // Always return true to consume all touch events in the gesture
            return true;
        });
    }

    // Compute FOV to use for the horizontal spread of the overlay in current orientation.
    // In portrait we rotate preview by 90°, so the camera's vertical FOV maps to screen width.
    private float getEffectiveFovForScreenWidth() {
        float base = baseFovXDeg; // horizontal FOV across screen width
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        float scale = prefs.getFloat("ar_fov_scale", 1.0f);
        if (scale < 0.1f) scale = 0.1f;
        if (scale > 1.5f) scale = 1.5f;
        return base * scale;
    }

    // Compute FOV to use for the vertical spread across screen height in current orientation.
    // In portrait we rotate preview by 90°, so the camera's horizontal FOV maps to screen height.
    private float getEffectiveFovForScreenHeight() {
        float base = baseFovYDeg; // vertical FOV across screen height
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        float scale = prefs.getFloat("ar_fov_scale", 1.0f);
        if (scale < 0.1f) scale = 0.1f;
        if (scale > 1.5f) scale = 1.5f;
        return base * scale;
    }

    private void computeBaseFovsForCurrentOrientation() {
        try {
            if (activeCharacteristics == null) {
                baseFovXDeg = cameraHfovDeg;
                baseFovYDeg = cameraVfovDeg;
                lastPreviewRotationDeg = 0;
                return;
            }
            // Determine the sensor orientation and current display rotation to map FOV to screen axes
            Integer sensorOrientation = activeCharacteristics.get(CameraCharacteristics.SENSOR_ORIENTATION);
            int displayRotation = getWindowManager().getDefaultDisplay().getRotation();
            int rotationDeg;
            switch (displayRotation) {
                case android.view.Surface.ROTATION_90: rotationDeg = 90; break;
                case android.view.Surface.ROTATION_180: rotationDeg = 180; break;
                case android.view.Surface.ROTATION_270: rotationDeg = 270; break;
                case android.view.Surface.ROTATION_0:
                default: rotationDeg = 0; break;
            }
            int sensorDeg = (sensorOrientation != null) ? sensorOrientation : 90;
            // Effective buffer rotation relative to display
            int total = (sensorDeg - rotationDeg + 360) % 360;
            // Rotate the preview by the inverse plus 90° to align typical back-camera sensors in portrait
            lastPreviewRotationDeg = (360 - total + 90) % 360;
            boolean swapped = (total == 90 || total == 270);
            if (swapped) {
                // In portrait with 90° sensor, camera vertical FOV maps to screen width
                baseFovXDeg = cameraVfovDeg;
                baseFovYDeg = cameraHfovDeg;
            } else {
                baseFovXDeg = cameraHfovDeg;
                baseFovYDeg = cameraVfovDeg;
            }
        } catch (Exception e) {
            baseFovXDeg = cameraHfovDeg;
            baseFovYDeg = cameraVfovDeg;
            lastPreviewRotationDeg = 0;
        }
    }
    
    /**
     * Update the position of FOV calibration buttons based on compass orientation.
     * Buttons should always be on the opposite edge from the compass bar.
     * 
     * @param snappedAngle The compass snap angle: 0 (top), 90 (left edge), 180 (bottom), 270 (right edge)
     */
    private void updateButtonPositions(int snappedAngle) {
        if (narrowerBtn == null || widerBtn == null) return;
        
        int margin = (int) (16 * getResources().getDisplayMetrics().density);
        
        FrameLayout.LayoutParams narrowerParams = (FrameLayout.LayoutParams) narrowerBtn.getLayoutParams();
        FrameLayout.LayoutParams widerParams = (FrameLayout.LayoutParams) widerBtn.getLayoutParams();
        
        // Reset margins
        narrowerParams.setMargins(0, 0, 0, 0);
        widerParams.setMargins(0, 0, 0, 0);
        
        switch (snappedAngle) {
            case 0:
                // Compass at top → buttons at bottom edge
                narrowerParams.gravity = android.view.Gravity.BOTTOM | android.view.Gravity.START;
                narrowerParams.setMargins(margin, 0, 0, margin);
                widerParams.gravity = android.view.Gravity.BOTTOM | android.view.Gravity.END;
                widerParams.setMargins(0, 0, margin, margin);
                // No rotation needed for portrait
                narrowerBtn.setRotation(0);
                widerBtn.setRotation(0);
                break;
            case 90:
                // Compass at right edge (canvas rotated 90° CW) → buttons at left edge
                narrowerParams.gravity = android.view.Gravity.START | android.view.Gravity.TOP;
                narrowerParams.setMargins(margin, margin, 0, 0);
                widerParams.gravity = android.view.Gravity.START | android.view.Gravity.BOTTOM;
                widerParams.setMargins(margin, 0, 0, margin);
                // Rotate buttons to appear upright in landscape
                narrowerBtn.setRotation(-90);
                widerBtn.setRotation(-90);
                break;
            case 180:
                // Compass at bottom → buttons at top edge
                narrowerParams.gravity = android.view.Gravity.TOP | android.view.Gravity.START;
                narrowerParams.setMargins(margin, margin, 0, 0);
                widerParams.gravity = android.view.Gravity.TOP | android.view.Gravity.END;
                widerParams.setMargins(0, margin, margin, 0);
                // No rotation needed for portrait
                narrowerBtn.setRotation(0);
                widerBtn.setRotation(0);
                break;
            case 270:
                // Compass at left edge (canvas rotated 90° CCW) → buttons at right edge
                narrowerParams.gravity = android.view.Gravity.END | android.view.Gravity.TOP;
                narrowerParams.setMargins(0, margin, margin, 0);
                widerParams.gravity = android.view.Gravity.END | android.view.Gravity.BOTTOM;
                widerParams.setMargins(0, 0, margin, margin);
                // Rotate buttons to appear upright in landscape
                narrowerBtn.setRotation(90);
                widerBtn.setRotation(90);
                break;
            default:
                // Default to bottom edge
                narrowerParams.gravity = android.view.Gravity.BOTTOM | android.view.Gravity.START;
                narrowerParams.setMargins(margin, 0, 0, margin);
                widerParams.gravity = android.view.Gravity.BOTTOM | android.view.Gravity.END;
                widerParams.setMargins(0, 0, margin, margin);
                // No rotation for default
                narrowerBtn.setRotation(0);
                widerBtn.setRotation(0);
                break;
        }
        
        narrowerBtn.setLayoutParams(narrowerParams);
        widerBtn.setLayoutParams(widerParams);
    }

}
