package com.trigpointinguk.android;

import android.content.Context;
import android.database.Cursor;
import android.location.Location;
import android.support.v4.widget.SimpleCursorAdapter;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import com.trigpointinguk.android.common.LatLon;

public class TrigListCursorAdapter extends SimpleCursorAdapter {

	private LayoutInflater mInflater;
	private int mNameIndex;
	private int mLatIndex;
	private int mLonIndex;
	private int mConditionIndex;
	private int mLoggedIndex;
	private Location mCurrentLocation;
	
	public TrigListCursorAdapter(Context context, int layout, Cursor c,	String[] from, int[] to, Location currentLocation) {
		this(context, layout, c, from, to);
		this.mCurrentLocation = currentLocation;
	}

	public TrigListCursorAdapter(Context context, int layout, Cursor c,
			String[] from, int[] to) {
		super(context, layout, c, from, to, 0);
		mInflater = LayoutInflater.from(context);

		if (c != null) {
			mNameIndex = c.getColumnIndex(DbHelper.TRIG_NAME);
			mConditionIndex = c.getColumnIndex(DbHelper.TRIG_CONDITION);
			mLoggedIndex = c.getColumnIndex(DbHelper.TRIG_LOGGED);
			mLatIndex = c.getColumnIndex(DbHelper.TRIG_LAT);	
			mLonIndex = c.getColumnIndex(DbHelper.TRIG_LON);
		}
	}

	@Override
	public void bindView(View view, Context context, Cursor cursor) {
		TextView  tn = (TextView)  view.findViewById(R.id.trigName);
		TextView  td = (TextView)  view.findViewById(R.id.trigDistance);
		ImageView ta = (ImageView) view.findViewById(R.id.trigArrow);
		ImageView tc = (ImageView) view.findViewById(R.id.trigCondition);
		ImageView tl = (ImageView) view.findViewById(R.id.trigLogged);
		
		tn.setText(cursor.getString(mNameIndex));
		tc.setImageResource(R.drawable.c0_unknown + cursor.getInt(mConditionIndex));
		tl.setImageResource(R.drawable.c0_unknown + cursor.getInt(mLoggedIndex));
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
			mNameIndex = c.getColumnIndex(DbHelper.TRIG_NAME);
			mConditionIndex = c.getColumnIndex(DbHelper.TRIG_CONDITION);
			mLoggedIndex = c.getColumnIndex(DbHelper.TRIG_LOGGED);
			mLatIndex = c.getColumnIndex(DbHelper.TRIG_LAT);	
			mLonIndex = c.getColumnIndex(DbHelper.TRIG_LON);
		}
		return super.swapCursor(c);
	}

}
