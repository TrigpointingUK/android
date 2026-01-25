package uk.trigpointing.android;

import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.location.Location;

import androidx.preference.PreferenceManager;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import uk.trigpointing.android.types.Condition;
import uk.trigpointing.android.types.PhotoSubject;
import uk.trigpointing.android.types.Trig;
import uk.trigpointing.android.mapping.BoundingBox;

import static org.junit.Assert.*;

/**
 * Integration tests for DbHelper class
 * Tests database operations, CRUD functionality, and data integrity
 */
@RunWith(AndroidJUnit4.class)
public class DbHelperIntegrationTest {

    private DbHelper dbHelper;
    private Context context;

    @Before
    public void setUp() throws Exception {
        context = ApplicationProvider.getApplicationContext();
        dbHelper = new DbHelper(context);
        dbHelper.open();
        
        // Clean up any existing data
        dbHelper.deleteAll();
        dbHelper.clearUserLogs();
    }

    @After
    public void tearDown() throws Exception {
        if (dbHelper != null) {
            // Clean up test data
            dbHelper.deleteAll();
            dbHelper.clearUserLogs();
            dbHelper.close();
        }
    }

    @Test
    public void testDatabaseInitialization() {
        // Test that database opens successfully
        assertNotNull("DbHelper should be initialized", dbHelper);
        assertNotNull("Database should be open", dbHelper.mDb);
        assertTrue("Database should be open", dbHelper.mDb.isOpen());
    }

    @Test
    public void testCreateAndRetrieveTrig() {
        // Create test trigpoint (Helvellyn)
        long trigId = 1001;
        String name = "Helvellyn";
        String waypoint = "LD0001";
        double lat = 54.5270;
        double lon = -3.0165;
        
        long result = dbHelper.createTrig(
            trigId, name, waypoint, lat, lon,
            Trig.Physical.PILLAR, 
            Condition.GOOD, 
            Condition.TRIGNOTLOGGED,
            Trig.Current.ACTIVE, 
            Trig.Historic.PRIMARY,
            "Test FB content"
        );
        
        assertTrue("Trig creation should succeed", result != -1);
        
        // Retrieve the trigpoint
        Cursor cursor = dbHelper.fetchTrigInfo(trigId);
        assertNotNull("Cursor should not be null", cursor);
        assertTrue("Cursor should have data", cursor.moveToFirst());
        
        assertEquals("Name should match", name, cursor.getString(cursor.getColumnIndex(DbHelper.TRIG_NAME)));
        // Note: fetchTrigInfo doesn't return TRIG_WAYPOINT, so we'll skip that check
        assertEquals("Latitude should match", lat, cursor.getDouble(cursor.getColumnIndex(DbHelper.TRIG_LAT)), 0.0001);
        assertEquals("Longitude should match", lon, cursor.getDouble(cursor.getColumnIndex(DbHelper.TRIG_LON)), 0.0001);
        assertEquals("Type should match", Trig.Physical.PILLAR.code(), cursor.getString(cursor.getColumnIndex(DbHelper.TRIG_TYPE)));
        
        cursor.close();
    }

    @Test
    public void testUpdateTrigLog() {
        // Create test trigpoint
        long trigId = 1002;
        dbHelper.createTrig(
            trigId, "Test Trig", "TEST001", 54.0, -3.0,
            Trig.Physical.PILLAR, Condition.GOOD, Condition.TRIGNOTLOGGED,
            Trig.Current.ACTIVE, Trig.Historic.PRIMARY, null
        );
        
        // Update log status
        boolean updateResult = dbHelper.updateTrigLog(trigId, Condition.GOOD);
        assertTrue("Update should succeed", updateResult);
        
        // Verify update
        Cursor cursor = dbHelper.fetchTrigInfo(trigId);
        assertNotNull(cursor);
        assertTrue(cursor.moveToFirst());
        assertEquals("Log status should be updated", 
            Condition.GOOD.code(), 
            cursor.getString(cursor.getColumnIndex(DbHelper.TRIG_LOGGED)));
        cursor.close();
    }

    @Test
    public void testTrigTablePopulation() {
        // Initially empty
        assertFalse("Table should be empty initially", dbHelper.isTrigTablePopulated());
        
        // Add a trigpoint
        dbHelper.createTrig(
            1003, "Test Trig", "TEST001", 54.0, -3.0,
            Trig.Physical.PILLAR, Condition.GOOD, Condition.TRIGNOTLOGGED,
            Trig.Current.ACTIVE, Trig.Historic.PRIMARY, null
        );
        
        // Now should be populated
        assertTrue("Table should be populated after adding trig", dbHelper.isTrigTablePopulated());
    }

    @Test
    public void testCountingMethods() {
        // Add test trigpoints of different types
        dbHelper.createTrig(1011, "Pillar 1", "P001", 54.0, -3.0,
            Trig.Physical.PILLAR, Condition.GOOD, Condition.GOOD,
            Trig.Current.ACTIVE, Trig.Historic.PRIMARY, null);
            
        dbHelper.createTrig(1012, "Pillar 2", "P002", 54.1, -3.1,
            Trig.Physical.PILLAR, Condition.GOOD, Condition.GOOD,
            Trig.Current.ACTIVE, Trig.Historic.PRIMARY, null);
            
        dbHelper.createTrig(1013, "FBM 1", "F001", 54.2, -3.2,
            Trig.Physical.FBM, Condition.GOOD, Condition.GOOD,
            Trig.Current.ACTIVE, Trig.Historic.PRIMARY, null);
            
        dbHelper.createTrig(1014, "Intersected 1", "I001", 54.3, -3.3,
            Trig.Physical.INTERSECTED, Condition.GOOD, Condition.GOOD,
            Trig.Current.ACTIVE, Trig.Historic.PRIMARY, null);
        
        // Test counting methods
        assertEquals("Should count 2 logged pillars", 2, dbHelper.countLoggedPillars());
        assertEquals("Should count 1 logged FBM", 1, dbHelper.countLoggedFbms());
        assertEquals("Should count 1 logged intersected", 1, dbHelper.countLoggedIntersecteds());
        assertEquals("Should count 0 logged passives", 0, dbHelper.countLoggedPassives());
    }

    @Test
    public void testLogOperations() {
        // Create a log entry
        long logId = 2001;
        long result = dbHelper.createLog(
            logId, 2024, 12, 25, 1640, 14, 30,
            "NY341151", "Test FB", Condition.GOOD, 10,
            "Test log comment", 0, 0
        );
        
        assertTrue("Log creation should succeed", result != -1);
        assertEquals("Should count 1 unsynced log", 1, dbHelper.countUnsynced());
        
        // Fetch the log
        Cursor cursor = dbHelper.fetchLog(logId);
        assertNotNull("Log cursor should not be null", cursor);
        assertTrue("Log cursor should have data", cursor.moveToFirst());
        
        assertEquals("Year should match", 2024, cursor.getInt(cursor.getColumnIndex(DbHelper.LOG_YEAR)));
        assertEquals("Month should match", 12, cursor.getInt(cursor.getColumnIndex(DbHelper.LOG_MONTH)));
        assertEquals("Day should match", 25, cursor.getInt(cursor.getColumnIndex(DbHelper.LOG_DAY)));
        assertEquals("Grid ref should match", "NY341151", cursor.getString(cursor.getColumnIndex(DbHelper.LOG_GRIDREF)));
        
        cursor.close();
        
        // Delete the log
        boolean deleteResult = dbHelper.deleteLog(logId);
        assertTrue("Log deletion should succeed", deleteResult);
        assertEquals("Should count 0 unsynced logs after deletion", 0, dbHelper.countUnsynced());
    }

    @Test
    public void testPhotoOperations() {
        // Create a trigpoint first
        long trigId = 3001;
        dbHelper.createTrig(
            trigId, "Photo Test Trig", "PT001", 54.0, -3.0,
            Trig.Physical.PILLAR, Condition.GOOD, Condition.TRIGNOTLOGGED,
            Trig.Current.ACTIVE, Trig.Historic.PRIMARY, null
        );
        
        // Create a photo
        long photoResult = dbHelper.createPhoto(
            trigId, "Test Photo", "Test Description",
            "icon.jpg", "photo.jpg", PhotoSubject.TRIGPOINT, 1
        );
        
        assertTrue("Photo creation should succeed", photoResult != -1);
        assertEquals("Should count 1 photo", 1, dbHelper.countPhotos());
        
        // Fetch the photo
        Cursor cursor = dbHelper.fetchPhoto(photoResult);
        assertNotNull("Photo cursor should not be null", cursor);
        assertTrue("Photo cursor should have data", cursor.moveToFirst());
        
        assertEquals("Trig ID should match", trigId, cursor.getLong(cursor.getColumnIndex(DbHelper.PHOTO_TRIG)));
        assertEquals("Name should match", "Test Photo", cursor.getString(cursor.getColumnIndex(DbHelper.PHOTO_NAME)));
        assertEquals("Description should match", "Test Description", cursor.getString(cursor.getColumnIndex(DbHelper.PHOTO_DESCR)));
        
        cursor.close();
        
        // Test photo deletion
        boolean deleteResult = dbHelper.deletePhoto(photoResult);
        assertTrue("Photo deletion should succeed", deleteResult);
        assertEquals("Should count 0 photos after deletion", 0, dbHelper.countPhotos());
    }

    @Test
    public void testMarkedTrigOperations() {
        long trigId = 4001;
        
        // Initially not marked
        assertFalse("Trig should not be marked initially", dbHelper.isMarkedTrig(trigId));
        
        // Mark the trig
        Boolean markResult = dbHelper.setMarkedTrig(trigId, true);
        assertTrue("Marking should succeed", markResult);
        assertTrue("Trig should be marked after setting", dbHelper.isMarkedTrig(trigId));
        
        // Unmark the trig
        Boolean unmarkResult = dbHelper.setMarkedTrig(trigId, false);
        assertFalse("Unmarking should return false", unmarkResult);
        assertFalse("Trig should not be marked after unsetting", dbHelper.isMarkedTrig(trigId));
    }

    @Test
    public void testFetchTrigListWithLocation() {
        // Create test trigpoints at different distances
        dbHelper.createTrig(5001, "Near Trig", "N001", 54.5270, -3.0165,
            Trig.Physical.PILLAR, Condition.GOOD, Condition.TRIGNOTLOGGED,
            Trig.Current.ACTIVE, Trig.Historic.PRIMARY, null);
            
        dbHelper.createTrig(5002, "Far Trig", "F001", 55.0, -4.0,
            Trig.Physical.PILLAR, Condition.GOOD, Condition.TRIGNOTLOGGED,
            Trig.Current.ACTIVE, Trig.Historic.PRIMARY, null);
        
        // Create a location near the first trigpoint
        Location testLocation = new Location("test");
        testLocation.setLatitude(54.5270);
        testLocation.setLongitude(-3.0165);
        
        Cursor cursor = dbHelper.fetchTrigList(testLocation);
        assertNotNull("Cursor should not be null", cursor);
        assertTrue("Should have results", cursor.getCount() > 0);
        
        // First result should be the nearest trigpoint
        cursor.moveToFirst();
        assertEquals("First result should be nearest trig", "Near Trig", 
            cursor.getString(cursor.getColumnIndex(DbHelper.TRIG_NAME)));
        
        cursor.close();
    }

    @Test
    public void testFetchTrigMapListWithBoundingBox() {
        // Set filter preferences to include all trigpoints (no filtering)
        SharedPreferences.Editor editor = PreferenceManager.getDefaultSharedPreferences(context).edit();
        editor.putInt("filter_type", 0); // Show all types
        editor.putInt("filter_radio", 0); // Show all found/not found
        editor.putString("mapcount", "1000"); // High limit to include all results
        editor.apply();
        
        // Create trigpoints inside and outside bounding box
        dbHelper.createTrig(6001, "Inside Trig", "I001", 54.5, -3.0,
            Trig.Physical.PILLAR, Condition.GOOD, Condition.TRIGNOTLOGGED,
            Trig.Current.ACTIVE, Trig.Historic.PRIMARY, null);
            
        dbHelper.createTrig(6002, "Outside Trig", "O001", 55.5, -4.5,
            Trig.Physical.PILLAR, Condition.GOOD, Condition.TRIGNOTLOGGED,
            Trig.Current.ACTIVE, Trig.Historic.PRIMARY, null);
        
        // Create bounding box around Lake District
        BoundingBox boundingBox = new BoundingBox(54.8, -2.5, 54.2, -3.5);
        
        Cursor cursor = dbHelper.fetchTrigMapList(boundingBox);
        assertNotNull("Cursor should not be null", cursor);
        assertEquals("Should have 1 result in bounding box", 1, cursor.getCount());
        
        cursor.moveToFirst();
        assertEquals("Result should be inside trig", "Inside Trig", 
            cursor.getString(cursor.getColumnIndex(DbHelper.TRIG_NAME)));
        
        cursor.close();
    }

    @Test
    public void testClearUserLogs() {
        // Create test data
        long trigId = 7001;
        dbHelper.createTrig(trigId, "Test Trig", "T001", 54.0, -3.0,
            Trig.Physical.PILLAR, Condition.GOOD, Condition.GOOD,
            Trig.Current.ACTIVE, Trig.Historic.PRIMARY, null);
            
        dbHelper.createLog(trigId, 2024, 12, 25, 1640, 14, 30,
            "NY341151", "Test", Condition.GOOD, 10, "Comment", 0, 0);
            
        dbHelper.createPhoto(trigId, "Photo", "Desc", "icon.jpg", "photo.jpg", 
            PhotoSubject.TRIGPOINT, 1);
            
        dbHelper.setMarkedTrig(trigId, true);
        
        // Verify data exists
        assertTrue("Should have unsynced logs", dbHelper.countUnsynced() > 0);
        assertTrue("Should have photos", dbHelper.countPhotos() > 0);
        assertTrue("Should have logged pillars", dbHelper.countLoggedPillars() > 0);
        assertTrue("Trig should be marked", dbHelper.isMarkedTrig(trigId));
        
        // Clear user logs
        dbHelper.clearUserLogs();
        
        // Verify data is cleared
        assertEquals("Should have no unsynced logs", 0, dbHelper.countUnsynced());
        assertEquals("Should have no photos", 0, dbHelper.countPhotos());
        assertEquals("Should have no logged pillars", 0, dbHelper.countLoggedPillars());
        assertFalse("Trig should not be marked", dbHelper.isMarkedTrig(trigId));
        
        // But trigpoint should still exist
        assertTrue("Trig table should still be populated", dbHelper.isTrigTablePopulated());
    }

    @Test
    public void testDatabaseTransaction() {
        // Test that database operations work correctly with transactions
        long trigId = 8001;
        
        // This should work as one unit
        dbHelper.mDb.beginTransaction();
        try {
            dbHelper.createTrig(trigId, "Transaction Test", "T001", 54.0, -3.0,
                Trig.Physical.PILLAR, Condition.GOOD, Condition.TRIGNOTLOGGED,
                Trig.Current.ACTIVE, Trig.Historic.PRIMARY, null);
                
            dbHelper.updateTrigLog(trigId, Condition.GOOD);
            
            dbHelper.mDb.setTransactionSuccessful();
        } finally {
            dbHelper.mDb.endTransaction();
        }
        
        // Verify the transaction completed successfully
        Cursor cursor = dbHelper.fetchTrigInfo(trigId);
        assertNotNull(cursor);
        assertTrue(cursor.moveToFirst());
        assertEquals("Log status should be updated", Condition.GOOD.code(), 
            cursor.getString(cursor.getColumnIndex(DbHelper.TRIG_LOGGED)));
        cursor.close();
    }

    // ==================== Heatmap Feature Tests ====================

    @Test
    public void testCountTrigpointsInBoundingBox() {
        // Set filter preferences to include all trigpoints (no filtering)
        SharedPreferences.Editor editor = PreferenceManager.getDefaultSharedPreferences(context).edit();
        editor.putInt("filter_type", 0); // Show all types
        editor.putInt("filter_radio", 0); // Show all found/not found
        editor.apply();
        
        // Create multiple trigpoints inside the bounding box
        dbHelper.createTrig(9001, "Inside Trig 1", "I001", 54.5, -3.0,
            Trig.Physical.PILLAR, Condition.GOOD, Condition.TRIGNOTLOGGED,
            Trig.Current.ACTIVE, Trig.Historic.PRIMARY, null);
            
        dbHelper.createTrig(9002, "Inside Trig 2", "I002", 54.6, -3.1,
            Trig.Physical.PILLAR, Condition.GOOD, Condition.TRIGNOTLOGGED,
            Trig.Current.ACTIVE, Trig.Historic.PRIMARY, null);
            
        dbHelper.createTrig(9003, "Inside Trig 3", "I003", 54.4, -2.9,
            Trig.Physical.FBM, Condition.GOOD, Condition.TRIGNOTLOGGED,
            Trig.Current.ACTIVE, Trig.Historic.PRIMARY, null);
            
        // Create trigpoint outside the bounding box
        dbHelper.createTrig(9004, "Outside Trig", "O001", 55.5, -4.5,
            Trig.Physical.PILLAR, Condition.GOOD, Condition.TRIGNOTLOGGED,
            Trig.Current.ACTIVE, Trig.Historic.PRIMARY, null);
        
        // Create bounding box that includes only the first 3 trigpoints
        BoundingBox boundingBox = new BoundingBox(54.8, -2.5, 54.2, -3.5);
        
        int count = dbHelper.countTrigpointsInBoundingBox(boundingBox);
        assertEquals("Should count 3 trigpoints inside bounding box", 3, count);
    }

    @Test
    public void testCountTrigpointsInBoundingBoxEmpty() {
        // Set filter preferences
        SharedPreferences.Editor editor = PreferenceManager.getDefaultSharedPreferences(context).edit();
        editor.putInt("filter_type", 0);
        editor.putInt("filter_radio", 0);
        editor.apply();
        
        // Create trigpoint outside the test bounding box
        dbHelper.createTrig(9101, "Outside", "O001", 55.5, -4.5,
            Trig.Physical.PILLAR, Condition.GOOD, Condition.TRIGNOTLOGGED,
            Trig.Current.ACTIVE, Trig.Historic.PRIMARY, null);
        
        // Create bounding box in empty area
        BoundingBox boundingBox = new BoundingBox(50.0, 0.0, 49.0, -1.0);
        
        int count = dbHelper.countTrigpointsInBoundingBox(boundingBox);
        assertEquals("Should count 0 trigpoints in empty area", 0, count);
    }

    @Test
    public void testFetchTrigpointCoordinates() {
        // Set filter preferences to include all trigpoints (no filtering)
        SharedPreferences.Editor editor = PreferenceManager.getDefaultSharedPreferences(context).edit();
        editor.putInt("filter_type", 0);
        editor.putInt("filter_radio", 0);
        editor.apply();
        
        // Create trigpoints with known coordinates
        double lat1 = 54.5, lon1 = -3.0;
        double lat2 = 54.6, lon2 = -3.1;
        double lat3 = 54.4, lon3 = -2.9;
        
        dbHelper.createTrig(9201, "Coord Test 1", "C001", lat1, lon1,
            Trig.Physical.PILLAR, Condition.GOOD, Condition.TRIGNOTLOGGED,
            Trig.Current.ACTIVE, Trig.Historic.PRIMARY, null);
            
        dbHelper.createTrig(9202, "Coord Test 2", "C002", lat2, lon2,
            Trig.Physical.PILLAR, Condition.GOOD, Condition.TRIGNOTLOGGED,
            Trig.Current.ACTIVE, Trig.Historic.PRIMARY, null);
            
        dbHelper.createTrig(9203, "Coord Test 3", "C003", lat3, lon3,
            Trig.Physical.FBM, Condition.GOOD, Condition.TRIGNOTLOGGED,
            Trig.Current.ACTIVE, Trig.Historic.PRIMARY, null);
        
        // Create bounding box
        BoundingBox boundingBox = new BoundingBox(54.8, -2.5, 54.2, -3.5);
        
        Cursor cursor = dbHelper.fetchTrigpointCoordinates(boundingBox);
        assertNotNull("Cursor should not be null", cursor);
        assertEquals("Should return 3 coordinates", 3, cursor.getCount());
        
        // Verify cursor has lat/lon columns
        int latIndex = cursor.getColumnIndex(DbHelper.TRIG_LAT);
        int lonIndex = cursor.getColumnIndex(DbHelper.TRIG_LON);
        assertTrue("Should have lat column", latIndex >= 0);
        assertTrue("Should have lon column", lonIndex >= 0);
        
        // Verify coordinates are returned correctly
        cursor.moveToFirst();
        do {
            double lat = cursor.getDouble(latIndex);
            double lon = cursor.getDouble(lonIndex);
            // Verify coordinates are within expected range
            assertTrue("Latitude should be valid", lat >= 54.2 && lat <= 54.8);
            assertTrue("Longitude should be valid", lon >= -3.5 && lon <= -2.5);
        } while (cursor.moveToNext());
        
        cursor.close();
    }

    @Test
    public void testFetchTrigpointCoordinatesNoLimit() {
        // Set filter preferences
        SharedPreferences.Editor editor = PreferenceManager.getDefaultSharedPreferences(context).edit();
        editor.putInt("filter_type", 0);
        editor.putInt("filter_radio", 0);
        editor.putString("mapcount", "2"); // Set very low limit for fetchTrigMapList
        editor.apply();
        
        // Create more trigpoints than the mapcount limit
        for (int i = 0; i < 5; i++) {
            dbHelper.createTrig(9300 + i, "Trig " + i, "T" + i, 54.5 + (i * 0.01), -3.0,
                Trig.Physical.PILLAR, Condition.GOOD, Condition.TRIGNOTLOGGED,
                Trig.Current.ACTIVE, Trig.Historic.PRIMARY, null);
        }
        
        // Create bounding box
        BoundingBox boundingBox = new BoundingBox(54.8, -2.5, 54.2, -3.5);
        
        // fetchTrigMapList should be limited by mapcount
        Cursor limitedCursor = dbHelper.fetchTrigMapList(boundingBox);
        assertEquals("fetchTrigMapList should be limited to 2", 2, limitedCursor.getCount());
        limitedCursor.close();
        
        // fetchTrigpointCoordinates should NOT be limited
        Cursor coordinatesCursor = dbHelper.fetchTrigpointCoordinates(boundingBox);
        assertEquals("fetchTrigpointCoordinates should return all 5", 5, coordinatesCursor.getCount());
        coordinatesCursor.close();
        
        // countTrigpointsInBoundingBox should also return total
        int count = dbHelper.countTrigpointsInBoundingBox(boundingBox);
        assertEquals("countTrigpointsInBoundingBox should return 5", 5, count);
    }
}
