package com.trigpointinguk.android;

import org.osmdroid.util.BoundingBoxE6;

import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.location.Location;
import android.preference.PreferenceManager;
import android.util.Log;

public class DbHelper {
	private static final String TAG					= "DbHelper";

	private static final int 	DATABASE_VERSION 	= 6;
	private static final String DATABASE_NAME		= "trigpointinguk";
	private static final String TRIG_TABLE			= "trig";
	public 	static final String TRIG_ID				= "_id";
	public 	static final String TRIG_NAME			= "name";
	public 	static final String TRIG_WAYPOINT		= "waypoint";
	public 	static final String TRIG_LAT			= "lat";
	public 	static final String TRIG_LON			= "lon";
	public 	static final String TRIG_TYPE			= "type";
	public 	static final String TRIG_CONDITION		= "condition";
	public 	static final String TRIG_LOGGED			= "logged";
	public 	static final String TRIG_CURRENT		= "current";
	public 	static final String TRIG_HISTORIC		= "historic";
	public 	static final String TRIG_FB				= "fb";
	private static final String LOG_TABLE			= "log";
	public 	static final String LOG_ID				= "_id";
	public 	static final String LOG_YEAR			= "year";
	public 	static final String LOG_MONTH			= "month";
	public 	static final String LOG_DAY				= "day";
	public 	static final String LOG_HOUR			= "hour";
	public 	static final String LOG_MINUTES			= "minutes";
	public 	static final String LOG_GRIDREF			= "gridref";
	public 	static final String LOG_FB				= "fb";
	public 	static final String LOG_CONDITION		= "condition";
	public  static final String LOG_SCORE   		= "score";
	public 	static final String LOG_COMMENT			= "comment";
	public 	static final String LOG_FLAGADMINS		= "flagadmins";
	public 	static final String LOG_FLAGUSERS		= "flagusers";
	public  static final String DEFAULT_MAP_COUNT   = "400";


	private static final String TRIG_CREATE = "create table trig (_id integer primary key, "
		+ "name text not null, waypoint text not null, "
		+ "lat real not null, lon real not null, " 
		+ "type integer not null, condition char(1) not null, logged condition char(1) not null, "
		+ "current integer not null, historic integer not null, fb text);";

	private static final String LOG_CREATE = "create table log (_id integer primary key, "
		+ "year integer not null, month integer not null, day integer not null, "
		+ "hour integer not null, minutes integer not null, gridref text, " 
		+ "fb text, condition char(1) not null, score integer not null, "
		+ "comment text, flagadmins integer not null, flagusers integer not null);";

	private DatabaseHelper mDbHelper;
	public SQLiteDatabase mDb;
    private SharedPreferences mPrefs; 
    	
	private final Context mCtx;


	private static class DatabaseHelper extends SQLiteOpenHelper {
		DatabaseHelper(Context context) {
			super(context, DATABASE_NAME, null, DATABASE_VERSION);
		}
		@Override
		public void onCreate(SQLiteDatabase db) {
			Log.i(TAG, "Creating database");
			db.execSQL(TRIG_CREATE);
			db.execSQL(LOG_CREATE);
		}
		@Override
		public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
			Log.w(TAG, "Upgrading database from version " + oldVersion + " to "
					+ newVersion + ", which will destroy all old data");
			db.execSQL("DROP TABLE IF EXISTS trig");
			db.execSQL("DROP TABLE IF EXISTS log");
			onCreate(db);
		}
	}
	
    /**
     * Constructor - takes the context to allow the database to be
     * opened/created
     * 
     * @param ctx the Context within which to work
     */
	public DbHelper(Context ctx) {
		this.mCtx = ctx;
		mPrefs = PreferenceManager.getDefaultSharedPreferences(mCtx);
	}

	/**
	 * Open the trigpointinguk database. If it cannot be opened, try to create a new
	 * instance of the database. If it cannot be created, throw an exception to
	 * signal the failure
	 * 
	 * @return this (self reference, allowing this to be chained in an initialisation call)
	 * @throws SQLException if the database could be neither opened or created
	 */
	public DbHelper open() throws SQLException {
		Log.i(TAG, "Opening mDbHelper");
		mDbHelper = new DatabaseHelper(mCtx);
		mDb = mDbHelper.getWritableDatabase();
		return this;
	}

	public void close() {
		Log.i(TAG, "Closing mDbHelper");
		mDbHelper.close();
	}


	/**
	 * Create a new trig using the data provided. If the trig is
	 * successfully created return the new rowId, otherwise return
	 * a -1 to indicate failure.
	 * 
	 * @param id
	 * @return rowId or -1 if failed
	 */
	public long createTrig(int id, String name, String waypoint, Double lat, Double lon, Trig.Physical type, Condition condition, Condition logged, Trig.Current current, Trig.Historic historic, String fb) {
		ContentValues initialValues = new ContentValues();
		initialValues.put(TRIG_ID			, id);
		initialValues.put(TRIG_NAME			, name);
		initialValues.put(TRIG_WAYPOINT		, waypoint);
		initialValues.put(TRIG_LAT			, lat);
		initialValues.put(TRIG_LON			, lon);
		initialValues.put(TRIG_TYPE			, type.code());
		initialValues.put(TRIG_CONDITION	, condition.code());
		initialValues.put(TRIG_LOGGED		, logged.code());
		initialValues.put(TRIG_CURRENT		, current.code());
		initialValues.put(TRIG_HISTORIC		, historic.code());
		initialValues.put(TRIG_FB			, fb);
		return mDb.insert(TRIG_TABLE, null, initialValues);
	}

	/**
	 * Update Trig Log
	 * 
	 * @return true if updated, false otherwise
	 */
	public boolean updateTrigLog(int id, Condition logged) {
		ContentValues args = new ContentValues();
		args.put(TRIG_LOGGED, logged.code());
		return mDb.update(TRIG_TABLE, args, TRIG_ID + "=" + id, null) > 0;
	}


	/**
	 * Delete all trigs
	 * 
	 * @return true if deleted, false otherwise
	 */
	public boolean deleteAll() {
		return mDb.delete(TRIG_TABLE, null, null) > 0;
	}

	
	/**
	 * Return a Cursor suitable for the triglist screen
	 * 
	 * @return Cursor 
	 */
	public Cursor fetchTrigList(Location loc) {
		String strOrder;	
   
		if (null != loc) {
			strOrder = String.format("(%3.3f-%s)*(%3.3f-%s) + %f * (%3.3f-%s)*(%3.3f-%s) LIMIT %s", loc.getLatitude(), TRIG_LAT, loc.getLatitude(), TRIG_LAT, 
					Math.pow(Math.cos(Math.toRadians(loc.getLatitude())),2), loc.getLongitude(), TRIG_LON, loc.getLongitude(), TRIG_LON, mPrefs.getString("listentries", "100"));
		} else {
			strOrder = TRIG_NAME + " LIMIT " +  mPrefs.getString("listentries", "100");
		}
		Log.i(TAG, strOrder);
		return mDb.query(TRIG_TABLE, new String[] {TRIG_ID, TRIG_NAME, TRIG_LAT, TRIG_LON, TRIG_TYPE, TRIG_CONDITION, TRIG_LOGGED}, null, null, null, null, strOrder);
	}
	
	
	
	/**
	 * Return a Cursor suitable for the map screen
	 * 
	 * @return Cursor 
	 */
	public Cursor fetchTrigMapList (BoundingBoxE6 box) {
		String strOrder = String.format("%s limit %s", TRIG_LAT, mPrefs.getString("mapcount", DEFAULT_MAP_COUNT));	
   
		String strWhere = String.format("%s between %3.6f and %3.6f  and  %s between %3.6f and %3.6f"
				, TRIG_LON, box.getLonWestE6()/1000000.0, box.getLonEastE6()/1000000.0, TRIG_LAT, box.getLatSouthE6()/1000000.0, box.getLatNorthE6()/1000000.0); 

		
		Log.i(TAG, strWhere);
		Log.i(TAG, strOrder);
		return mDb.query(TRIG_TABLE, new String[] {TRIG_ID, TRIG_NAME, TRIG_LAT, TRIG_LON, TRIG_TYPE, TRIG_CONDITION, TRIG_LOGGED}, strWhere, null, null, null, strOrder);
	}
	
	/**
	 * Return a Cursor suitable for the triglist screen
	 * 
	 * @return Cursor 
	 */
	public Cursor fetchTrigInfo (long id) {
		return mDb.query(TRIG_TABLE, new String[] {TRIG_ID, TRIG_NAME, TRIG_LAT, TRIG_LON, TRIG_TYPE, TRIG_CONDITION, TRIG_LOGGED, TRIG_CURRENT, TRIG_HISTORIC, TRIG_FB}, TRIG_ID + "="+id, null, null, null, null);
	}
	
	/**
	 * Returns whether the trig table contains data
	 * 
	 * @return int 
	 */
	public Boolean isTrigTablePopulated () {
		Cursor c =  mDb.query(TRIG_TABLE, new String[] {TRIG_ID}, null, null, null, null, null);
		if (c.getCount() == 0 ) {
			return false;
		}
		return true;
	}

	
	
	/**
	 * Create a new log.  If the record is
	 * successfully created return the new rowId, otherwise return
	 * a -1 to indicate failure.
	 * 
	 * @param id
	 * @return rowId or -1 if failed
	 */
	public long createLog(long id, int year, int month, int day, int hour, int minutes, String gridref, String fb, 
						Condition condition, int score, String comment, int flagadmins, int flagusers) {
		Log.i(TAG, "createLog - " + id);
		
		ContentValues initialValues = new ContentValues();
		initialValues.put(LOG_ID			, id);
		initialValues.put(LOG_YEAR			, year);
		initialValues.put(LOG_MONTH			, month);
		initialValues.put(LOG_DAY			, day);
		initialValues.put(LOG_HOUR			, hour);
		initialValues.put(LOG_MINUTES		, minutes);
		initialValues.put(LOG_GRIDREF		, gridref);
		initialValues.put(LOG_FB			, fb);
		initialValues.put(LOG_CONDITION		, condition.code());
		initialValues.put(LOG_SCORE			, score);
		initialValues.put(LOG_COMMENT		, comment);
		initialValues.put(LOG_FLAGADMINS	, flagadmins);
		initialValues.put(LOG_FLAGUSERS		, flagusers);
		return mDb.insert(LOG_TABLE, null, initialValues);
	}

	
	

	/**
	 * Delete individual log
	 * 
	 * @return true if deleted, false otherwise
	 */
	public boolean deleteLog(long id) {
		return mDb.delete(LOG_TABLE, LOG_ID + "=" + id, null) > 0;
	}


	/**
     * Return a Cursor positioned at the log that matches the given id
     * 
     * @param id of note to retrieve
     * @return Cursor positioned to matching log, if found
     * @throws SQLException if note could not be found/retrieved
     */
    public Cursor fetchLog(long id) throws SQLException {

        Cursor mCursor =
            mDb.query(true, LOG_TABLE, new String[] {LOG_ID, LOG_YEAR, LOG_MONTH, LOG_DAY, 
            				LOG_HOUR, LOG_MINUTES, LOG_GRIDREF, LOG_FB, LOG_CONDITION, 
            				LOG_COMMENT, LOG_SCORE, LOG_COMMENT, LOG_FLAGADMINS, LOG_FLAGUSERS}
                    , LOG_ID + "=" + id, null,
                    null, null, null, null);
        if (mCursor != null) {
            if (! mCursor.moveToFirst()) {
            	// No log row found
            	return null;
            }
        }
        return mCursor;
    }

	
	
}
