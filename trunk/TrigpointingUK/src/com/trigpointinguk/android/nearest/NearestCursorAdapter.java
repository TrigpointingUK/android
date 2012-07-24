package com.trigpointinguk.android.nearest;

import android.content.Context;
import android.database.Cursor;
import android.graphics.Typeface;
import android.location.Location;
import android.support.v4.widget.SimpleCursorAdapter;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import com.trigpointinguk.android.DbHelper;
import com.trigpointinguk.android.R;
import com.trigpointinguk.android.types.Condition;
import com.trigpointinguk.android.types.LatLon;
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
	private static final String TAG = "NearestCursorAdapter";

	
	public NearestCursorAdapter(Context context, int layout, Cursor c,	String[] from, int[] to, Location currentLocation) {
		this(context, layout, c, from, to);
		this.mCurrentLocation = currentLocation;
	}

	public NearestCursorAdapter(Context context, int layout, Cursor c,
			String[] from, int[] to) {
		super(context, layout, c, from, to, 0);
		mInflater = LayoutInflater.from(context);

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
			td.setText(String.format("%3.1f", l.distanceTo(mCurrentLocation)));
			ta.setImageResource(R.drawable.arrow_00_s + (int)((l.bearingFrom(mCurrentLocation)+191.25)/22.5));
		} else {
			td.setText("");
			ta.setImageResource(R.drawable.arrow_x);
		}
	}

	@Override
	public View newView(Context context, Cursor cursor, ViewGroup parent) {
		return mInflater.inflate(R.layout.trigrow, null);
	}

	public Cursor swapCursor(Cursor c, Location loc) {
		mCurrentLocation = loc;
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
		return super.swapCursor(c);
	}

}
