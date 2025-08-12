package com.trigpointinguk.android;

import org.osmdroid.util.BoundingBox;

import android.app.Activity;
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
import android.widget.Toast;

import com.trigpointinguk.android.filter.Filter;
import com.trigpointinguk.android.logging.SyncTask;
import com.trigpointinguk.android.types.Condition;
import com.trigpointinguk.android.types.PhotoSubject;
import com.trigpointinguk.android.types.Trig;

public class DbHelper {
	private static final String TAG					= "DbHelper";

	private static final int 	DATABASE_VERSION 	= 12;
	private static final String DATABASE_NAME		= "trigpointinguk";
	public  static final String TRIG_TABLE			= "trig";
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
	public  static final String LOG_TABLE			= "log";
	public 	static final String LOG_ID				= "_id";
	public 	static final String LOG_YEAR			= "year";
	public 	static final String LOG_MONTH			= "month";
	public 	static final String LOG_DAY				= "day";
	public  static final String LOG_SENDTIME        = "sendtime";
	public 	static final String LOG_HOUR			= "hour";
	public 	static final String LOG_MINUTES			= "minutes";
	public 	static final String LOG_GRIDREF			= "gridref";
	public 	static final String LOG_FB				= "fb";
	public 	static final String LOG_CONDITION		= "condition";
	public  static final String LOG_SCORE   		= "score";
	public 	static final String LOG_COMMENT			= "comment";
	public 	static final String LOG_FLAGADMINS		= "flagadmins";
	public 	static final String LOG_FLAGUSERS		= "flagusers";
	public  static final String PHOTO_TABLE         = "photo";
	public  static final String PHOTO_ID			= "_id";
	public  static final String PHOTO_TRIG          = "trig";
	public  static final String PHOTO_ICON          = "icon";
	public  static final String PHOTO_PHOTO         = "photo";
	public  static final String PHOTO_NAME          = "name";
	public  static final String PHOTO_DESCR         = "descr";
	public  static final String PHOTO_SUBJECT       = "subject";
	public  static final String PHOTO_ISPUBLIC      = "ispublic";
	public  static final String PHOTO_TUKLOGID      = "tuklogid";
	public  static final String MARK_TABLE          = "mark";
	public  static final String MARK_ID			    = "_id";	
	public  static final String JOIN_UNSYNCED       = "unsynced";
	public  static final String JOIN_MARKED         = "marked";


	public  static final String DEFAULT_MAP_COUNT   = "4000";


	private static final String TRIG_CREATE = "create table " + TRIG_TABLE + "("
		+ TRIG_ID 		 + " integer primary key, "
		+ TRIG_NAME		 + " text not null, "
		+ TRIG_WAYPOINT  + " text not null, "
		+ TRIG_LAT		 + " real not null, "
		+ TRIG_LON		 + " real not null, " 
		+ TRIG_TYPE		 + " integer not null, "
		+ TRIG_CONDITION + " char(1) not null, "
		+ TRIG_LOGGED    + " condition char(1) not null, "
		+ TRIG_CURRENT   + " integer not null, "
		+ TRIG_HISTORIC  + " integer not null, " 
		+ TRIG_FB		 + " text"
		+ ");";

	private static final String LOG_CREATE = "create table " + LOG_TABLE + "("
		+ LOG_ID		 + " integer primary key, "
		+ LOG_YEAR	     + " integer not null, "
		+ LOG_MONTH		 + " integer not null, "
		+ LOG_DAY		 + " integer not null, "
		+ LOG_SENDTIME	 + " integer not null, "
		+ LOG_HOUR		 + " integer not null, "
		+ LOG_MINUTES	 + " integer not null, "
		+ LOG_GRIDREF    + " text, " 
		+ LOG_FB	     + " text, "
		+ LOG_CONDITION  + " char(1) not null, "
		+ LOG_SCORE      + " integer not null, "
		+ LOG_COMMENT    + " text, "
		+ LOG_FLAGADMINS + " integer not null, "
		+ LOG_FLAGUSERS  + " integer not null"
		+ ");";

	private static final String PHOTO_CREATE = "create table " + PHOTO_TABLE + "(" 
		+ PHOTO_ID 		 + " integer primary key autoincrement, "
		+ PHOTO_TRIG 	 + " integer not null, "
		+ PHOTO_ICON 	 + " string not null, "
		+ PHOTO_PHOTO 	 + " string not null, "
		+ PHOTO_NAME 	 + " string not null, "
		+ PHOTO_DESCR    + " string not null, "
		+ PHOTO_SUBJECT  + " char(1) not null, " 
		+ PHOTO_ISPUBLIC + " integer not null, "
		+ PHOTO_TUKLOGID + " integer not null "
		+ ");";

	private static final String MARK_CREATE = "create table " + MARK_TABLE + "(" 
			+ MARK_ID 		 + " integer primary key"
			+ ");";
	
	private DatabaseHelper mDbHelper;
	public SQLiteDatabase mDb;
    private SharedPreferences mPrefs; 
    	
	private final Context mCtx;


	private static class DatabaseHelper extends SQLiteOpenHelper {
		Context mCtx;
		DatabaseHelper(Context context) {
			super(context, DATABASE_NAME, null, DATABASE_VERSION);
			this.mCtx = context;
		}
		@Override
		public void onCreate(SQLiteDatabase db) {
			Log.i(TAG, "Creating database");
			db.execSQL(TRIG_CREATE);
			db.execSQL(LOG_CREATE);
			db.execSQL(PHOTO_CREATE);
			db.execSQL(MARK_CREATE);
		}
		@Override
		public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
			if (oldVersion==11 && newVersion==12) {
				Log.w(TAG, "Upgrading from 11 to 12 - require a resync!");
				Toast.makeText(mCtx, "Database updated - please sync logs", Toast.LENGTH_LONG).show();
				ContentValues args = new ContentValues();
				args.put(TRIG_LOGGED, Condition.TRIGNOTLOGGED.code());
				db.update(TRIG_TABLE, args, null, null);
				return;
			}
			Log.w(TAG, "Upgrading database from version " + oldVersion + " to "
					+ newVersion + ", which will destroy all old data");
			db.execSQL("DROP TABLE IF EXISTS " + TRIG_TABLE);
			db.execSQL("DROP TABLE IF EXISTS " + LOG_TABLE);
			db.execSQL("DROP TABLE IF EXISTS " + PHOTO_TABLE);
			db.execSQL("DROP TABLE IF EXISTS " + MARK_TABLE);
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
		Log.i(TAG, "open: Opening database");
		mDbHelper = new DatabaseHelper(mCtx);
		mDb = mDbHelper.getWritableDatabase();
		Log.i(TAG, "open: Database opened successfully");
		return this;
	}

	public void close() {
		Log.i(TAG, "close: Closing database");
		mDbHelper.close();
		Log.i(TAG, "close: Database closed");
	}


	/**
	 * Create a new trig using the data provided. If the trig is
	 * successfully created return the new rowId, otherwise return
	 * a -1 to indicate failure.
	 * 
	 * @param id
	 * @return rowId or -1 if failed
	 */
	public long createTrig(long id, String name, String waypoint, Double lat, Double lon, Trig.Physical type, Condition condition, Condition logged, Trig.Current current, Trig.Historic historic, String fb) {
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
	public boolean updateTrigLog(long id, Condition logged) {
		ContentValues args = new ContentValues();
		args.put(TRIG_LOGGED, logged.code());
		return mDb.update(TRIG_TABLE, args, TRIG_ID + "=" + id, null) > 0;
	}

	public boolean deleteAllTrigLogs() {
		ContentValues args = new ContentValues();
		args.put(TRIG_LOGGED, Condition.TRIGNOTLOGGED.code());
		return mDb.update(TRIG_TABLE, args, null, null) > 0;
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
			strOrder = String.format("(%s-%s)*(%s-%s) + %s * (%s-%s)*(%s-%s) LIMIT %s", 
					loc.getLatitude(), 
					TRIG_LAT, 
					loc.getLatitude(), 
					TRIG_LAT, 
					Math.pow(Math.cos(Math.toRadians(loc.getLatitude())),2), 
					loc.getLongitude(), 
					TRIG_LON, 
					loc.getLongitude(), 
					TRIG_LON, 
					mPrefs.getString("listentries", "100"));
		} else {
			strOrder = TRIG_NAME + " LIMIT " +  mPrefs.getString("listentries", "100");
		}
		Log.i(TAG, strOrder);
		
		String strWhere = new Filter((Activity) mCtx).filterWhere("WHERE");
		Log.i(TAG, "fetchTrigList: Filter where clause: " + strWhere);
		
		// Debug: Log the current filter settings
		SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(mCtx);
		int filterType = prefs.getInt(Filter.FILTERTYPE, 0);
		int filterRadio = prefs.getInt(Filter.FILTERRADIO, 0);
		String filterRadioText = prefs.getString(Filter.FILTERRADIOTEXT, "");
		Log.i(TAG, "fetchTrigList: Filter settings - Type: " + filterType + ", Radio: " + filterRadio + ", RadioText: '" + filterRadioText + "'");
		
		final String qry = "SELECT "+
				TRIG_TABLE +"."+ TRIG_ID +", "+
				TRIG_TABLE +"."+ TRIG_NAME +", "+
				TRIG_TABLE +"."+ TRIG_LAT +", "+
				TRIG_TABLE +"."+ TRIG_LON +", "+
				TRIG_TABLE +"."+ TRIG_TYPE +", "+
				TRIG_TABLE +"."+ TRIG_CONDITION +", "+
				TRIG_TABLE +"."+ TRIG_LOGGED + ", "+
				LOG_TABLE  +"."+ LOG_CONDITION + " AS " + JOIN_UNSYNCED + ", " +
				MARK_TABLE  +"."+ MARK_ID + " AS " + JOIN_MARKED + " " +
				"FROM " + TRIG_TABLE + " "+
				"LEFT OUTER JOIN " + LOG_TABLE + " "+
				"ON " + TRIG_TABLE + "." + TRIG_ID + "=" + LOG_TABLE + "." + LOG_ID + " " +
				"LEFT OUTER JOIN " + MARK_TABLE + " "+
				"ON " + TRIG_TABLE + "." + TRIG_ID + "=" + MARK_TABLE + "." + MARK_ID + " " +
				strWhere + " " +
				"ORDER BY " + strOrder;
		Log.i(TAG, qry);
		return mDb.rawQuery(qry, null);

	}
	
	
	
	/**
	 * Return a Cursor suitable for the map screen
	 * 
	 * @return Cursor 
	 */
	public Cursor fetchTrigMapList (BoundingBox box) {
		String strOrder = String.format("%s limit %s", 
				TRIG_LAT, 
				mPrefs.getString("mapcount", DEFAULT_MAP_COUNT));	
   
		String strWhere = String.format("WHERE %s between %s and %s  and  %s between %s and %s",
				TRIG_LON, 
				box.getLonWest(), 
				box.getLonEast(), 
				TRIG_LAT, 
				box.getLatSouth(), 
				box.getLatNorth()); 

		strWhere += new Filter((Activity)mCtx).filterWhere("AND");
		Log.i(TAG, strWhere);
		Log.i(TAG, strOrder);
		
		final String qry = "SELECT "+
				TRIG_TABLE +"."+ TRIG_ID +", "+
				TRIG_TABLE +"."+ TRIG_NAME +", "+
				TRIG_TABLE +"."+ TRIG_LAT +", "+
				TRIG_TABLE +"."+ TRIG_LON +", "+
				TRIG_TABLE +"."+ TRIG_TYPE +", "+
				TRIG_TABLE +"."+ TRIG_CONDITION +", "+
				TRIG_TABLE +"."+ TRIG_LOGGED + ", "+
				LOG_TABLE  +"."+ LOG_CONDITION + " AS " + JOIN_UNSYNCED + ", " +
				MARK_TABLE  +"."+ MARK_ID + " AS " + JOIN_MARKED + " " +
				"FROM " + TRIG_TABLE + " "+
				"LEFT OUTER JOIN " + LOG_TABLE + " "+
				"ON " + TRIG_TABLE + "." + TRIG_ID + "=" + LOG_TABLE + "." + LOG_ID + " " +
				"LEFT OUTER JOIN " + MARK_TABLE + " "+
				"ON " + TRIG_TABLE + "." + TRIG_ID + "=" + MARK_TABLE + "." + MARK_ID + " " +
				strWhere + " " +
				"ORDER BY " + strOrder;
		Log.i(TAG, qry);
		return mDb.rawQuery(qry, null);
		
		
/*		return mDb.query(TRIG_TABLE, new String[] {
				TRIG_ID, 
				TRIG_NAME, 
				TRIG_LAT, 
				TRIG_LON, 
				TRIG_TYPE, 
				TRIG_CONDITION, 
				TRIG_LOGGED}, 
				strWhere, null, null, null, strOrder);
*/	}
	
	/**
	 * Return a Cursor suitable for the triglist screen
	 * 
	 * @return Cursor 
	 */
	public Cursor fetchTrigInfo (long id) {
		return mDb.query(TRIG_TABLE, new String[] {
				TRIG_ID, 
				TRIG_NAME, 
				TRIG_LAT, 
				TRIG_LON, 
				TRIG_TYPE, 
				TRIG_CONDITION, 
				TRIG_LOGGED, 
				TRIG_CURRENT, 
				TRIG_HISTORIC, 
				TRIG_FB}, 
				TRIG_ID + "="+id, 
				null, null, null, null);
	}
	
	/**
	 * Returns whether the trig table contains data
	 * 
	 * @return Boolean 
	 */
	public Boolean isTrigTablePopulated () {
		Log.i(TAG, "isTrigTablePopulated: Checking if trig table is populated");
		try {
			Cursor cursor = mDb.rawQuery("SELECT COUNT(*) FROM " + TRIG_TABLE, null);
			if (cursor.moveToFirst()) {
				int count = cursor.getInt(0);
				Log.i(TAG, "isTrigTablePopulated: Table has " + count + " records");
				cursor.close();
				return count > 0;
			}
			cursor.close();
			Log.i(TAG, "isTrigTablePopulated: No results found");
			return false;
		} catch (Exception e) {
			Log.e(TAG, "isTrigTablePopulated: Exception occurred", e);
			e.printStackTrace();
			return false;
		}
	}

	/**
	 * Returns number of logged pillars
	 * 
	 * @return int
	 */
	public int countLoggedPillars () {
		Log.i(TAG, "countLoggedPillars: Starting count");
		Cursor c = null;
		try {
			c = mDb.query(TRIG_TABLE, new String[] {TRIG_ID}, 
					TRIG_TYPE + "='" + Trig.Physical.PILLAR.code()+"' and " + TRIG_LOGGED + "!= '" + Condition.TRIGNOTLOGGED.code()+ "'", 
					null, null, null, null);
			int count = c.getCount();
			Log.i(TAG, "countLoggedPillars: Count = " + count);
			return count;
		} catch (Exception e) {
			Log.e(TAG, "countLoggedPillars: Exception occurred", e);
			e.printStackTrace();
			return 0;
		} finally {
			if (c != null) {
				c.close();
			}
		}
	}
	/**
	 * Returns number of logged FBMs
	 * 
	 * @return int
	 */
	public int countLoggedFbms () {
		Log.i(TAG, "countLoggedFbms");
		Cursor c = null;
		try {
			c =  mDb.query(TRIG_TABLE, new String[] {TRIG_ID}, 
					TRIG_TYPE + "='" + Trig.Physical.FBM.code()+"' and " + TRIG_LOGGED + "!= '" + Condition.TRIGNOTLOGGED.code()+ "'", 
					null, null, null, null);
			return c.getCount();
		} finally {
			if (c != null) {
				c.close();
			}
		}
	}
	/**
	 * Returns number of logged Intersecteds
	 * 
	 * @return int
	 */
	public int countLoggedIntersecteds () {
		Log.i(TAG, "countLoggedIntersecteds");
		Cursor c = null;
		try {
			c =  mDb.query(TRIG_TABLE, new String[] {TRIG_ID}, 
					TRIG_TYPE + "='" + Trig.Physical.INTERSECTED.code()+"' and " + TRIG_LOGGED + "!= '" + Condition.TRIGNOTLOGGED.code()+ "'", 
					null, null, null, null);
			return c.getCount();
		} finally {
			if (c != null) {
				c.close();
			}
		}
	}
	/**
	 * Returns number of logged Passives
	 * 
	 * @return int
	 */
	public int countLoggedPassives () {
		Log.i(TAG, "countLoggedPassives");
		Cursor c = null;
		try {
			c =  mDb.query(TRIG_TABLE, new String[] {TRIG_ID}, 
					TRIG_TYPE + " NOT IN ('" + Trig.Physical.PILLAR.code()+"','"+
								Trig.Physical.INTERSECTED.code()+"','"+Trig.Physical.FBM.code()+"') " +
					"and " + TRIG_LOGGED + "!= '" + Condition.TRIGNOTLOGGED.code()+ "'", 
					null, null, null, null);
			return c.getCount();
		} finally {
			if (c != null) {
				c.close();
			}
		}
	}
	/**
	 * Returns number of unsynced logs
	 * 
	 * @return int
	 */
	public int countUnsynced () {
		Log.i(TAG, "countUnsynced");
		Cursor c = null;
		try {
			c =  mDb.query(LOG_TABLE, new String[] {LOG_ID}, 
					null, 
					null, null, null, null);
			return c.getCount();
		} finally {
			if (c != null) {
				c.close();
			}
		}
	}
	/**
	 * Returns number of unsynced photos
	 * 
	 * @return int
	 */
	public int countPhotos () {
		Log.i(TAG, "countPhotos");
		Cursor c = null;
		try {
			c =  mDb.query(PHOTO_TABLE, new String[] {PHOTO_ID}, 
					null, 
					null, null, null, null);
			return c.getCount();
		} finally {
			if (c != null) {
				c.close();
			}
		}
	}
	
	
	/**
	 * Create a new log.  If the record is
	 * successfully created return the new rowId, otherwise return
	 * a -1 to indicate failure.
	 * 
	 * @param id
	 * @return rowId or -1 if failed
	 */
	public long createLog(long id, int year, int month, int day, int sendtime, int hour, int minutes, String gridref, String fb, 
						Condition condition, int score, String comment, int flagadmins, int flagusers) {
		Log.i(TAG, "createLog - " + id);
		
		ContentValues initialValues = new ContentValues();
		initialValues.put(LOG_ID			, id);
		initialValues.put(LOG_YEAR			, year);
		initialValues.put(LOG_MONTH			, month);
		initialValues.put(LOG_DAY			, day);
		initialValues.put(LOG_SENDTIME		, sendtime);
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
     * @param id of log to retrieve
     * @return Cursor positioned to matching log, if found
     * @throws SQLException if note could not be found/retrieved
     */
    public Cursor fetchLog(long id) throws SQLException {

        Cursor mCursor =
            mDb.query(true, LOG_TABLE, new String[] {
            		LOG_ID, 
            		LOG_YEAR, 
            		LOG_MONTH, 
            		LOG_DAY, 
            		LOG_SENDTIME, 
            		LOG_HOUR, 
            		LOG_MINUTES, 
            		LOG_GRIDREF, 
            		LOG_FB, 
            		LOG_CONDITION, 
            		LOG_COMMENT, 
            		LOG_SCORE, 
            		LOG_COMMENT, 
            		LOG_FLAGADMINS, 
            		LOG_FLAGUSERS},
                    LOG_ID + "=" + id, null,
                    null, null, null, null);
        if (mCursor != null) {
            if (! mCursor.moveToFirst()) {
            	// No log row found
            	return null;
            }
        }
        return mCursor;
    }



	/**
     * Return a Cursor positioned at the log that matches the given id
     * 
     * @param id of log to retrieve
     * @return Cursor positioned to matching log, if found
     * @throws SQLException if note could not be found/retrieved
     */
    public Cursor fetchLogs(Long trigId) throws SQLException {

    	String condition = null;
    	if (trigId != null) {
    		// only a single trig
    		condition = new String (LOG_ID + "=" + trigId);
    	}
        Cursor mCursor =
            mDb.query(true, LOG_TABLE, new String[] {
            		LOG_ID, 
            		LOG_YEAR, 
            		LOG_MONTH, 
            		LOG_DAY, 
            		LOG_SENDTIME,
            		LOG_HOUR, 
            		LOG_MINUTES, 
            		LOG_GRIDREF, 
            		LOG_FB, 
            		LOG_CONDITION, 
            		LOG_COMMENT, 
            		LOG_SCORE, 
            		LOG_COMMENT, 
            		LOG_FLAGADMINS, 
            		LOG_FLAGUSERS}
                    , condition, null, null, null, null, null);
        if (mCursor != null) {
            if (! mCursor.moveToFirst()) {
            	// No log row found
            	return null;
            }
        }
        return mCursor;
    }

    

	
	/**
	 * Create a new photo.  If the record is
	 * successfully created return the new rowId, otherwise return
	 * a -1 to indicate failure.
	 * 
	 * @param id
	 * @param name 
	 * @param descr 
	 * @param icon 
	 * @param photo 
	 * @param subject 
	 * @param ispublic 
	 * @return rowId or -1 if failed
	 */
	public long createPhoto(long trigId, String name, String descr, String icon, String photo, PhotoSubject subject, int ispublic) {
		Log.i(TAG, "createPhoto");
		
		ContentValues initialValues = new ContentValues();
		initialValues.put(PHOTO_TRIG		, trigId);
		initialValues.put(PHOTO_NAME		, name);
		initialValues.put(PHOTO_DESCR		, descr);
		initialValues.put(PHOTO_ICON		, icon);
		initialValues.put(PHOTO_PHOTO		, photo);
		initialValues.put(PHOTO_SUBJECT		, subject.code());
		initialValues.put(PHOTO_ISPUBLIC	, ispublic);
		initialValues.put(PHOTO_TUKLOGID    , 0);
		return mDb.insert(PHOTO_TABLE, null, initialValues);
	}
;

	
	/**
	 * Update a photo.  If the record is
	 * successfully updated return the new rowId, otherwise return
	 * a -1 to indicate failure.
	 * 
	 * @param id
	 * @param name 
	 * @param descr 
	 * @param icon 
	 * @param photo 
	 * @param subject 
	 * @param ispublic 
	 * @return rowId or -1 if failed
	 */
	public long updatePhoto(long photoId, long trigId, String name, String descr, String icon, String photo, PhotoSubject subject, int ispublic) {
		Log.i(TAG, "updatePhoto");
		
		ContentValues newValues = new ContentValues();
		newValues.put(PHOTO_TRIG		, trigId);
		newValues.put(PHOTO_NAME		, name);
		newValues.put(PHOTO_DESCR		, descr);
		newValues.put(PHOTO_ICON		, icon);
		newValues.put(PHOTO_PHOTO		, photo);
		newValues.put(PHOTO_SUBJECT		, subject.code());
		newValues.put(PHOTO_ISPUBLIC	, ispublic);
		newValues.put(PHOTO_TUKLOGID    , 0);
		return mDb.update(PHOTO_TABLE, newValues, PHOTO_ID + "=" + photoId, null);
	}

	
	/**
	 * Update all photos for a trig with the T:UK log number.  If the records are
	 * successfully updated return the number of rows updated, otherwise return
	 * a -1 to indicate failure.
	 * 
	 * @param id
	 * @param name 
	 * @param descr 
	 * @param icon 
	 * @param photo 
	 * @param subject 
	 * @param ispublic 
	 * @return rowId or -1 if failed
	 */
	public long updatePhotos(long trigId, long tukLogId) {
		Log.i(TAG, "updatePhotos");
		ContentValues newValues = new ContentValues();
		newValues.put(PHOTO_TUKLOGID	, tukLogId);
		
		return mDb.update(PHOTO_TABLE, newValues, PHOTO_TRIG + "= ?", new String[]{String.valueOf(trigId)});
	}

	
	
	

	/**
	 * Delete individual photo
	 * 
	 * @return true if deleted, false otherwise
	 */
	public boolean deletePhoto(long id) {
		return mDb.delete(PHOTO_TABLE, PHOTO_ID + "=" + id, null) > 0;
	}



	/**
	 * Delete all photo records for trigpoint
	 * 
	 * @return true if deleted, false otherwise
	 */
	public boolean deletePhotosForTrig(long trigId) {
		return mDb.delete(PHOTO_TABLE, PHOTO_TRIG + "=" + trigId, null) > 0;
	}


	
	/**
     * Return a Cursor positioned at the photo that matches the given id
     * 
     * @param id of photo to retrieve
     * @return Cursor positioned to matching photo, if found
     * @throws SQLException if photo could not be found/retrieved
     */
    public Cursor fetchPhoto(long photo_id) throws SQLException {

        Cursor mCursor =
            mDb.query(true, 
            		PHOTO_TABLE, 
            		new String[] {	PHOTO_ID, 
            						PHOTO_TRIG, 
            						PHOTO_ICON,
            						PHOTO_PHOTO,
            						PHOTO_NAME, 
            						PHOTO_DESCR, 
            						PHOTO_SUBJECT,
            						PHOTO_ISPUBLIC}
                    , PHOTO_ID + "=" + photo_id, null,
                    null, null, null, null);
        
        if (mCursor != null) {
            if (! mCursor.moveToFirst()) {
            	// No photo row found
            	return null;
            }
        }
        return mCursor;
    }


	/**
     * Return a Cursor positioned at the first photo for the given trigpoint
     * (or all trigpoints, if null)
     * 
     * @param id of trigpoint to retrieve
     * @return Cursor positioned to matching photo, if found
     * @throws SQLException if photo could not be found/retrieved
     */
    public Cursor fetchPhotos(Long trig_id) throws SQLException {

    	String condition = null;
    	if (trig_id != null) {
    		// only a single trig
    		condition = new String (PHOTO_TRIG + "=" + trig_id);
    	}
    	
        Cursor mCursor =
            mDb.query(true, 
            		PHOTO_TABLE, 
            		new String[] {	PHOTO_ID, 
            						PHOTO_TRIG, 
            						PHOTO_ICON,
            						PHOTO_PHOTO,
            						PHOTO_NAME, 
            						PHOTO_DESCR, 
            						PHOTO_SUBJECT,
            						PHOTO_ISPUBLIC,
            						PHOTO_TUKLOGID}
                    , condition, null,
                    null, null, null, null);
        
        if (mCursor != null) {
            if (! mCursor.moveToFirst()) {
            	// No photo row found
            	return null;
            }
        }
        return mCursor;
    }

	
    public Boolean setMarkedTrig(long trig_id, Boolean mark) {
		Log.i(TAG, "setMarkedTrig - " + trig_id + " - " + mark);
		
		if (mark) {
			ContentValues initialValues = new ContentValues();
			initialValues.put(MARK_ID			, trig_id);
			mDb.insert(MARK_TABLE, null, initialValues);
			return true;
		} else {
			mDb.delete(MARK_TABLE, MARK_ID + "=" + trig_id, null);
			return false;
		}
    }
    public Boolean isMarkedTrig(long trig_id) {
		Log.i(TAG, "isMarkedTrig - " + trig_id);
		
		Cursor c = null;
		try {
			c =  mDb.query(MARK_TABLE, new String[] {MARK_ID}, MARK_ID + "=" + trig_id, null, null, null, null);
			if (c.getCount() == 0 ) {
				return false;
			}
			return true;
		} finally {
			if (c != null) {
				c.close();
			}
		}
    }
    
    /**
     * Delete the entire database file
     */
    public void deleteDatabase() {
        Log.i(TAG, "deleteDatabase: Deleting database file");
        try {
            if (mDb != null && mDb.isOpen()) {
                mDb.close();
            }
            if (mDbHelper != null) {
                mDbHelper.close();
            }
            boolean deleted = mCtx.deleteDatabase(DATABASE_NAME);
            Log.i(TAG, "deleteDatabase: Database deletion result: " + deleted);
        } catch (Exception e) {
            Log.e(TAG, "deleteDatabase: Error deleting database", e);
        }
    }
    
}
