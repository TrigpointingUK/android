package com.trigpointinguk.android;

import android.app.Activity;
import android.database.Cursor;
import android.os.Bundle;
import android.util.Log;
import android.widget.ImageView;
import android.widget.TextView;

import com.trigpointinguk.android.common.LatLon;

public class TrigDetailsInfoTab extends Activity {
	private long mTrigId;
	private DbHelper mDb;

	public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.triginfo);

        // get trig_id from extras
        Bundle extras = getIntent().getExtras();
		if (extras == null) {return;}
		mTrigId = extras.getLong(DbHelper.TRIG_ID);
		Log.i("TrigInfo", "Trig_id = "+mTrigId);

		// get trig info from database
		mDb = new DbHelper(TrigDetailsInfoTab.this);
		mDb.open();		
		Cursor c = mDb.fetchTrigInfo(mTrigId);
		c.moveToFirst();
		
		TextView tv;
		ImageView iv;
		
		tv = (TextView)  findViewById(R.id.triginfo_name);
		tv.setText(c.getString(c.getColumnIndex(DbHelper.TRIG_NAME)));
		
		tv = (TextView)  findViewById(R.id.triginfo_waypoint);
		tv.setText(String.format("TP%04d", c.getLong(c.getColumnIndex(DbHelper.TRIG_ID))));
		
		iv = (ImageView) findViewById(R.id.triginfo_condition_icon);
		iv.setImageResource(Condition.fromCode(c.getString(c.getColumnIndex(DbHelper.TRIG_CONDITION))).icon());

		tv = (TextView) findViewById(R.id.triginfo_condition);
		tv.setText(Condition.fromCode(c.getString(c.getColumnIndex(DbHelper.TRIG_CONDITION))).descr());

		LatLon ll = new LatLon(c.getDouble(c.getColumnIndex(DbHelper.TRIG_LAT)), c.getDouble(c.getColumnIndex(DbHelper.TRIG_LON)));

		tv = (TextView)  findViewById(R.id.triginfo_gridref);
		tv.setText(ll.getOSGB10());
		
		tv = (TextView)  findViewById(R.id.triginfo_wgs84);
		tv.setText(ll.getWGS());
		
		tv = (TextView) findViewById(R.id.triginfo_current);
		tv.setText(Trig.Current.fromCode(c.getString(c.getColumnIndex(DbHelper.TRIG_CURRENT))).descr());

		tv = (TextView) findViewById(R.id.triginfo_historic);
		tv.setText(Trig.Historic.fromCode(c.getString(c.getColumnIndex(DbHelper.TRIG_HISTORIC))).descr());

		tv = (TextView) findViewById(R.id.triginfo_type);
		tv.setText(Trig.Physical.fromCode(c.getString(c.getColumnIndex(DbHelper.TRIG_TYPE))).descr());

		tv = (TextView) findViewById(R.id.triginfo_fb);
		tv.setText(c.getString(c.getColumnIndex(DbHelper.TRIG_FB)));

		c.close();
		mDb.close();
    }
}