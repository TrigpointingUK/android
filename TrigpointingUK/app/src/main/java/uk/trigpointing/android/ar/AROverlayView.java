package uk.trigpointing.android.ar;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.location.Location;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;
import android.view.MotionEvent;

import androidx.core.content.ContextCompat;
import androidx.preference.PreferenceManager;

import java.io.IOException;
import java.io.InputStream;
import java.util.Locale;
import java.util.ArrayList;
import java.util.List;

import uk.trigpointing.android.types.Trig;

/**
 * Custom view that draws trigpoint overlays on top of camera for sensor-based AR
 */
public class AROverlayView extends View {
    private static final String TAG = "AROverlayView";
    
    private Paint textPaint;
    private Paint iconPaint;
    private List<TrigpointData> trigpoints = new ArrayList<>();
    private float deviceAzimuth = 0;
    private float devicePitch = 0;
    private float deviceRoll = 0;
    private float compassSnapAngleDeg = 0f; // remembers last snapped angle for hysteresis
    private Location currentLocation;
    private final List<HitTarget> hitTargets = new ArrayList<>();
    private OnTrigpointClickListener clickListener;
    
    // Pre-allocated objects to avoid DrawAllocation warnings
    private final List<TrigpointData> sortedTrigpoints = new ArrayList<>();
    private final Location tempLocation1 = new Location("temp");
    private final Location tempLocation2 = new Location("temp");
    private final Location trigLocation = new Location("trigpoint");
    
    // Field of view (degrees) used for mapping
    // X uses horizontal FOV across screen width; Y uses vertical FOV across screen height
    private float fieldOfViewDegX = 60.0f;
    private float fieldOfViewDegY = 45.0f;
    private static final float COMPASS_HYSTERESIS_DEG = 7.0f;
    
    // Simple data holder for trigpoint information
    public static class TrigpointData {
        long id;
        String name;
        double lat;
        double lon;
        String type;
        String condition;
        
        public TrigpointData(long id, String name, double lat, double lon, String type, String condition) {
            this.id = id;
            this.name = name;
            this.lat = lat;
            this.lon = lon;
            this.type = type;
            this.condition = condition;
        }
        
        public long getId() { return id; }
        public String getName() { return name; }
        public double getLat() { return lat; }
        public double getLon() { return lon; }
        public String getType() { return type; }
        public String getCondition() { return condition; }
    }
    
    public AROverlayView(Context context, AttributeSet attrs) {
        super(context, attrs);
        initPaints();
    }
    
    private void initPaints() {
        textPaint = new Paint();
        textPaint.setColor(Color.WHITE);
        textPaint.setTextSize(40);
        textPaint.setAntiAlias(true);
        textPaint.setShadowLayer(2, 1, 1, Color.BLACK);
        
        iconPaint = new Paint();
        iconPaint.setAntiAlias(true);
    }
    
    // Compass directions with their bearings
    private static final String[] COMPASS_DIRECTIONS = {
        "N", "NNE", "NE", "ENE", "E", "ESE", "SE", "SSE", 
        "S", "SSW", "SW", "WSW", "W", "WNW", "NW", "NNW"
    };
    private static final float[] COMPASS_BEARINGS = {
        0f, 22.5f, 45f, 67.5f, 90f, 112.5f, 135f, 157.5f,
        180f, 202.5f, 225f, 247.5f, 270f, 292.5f, 315f, 337.5f
    };
    
    public void updateTrigpoints(List<TrigpointData> trigpoints) {
        this.trigpoints = new ArrayList<>(trigpoints);
        invalidate(); // Trigger redraw
    }

    public interface OnTrigpointClickListener {
        void onTrigpointClick(long trigId);
    }

    public void setOnTrigpointClickListener(OnTrigpointClickListener listener) {
        this.clickListener = listener;
    }
    
    /**
     * Listener for compass orientation changes.
     * Called when the compass bar snaps to a different edge of the screen.
     */
    public interface OnCompassOrientationChangeListener {
        /**
         * Called when the compass orientation changes.
         * @param snappedAngle The snapped angle: 0 (top), 90 (left), 180 (bottom), 270 (right)
         */
        void onCompassOrientationChanged(int snappedAngle);
    }
    
    private OnCompassOrientationChangeListener compassOrientationListener;
    private int lastReportedSnapAngle = -1;
    
    public void setOnCompassOrientationChangeListener(OnCompassOrientationChangeListener listener) {
        this.compassOrientationListener = listener;
    }
    
    public void updateOrientation(float azimuth, float pitch, float roll) {
        this.deviceAzimuth = azimuth;
        this.devicePitch = pitch;
        this.deviceRoll = roll;
        invalidate(); // Trigger redraw
    }
    
    public void setCurrentLocation(Location location) {
        this.currentLocation = location;
    }
    
    // Allow activity to set FOV based on camera characteristics and/or user calibration
    public void setFieldOfViewDegrees(float horizontalFovDegrees, float verticalFovDegrees) {
        if (!Float.isNaN(horizontalFovDegrees) && horizontalFovDegrees > 0f) {
            this.fieldOfViewDegX = Math.max(20f, Math.min(120f, horizontalFovDegrees));
        }
        if (!Float.isNaN(verticalFovDegrees) && verticalFovDegrees > 0f) {
            this.fieldOfViewDegY = Math.max(20f, Math.min(120f, verticalFovDegrees));
        }
        invalidate();
    }

    // Backwards-compatible setter used by older callers; affects X axis mapping
    public void setFieldOfViewDegrees(float horizontalFovDegrees) {
        setFieldOfViewDegrees(horizontalFovDegrees, this.fieldOfViewDegY);
    }
    
    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        
        int screenWidth = getWidth();
        int screenHeight = getHeight();
        float verticalFieldOfView = fieldOfViewDegY; // degrees across height (Y mapping)
        float fieldOfView = fieldOfViewDegX; // original behavior: map across X FOV only
        
        // Calculate the diagonal FOV to ensure we show all trigpoints that could potentially 
        // be visible at any screen rotation. This uses the diagonal of the FOV rectangle.
        float maxPossibleFOV = calculateDiagonalFOV(fieldOfViewDegX, fieldOfViewDegY);
        
        // Reset per-frame hit targets
        hitTargets.clear();

        // Draw compass directions snapped to the edge closest to zenith, with hysteresis
        float snapAngle = updateCompassSnapAngle(deviceRoll);
        int snapped = ((int) Math.round(((snapAngle % 360f) + 360f) % 360f));
        
        // Notify listener if snap angle changed
        if (snapped != lastReportedSnapAngle && compassOrientationListener != null) {
            lastReportedSnapAngle = snapped;
            final int finalSnapped = snapped;
            post(() -> {
                if (compassOrientationListener != null) {
                    compassOrientationListener.onCompassOrientationChanged(finalSnapped);
                }
            });
        }
        
        boolean anchorTop = (snapped != 180); // top for 0/90/270, bottom for 180
        boolean landscapeSnap = (snapped == 90 || snapped == 270);
        float effectiveHorizontalFov = landscapeSnap ? fieldOfViewDegY : fieldOfViewDegX;
        if (snapped == 90) {
            // Rotate canvas so text is upright at the top long edge (anticlockwise landscape)
            canvas.save();
            canvas.rotate(90);
            canvas.translate(0, -screenWidth);
            drawCompassDirections(canvas, screenHeight, effectiveHorizontalFov, true, screenWidth);
            canvas.restore();
        } else if (snapped == 270) {
            // Rotate canvas so text is upright at the top long edge (clockwise landscape)
            canvas.save();
            canvas.rotate(-90);
            canvas.translate(-screenHeight, 0);
            drawCompassDirections(canvas, screenHeight, effectiveHorizontalFov, true, screenWidth);
            canvas.restore();
        } else {
            // 0 or 180 portrait variants
            drawCompassDirections(canvas, screenWidth, effectiveHorizontalFov, anchorTop, screenHeight);
        }
        
        if (trigpoints.isEmpty() || currentLocation == null) {
            return;
        }
        
        // Sort trigpoints by distance (farthest first) so nearest appear on top
        sortedTrigpoints.clear();
        sortedTrigpoints.addAll(trigpoints);
        sortedTrigpoints.sort((t1, t2) -> {
            tempLocation1.setLatitude(t1.getLat());
            tempLocation1.setLongitude(t1.getLon());
            float dist1 = currentLocation.distanceTo(tempLocation1);
            
            tempLocation2.setLatitude(t2.getLat());
            tempLocation2.setLongitude(t2.getLon());
            float dist2 = currentLocation.distanceTo(tempLocation2);
            
            return Float.compare(dist2, dist1); // Farthest first (reverse order)
        });
        
        // Rotate canvas to follow device roll so trig overlay stays aligned with horizon
        canvas.save();
        canvas.rotate(deviceRoll, screenWidth / 2f, screenHeight / 2f);

        for (TrigpointData trig : sortedTrigpoints) {
            // Calculate bearing to trigpoint
            trigLocation.setLatitude(trig.getLat());
            trigLocation.setLongitude(trig.getLon());
            
            float bearing = currentLocation.bearingTo(trigLocation);
            if (bearing < 0) bearing += 360; // Normalize to 0-360
            
            float distance = currentLocation.distanceTo(trigLocation);
            
            // Calculate relative bearing (difference between trigpoint bearing and device bearing)
            float relativeBearing = bearing - deviceAzimuth;
            
            // Normalize relative bearing to -180 to 180
            while (relativeBearing > 180) relativeBearing -= 360;
            while (relativeBearing < -180) relativeBearing += 360;
            
            // Draw trigpoints within the maximum possible FOV (so they're visible in any rotation)
            if (Math.abs(relativeBearing) <= maxPossibleFOV / 2f) {
                float screenX = screenWidth / 2f + (relativeBearing / (fieldOfView / 2f)) * (screenWidth / 2f);
                
                // Place at horizon line based on camera elevation, clamped to 15% from edges if out of range
                float centerY = screenHeight / 2f;
                float halfHeight = screenHeight / 2f;
                float halfVFov = verticalFieldOfView / 2f;
                // devicePitch carries camera elevation (+ up). When tilting up, horizon appears lower -> move line down (increase Y)
                float screenY = centerY + (devicePitch / halfVFov) * halfHeight;
                float minY = screenHeight * 0.15f;
                float maxY = screenHeight * 0.85f;
                if (screenY < minY) screenY = minY;
                if (screenY > maxY) screenY = maxY;
                
                // Draw trigpoint icon
                drawTrigpointIcon(canvas, trig, screenX, screenY, distance);
            }
        }

        canvas.restore();
    }

    // Expose vertical FOV (used as maximum horizontal FOV in landscape)
    public float getVerticalFieldOfViewDegrees() { return fieldOfViewDegY; }
    
    // Expose diagonal FOV for consistent filtering in activity
    public float getDiagonalFieldOfViewDegrees() { 
        return calculateDiagonalFOV(fieldOfViewDegX, fieldOfViewDegY); 
    }
    
    private void drawCompassDirections(Canvas canvas, int spanPixels, float fieldOfView, boolean anchorTop, int screenHeight) {
        Paint compassPaint = new Paint();
        compassPaint.setColor(Color.WHITE);
        compassPaint.setTextSize(36); // Slightly smaller than trigpoint text
        compassPaint.setAntiAlias(true);
        compassPaint.setShadowLayer(2, 1, 1, Color.BLACK);
        compassPaint.setTypeface(android.graphics.Typeface.MONOSPACE);
        
        float margin = 60f;
        float compassYTop = margin; // Position near top of screen
        float compassYBottom = screenHeight - margin; // Near bottom
        float compassY = anchorTop ? compassYTop : compassYBottom;
        
        for (int i = 0; i < COMPASS_DIRECTIONS.length; i++) {
            float bearing = COMPASS_BEARINGS[i];
            
            // Calculate relative bearing (difference between compass bearing and device bearing)
            float relativeBearing = bearing - deviceAzimuth;
            
            // Normalize relative bearing to -180 to 180
            while (relativeBearing > 180) relativeBearing -= 360;
            while (relativeBearing < -180) relativeBearing += 360;
            
            // Only draw compass directions within field of view
            if (Math.abs(relativeBearing) <= fieldOfView / 2) {
                // Calculate screen position
                float screenX = spanPixels / 2f + (relativeBearing / (fieldOfView / 2f)) * (spanPixels / 2f);
                
                String direction = COMPASS_DIRECTIONS[i];
                Rect textBounds = new Rect();
                compassPaint.getTextBounds(direction, 0, direction.length(), textBounds);
                
                // Center the text horizontally
                float textX = screenX - textBounds.width() / 2;
                
                // Draw text background
                canvas.drawRect(
                    textX - 8, compassY - textBounds.height() - 8,
                    textX + textBounds.width() + 8, compassY + 8,
                    new Paint() {{ setColor(0x80000000); }} // Semi-transparent black
                );
                
                canvas.drawText(direction, textX, compassY, compassPaint);
            }
        }
    }

    // Compute nearest 0/90/180/270 snap with hysteresis around the 45° boundaries
    private float updateCompassSnapAngle(float rollDeg) {
        // Normalize roll to [-180, 180)
        float r = rollDeg;
        while (r >= 180f) r -= 360f;
        while (r < -180f) r += 360f;

        // Determine the nearest bucket index for current roll
        int nearestIdx = Math.round(r / 90f); // -2..2
        if (nearestIdx == -2) nearestIdx = 2; // normalize -180 to 180
        float nearestAngle = nearestIdx * 90f;

        // If we have no previous snap, adopt nearest immediately
        if (compassSnapAngleDeg == 0f && Math.abs(r) < 1f) {
            compassSnapAngleDeg = 0f;
            return 0f;
        } else if (Float.isNaN(compassSnapAngleDeg)) {
            compassSnapAngleDeg = nearestAngle;
            return nearestAngle;
        }

        // Compute current snapped index
        int currentIdx = Math.round(compassSnapAngleDeg / 90f);
        if (currentIdx == -2) currentIdx = 2;
        float currentAngle = currentIdx * 90f;

        // Only switch when roll crosses beyond ±(45 + hysteresis) from current bucket center
        float lower = wrapDegrees(currentAngle - (45f + COMPASS_HYSTERESIS_DEG));
        float upper = wrapDegrees(currentAngle + (45f + COMPASS_HYSTERESIS_DEG));

        if (angleIsLess(r, lower)) {
            currentIdx -= 1;
        } else if (angleIsGreater(r, upper)) {
            currentIdx += 1;
        }

        if (currentIdx < -2) currentIdx = -2;
        if (currentIdx > 2) currentIdx = 2;

        compassSnapAngleDeg = (currentIdx == -2 ? 180f : currentIdx * 90f);
        return compassSnapAngleDeg;
    }

    // Helpers to compare wrapped angles safely around -180/180 boundaries
    private float wrapDegrees(float deg) {
        float d = deg;
        while (d >= 180f) d -= 360f;
        while (d < -180f) d += 360f;
        return d;
    }
    private boolean angleIsLess(float a, float b) {
        float da = wrapDegrees(a);
        float db = wrapDegrees(b);
        return da - db < 0f;
    }
    private boolean angleIsGreater(float a, float b) {
        float da = wrapDegrees(a);
        float db = wrapDegrees(b);
        return da - db > 0f;
    }
    
    /**
     * Calculate the diagonal field of view given horizontal and vertical FOVs.
     * This represents the maximum angular span that could be visible at any screen rotation.
     * 
     * @param horizontalFovDeg Horizontal field of view in degrees
     * @param verticalFovDeg Vertical field of view in degrees
     * @return Diagonal field of view in degrees
     */
    private float calculateDiagonalFOV(float horizontalFovDeg, float verticalFovDeg) {
        // Convert to radians for calculation
        float hFovRad = (float) Math.toRadians(horizontalFovDeg / 2.0);
        float vFovRad = (float) Math.toRadians(verticalFovDeg / 2.0);
        
        // Calculate diagonal half-angle using: diagonal = sqrt(tan²(h/2) + tan²(v/2))
        float tanH = (float) Math.tan(hFovRad);
        float tanV = (float) Math.tan(vFovRad);
        float diagonalHalfRad = (float) Math.atan(Math.sqrt(tanH * tanH + tanV * tanV));
        
        // Convert back to degrees and return full angle
        return (float) Math.toDegrees(diagonalHalfRad * 2.0);
    }
    
    /**
     * Transform coordinates from rotated canvas space to where they actually appear on screen.
     * When canvas.rotate(deviceRoll, centerX, centerY) is applied, a point drawn at (canvasX, canvasY)
     * in the rotated coordinate system will actually appear at a different location on screen.
     * 
     * @param canvasX X coordinate in rotated canvas space
     * @param canvasY Y coordinate in rotated canvas space  
     * @param iconSize Size of the icon (for debugging)
     * @return Array containing [screenX, screenY] where the point actually appears on screen
     */
    private float[] transformToScreenCoordinates(float canvasX, float canvasY, int iconSize) {
        int screenWidth = getWidth();
        int screenHeight = getHeight();
        float centerX = screenWidth / 2f;
        float centerY = screenHeight / 2f;
        
        // Translate to origin (center of rotation)
        float x = canvasX - centerX;
        float y = canvasY - centerY;
        
        // Apply the SAME rotation that was applied to the canvas
        // This tells us where the point actually appears on screen
        double rollRad = Math.toRadians(deviceRoll);
        float cos = (float) Math.cos(rollRad);
        float sin = (float) Math.sin(rollRad);
        
        float rotatedX = x * cos - y * sin;
        float rotatedY = x * sin + y * cos;
        
        // Translate back to screen coordinates
        float finalX = rotatedX + centerX;
        float finalY = rotatedY + centerY;
        
        // Transform completed
        
        return new float[]{finalX, finalY};
    }
    
        private void drawTrigpointIcon(Canvas canvas, TrigpointData trig, float x, float y, float distance) {
        try {
            // Get trigpoint icon (same source as Leaflet map)
            Drawable icon = getTrigpointIconDrawable(trig);

            if (icon != null) {
                // Scale icon based on distance (closer = larger, but with reasonable limits)
                float scale = Math.max(0.3f, Math.min(1.0f, 1000.0f / distance));
                int iconSize = (int) (213 * scale); // Base size 213dp (1/3 of 640dp)
                
                // Draw icon
                int left = (int) (x - iconSize / 2);
                int top = (int) (y - iconSize / 2);
                int right = (int) (x + iconSize / 2);
                int bottom = (int) (y + iconSize / 2);
                icon.setBounds(left, top, right, bottom);
                icon.draw(canvas);
                
                // We'll create a single comprehensive hit target after drawing all elements
                
                // Draw trigpoint name below icon
                String text = trig.getName();
                Rect textBounds = new Rect();
                textPaint.getTextBounds(text, 0, text.length(), textBounds);
                
                float textX = x - textBounds.width() / 2;
                float textY = y + iconSize / 2 + textBounds.height() + 10;
                
                // Draw text background
                canvas.drawRect(
                    textX - 5, textY - textBounds.height() - 5,
                    textX + textBounds.width() + 5, textY + 5,
                    new Paint() {{ setColor(0x80000000); }} // Semi-transparent black
                );
                
                canvas.drawText(text, textX, textY, textPaint);
                
                // Store text position for comprehensive hit target calculation
                
                // Draw distance
                String distanceText = String.format(Locale.getDefault(), "%.0fm", distance);
                textPaint.getTextBounds(distanceText, 0, distanceText.length(), textBounds);
                float distanceX = x - textBounds.width() / 2;
                float distanceY = textY + textBounds.height() + 10;
                
                canvas.drawRect(
                    distanceX - 5, distanceY - textBounds.height() - 5,
                    distanceX + textBounds.width() + 5, distanceY + 5,
                    new Paint() {{ setColor(0x80000000); }}
                );
                
                canvas.drawText(distanceText, distanceX, distanceY, textPaint);
                
                // Create a single comprehensive hit target that covers the entire trigpoint area
                // Calculate the bounding area in canvas coordinates
                float canvasTop = y - iconSize / 2; // Top of icon
                float canvasBottom = distanceY + 5; // Bottom of distance text with padding
                float canvasLeft = Math.min(x - iconSize / 2, Math.min(textX - 5, distanceX - 5)); // Leftmost edge
                float canvasRight = Math.max(x + iconSize / 2, Math.max(textX + textBounds.width() + 5, distanceX + textBounds.width() + 5)); // Rightmost edge
                
                // Transform the center and calculate screen hit target
                float canvasCenterX = (canvasLeft + canvasRight) / 2;
                float canvasCenterY = (canvasTop + canvasBottom) / 2;
                float[] screenCenter = transformToScreenCoordinates(canvasCenterX, canvasCenterY, 0);
                
                // Use a generous circular hit area that encompasses the entire trigpoint
                float canvasWidth = canvasRight - canvasLeft;
                float canvasHeight = canvasBottom - canvasTop;
                int hitRadius = (int) (Math.max(canvasWidth, canvasHeight) / 2 + 20); // Extra generous padding
                
                int screenLeft = (int) (screenCenter[0] - hitRadius);
                int screenTop = (int) (screenCenter[1] - hitRadius);
                int screenRight = (int) (screenCenter[0] + hitRadius);
                int screenBottom = (int) (screenCenter[1] + hitRadius);
                
                hitTargets.add(new HitTarget(screenLeft, screenTop, screenRight, screenBottom, trig.getId()));
            }
        } catch (Exception e) {
            Log.e(TAG, "Error drawing trigpoint icon for " + trig.getName(), e);
        }
    }

    private static class HitTarget {
        final Rect rect;
        final long trigId;
        HitTarget(int left, int top, int right, int bottom, long trigId) {
            this.rect = new Rect(left, top, right, bottom);
            this.trigId = trigId;
        }
    }
    
        private Drawable getTrigpointIconDrawable(TrigpointData trigpoint) {
        // Get user's map icon style preference (same as Leaflet maps)
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getContext());
        String iconStyle = prefs.getString("map_icon_style", "medium");
        
        // Determine icon path using same logic as Leaflet map
        String iconPath = getTrigIconPath(trigpoint.getType(), trigpoint.getCondition(), iconStyle);
        
        try {
            // Load icon from assets folder (same source as Leaflet map)
            InputStream inputStream = getContext().getAssets().open("leaflet/icons/" + iconPath);
            Drawable drawable = Drawable.createFromStream(inputStream, null);
            inputStream.close();
            return drawable;
        } catch (IOException e) {
            Log.w(TAG, "Failed to load icon from assets: " + iconPath + ", falling back to default");
            // Fallback to default Android drawable
            Trig.Physical physicalType = Trig.Physical.fromCode(trigpoint.getType());
            int iconRes = physicalType.icon(true);
            return ContextCompat.getDrawable(getContext(), iconRes);
        }
    }
    
    private String getTrigIconPath(String type, String condition, String iconStyle) {
        // Map database type codes to icon type names (same as Leaflet)
        String iconType;
        switch (type) {
            case "PI": iconType = "pillar"; break;
            case "FB": iconType = "fbm"; break;
            case "IN": iconType = "intersected"; break;
            default: iconType = "passive"; break;
        }
        
        // Determine if icon should be flagged/highlighted (same logic as Leaflet)
        boolean flagged = false; // TODO: Add marked status check when available
        
        // Determine color based on condition (same logic as Leaflet)
        String color = getConditionColor(condition);
        
        // Generate icon path based on style (same logic as Leaflet)
        if ("symbols".equals(iconStyle)) {
            // Use symbol icons with full color support
            String highlight = flagged ? "_h" : "";
            return "symbolicon_" + iconType + "_" + color + highlight + ".png";
        } else if ("types".equals(iconStyle)) {
            // Use type icons with full color support  
            String highlight = flagged ? "_h" : "";
            return "typeicon_" + iconType + "_" + color + highlight + ".png";
        } else {
            // Use colored logo icons (small/medium/large)
            String highlight = flagged ? "_h" : "";
            return "mapicon_" + iconType + "_" + color + highlight + ".png";
        }
    }
    
    private String getConditionColor(String condition) {
        // Same color logic as Leaflet map
        switch (condition) {
            case "G": return "green";    // Good
            case "S": return "yellow";   // Slightly damaged
            case "C": return "yellow";   // Converted  
            case "D": return "red";      // Damaged
            case "R": return "red";      // Remains
            case "T": return "red";      // Toppled
            case "M": return "red";      // Moved
            case "Q": return "red";      // Possibly missing
            case "X": return "red";      // Destroyed
            case "N": return "red";      // Couldn't find
            case "V": return "yellow";   // Visible but unreachable
            case "P": return "grey";     // Inaccessible
            case "U": return "grey";     // Unknown
            case "-": return "grey";     // Not visited
            case "Z": return "grey";     // Not logged
            default: return "green";     // Default to green
        }
    }
    
    @Override
    public boolean performClick() {
        // Handle click events on trigpoint icons
        return super.performClick();
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (event.getAction() == MotionEvent.ACTION_UP) {
            float x = event.getX();
            float y = event.getY();
            
            // Check topmost first (draw order)
            // Hit targets are recorded in screen coordinates, so no transformation needed
            for (int i = hitTargets.size() - 1; i >= 0; i--) {
                HitTarget t = hitTargets.get(i);
                if (t.rect.contains((int) x, (int) y)) {
                    if (clickListener != null) {
                        clickListener.onTrigpointClick(t.trigId);
                    }
                    performClick();
                    return true;
                }
            }
        }
        return true;
    }
}
