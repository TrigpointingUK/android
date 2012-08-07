package com.trigpointinguk.android.nearest;

import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.graphics.Typeface;
import android.location.Location;
import android.preference.PreferenceManager;
import android.support.v4.widget.SimpleCursorAdapter;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import com.trigpointinguk.android.DbHelper;
import com.trigpointinguk.android.R;
import com.trigpointinguk.android.types.Condition;
import com.trigpointinguk.android.types.LatLon;
import com.trigpointinguk.android.types.LatLon.UNITS;
import com.trigpointinguk.android.types.Trig;

public class NearestCursorAdapter extends SimpleCursorAdapter {

	private LayoutInflater mInflater;
	private int mNameIndex;
	private int mLatIndex;
	private int mLonIndex;
	private int mConditionIndex;
	private int mLoggedIndex;
	private int mTypeIndex;
	private int mUnsyncedIndex;
	private int mMarkedIndex;
	private Location mCurrentLocation;
	private LatLon.UNITS mUnits;
	//private static final String TAG = "NearestCursorAdapter";
	private double mHeading = 0;
	private double mOrientationOffset = 0;
	
	
	public NearestCursorAdapter(Context context, int layout, Cursor c,	String[] from, int[] to, Location currentLocation) {
		this(context, layout, c, from, to);
		this.mCurrentLocation = currentLocation;
	}

	public NearestCursorAdapter(Context context, int layout, Cursor c,
			String[] from, int[] to) {
		super(context, layout, c, from, to, 0);
		mInflater = LayoutInflater.from(context);
		
		// should we list km or miles?
		SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
		if (prefs.getString("units", "metric").equals("metric")) {
			mUnits = UNITS.KM;
		} else {
			mUnits = UNITS.MILES;
		}
		
		if (c != null) {
			mNameIndex = c.getColumnIndexOrThrow(DbHelper.TRIG_NAME);
			mConditionIndex = c.getColumnIndexOrThrow(DbHelper.TRIG_CONDITION);
			mTypeIndex = c.getColumnIndexOrThrow(DbHelper.TRIG_TYPE);
			mLoggedIndex = c.getColumnIndexOrThrow(DbHelper.TRIG_LOGGED);
			mLatIndex = c.getColumnIndexOrThrow(DbHelper.TRIG_LAT);	
			mLonIndex = c.getColumnIndexOrThrow(DbHelper.TRIG_LON);
			mUnsyncedIndex = c.getColumnIndexOrThrow(DbHelper.JOIN_UNSYNCED);
			mMarkedIndex = c.getColumnIndexOrThrow(DbHelper.JOIN_MARKED);
		}
	}

	@Override
	public void bindView(View view, Context context, Cursor cursor) {
		TextView  tn = (TextView)  view.findViewById(R.id.trigName);
		TextView  td = (TextView)  view.findViewById(R.id.trigDistance);
		ImageView ta = (ImageView) view.findViewById(R.id.trigArrow);
		ImageView tc = (ImageView) view.findViewById(R.id.trigCondition);
		ImageView tt = (ImageView) view.findViewById(R.id.trigType);
		ImageView tl = (ImageView) view.findViewById(R.id.trigLogged);
		
		tn.setText(cursor.getString(mNameIndex));
		tc.setImageResource(Condition.fromCode(cursor.getString(mConditionIndex)).icon());
		
		// deal with marked trigpoints
		Boolean marked = (cursor.getString(mMarkedIndex) != null);
		if (marked) {tn.setTypeface(null, Typeface.BOLD);} else {tn.setTypeface(null, Typeface.NORMAL);}
		tt.setImageResource(Trig.Physical.fromCode(cursor.getString(mTypeIndex)).icon(marked));
		
		// Use either synced condition from T:UK, or highlighted unsynced condition from logs
		Boolean unsynced = (cursor.getString(mUnsyncedIndex) != null);
		if (unsynced) {
			tl.setImageResource(Condition.fromCode(cursor.getString(mUnsyncedIndex)).icon(unsynced));
		} else {
			tl.setImageResource(Condition.fromCode(cursor.getString(mLoggedIndex)).icon(unsynced));			
		}
		
		if (mCurrentLocation != null) {
			LatLon l = new LatLon(cursor.getDouble(mLatIndex), cursor.getDouble(mLonIndex));			
			td.setText(String.format("%3.1f", l.distanceTo(mCurrentLocation, mUnits)));
			ta.setImageResource(getArrow (l.bearingFrom(mCurrentLocation)-mHeading) );  
		} else {
			td.setText("");
			ta.setImageResource(R.drawable.arrow_x);
		}
	}


	static final int 	nDivisions = 16; // number of arrows
	static final double anglePerDivision = 360.0 / nDivisions;
	static final double halfAnglePerDivision = anglePerDivision / 2.0;
	public int getArrow(double bearing) {
		int division = (int) Math.floor(  (bearing + halfAnglePerDivision + mOrientationOffset) / anglePerDivision) % nDivisions;
		if (division < 0) {division += nDivisions;}
		return R.drawable.arrow_00_n + division;
	}	
	
	@Override
	public View newView(Context context, Cursor cursor, ViewGroup parent) {
		return mInflater.inflate(R.layout.trigrow, null);
	}

	public Cursor swapCursor(Cursor c, Location loc) {
		if (c != null) {
			mNameIndex = c.getColumnIndexOrThrow(DbHelper.TRIG_NAME);
			mConditionIndex = c.getColumnIndexOrThrow(DbHelper.TRIG_CONDITION);
			mTypeIndex = c.getColumnIndexOrThrow(DbHelper.TRIG_TYPE);
			mLoggedIndex = c.getColumnIndexOrThrow(DbHelper.TRIG_LOGGED);
			mLatIndex = c.getColumnIndexOrThrow(DbHelper.TRIG_LAT);	
			mLonIndex = c.getColumnIndexOrThrow(DbHelper.TRIG_LON);
			mUnsyncedIndex = c.getColumnIndexOrThrow(DbHelper.JOIN_UNSYNCED);
			mMarkedIndex = c.getColumnIndexOrThrow(DbHelper.JOIN_MARKED);
		}
		mCurrentLocation = loc;
		return super.swapCursor(c);
	}

	public void setHeading (double heading) {
		mHeading = heading;		
	}
	public void setOrientation (int orientation) {
		switch (orientation) {
		case 0:  // normal
			mOrientationOffset = 0.0;
			break;
		case 1:  // CCW screen rotation
			mOrientationOffset = -90.0;
			break;
		case 3:  // CW screen rotation
			mOrientationOffset = +90.0;
			break;
		}
	}

}
