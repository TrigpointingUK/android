package uk.trigpointing.android.trigdetails;

import uk.trigpointing.android.common.BaseTabActivity;
import android.app.Dialog;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import androidx.preference.PreferenceManager;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.CompoundButton.OnCheckedChangeListener;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import uk.trigpointing.android.DbHelper;
import uk.trigpointing.android.R;
import uk.trigpointing.android.mapping.LeafletMapActivity;
import uk.trigpointing.android.types.Condition;
import uk.trigpointing.android.types.LatLon;
import uk.trigpointing.android.types.Trig;

public class TrigDetailsInfoTab extends BaseTabActivity {
	private static final String TAG="TrigDetailsInfoTab";
	private long     mTrigId;
	private DbHelper mDb;
	private Uri 	 mTUKUrl;
	private Uri 	 mNavUrl;
	private double   mLatitude;
	private double   mLongitude;
	private String   mWaypoint;
	private CheckBox mMark;
    private SharedPreferences mPrefs;

	
	private static final int RADAR = 1;
	
	public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.triginfo);

        // Get trig_id robustly
        Bundle extras = getIntent().getExtras();
        if (extras != null && extras.containsKey(DbHelper.TRIG_ID)) {
            mTrigId = extras.getLong(DbHelper.TRIG_ID);
        } else {
            mTrigId = getIntent().getLongExtra(DbHelper.TRIG_ID, -1);
        }
        if (mTrigId <= 0 && getParent() != null && getParent().getIntent() != null) {
            mTrigId = getParent().getIntent().getLongExtra(DbHelper.TRIG_ID, -1);
        }
        if (mTrigId <= 0) {
            Log.w(TAG, "No trig ID provided to TrigDetailsInfoTab");
            Toast.makeText(this, "Unable to load trig details", Toast.LENGTH_LONG).show();
            return;
        }
		Log.i("TrigInfo", "Trig_id = "+mTrigId);

		// get application preferences
		mPrefs = PreferenceManager.getDefaultSharedPreferences(this);

		mMark = findViewById(R.id.mark);
		mMark.setOnCheckedChangeListener(new OnCheckedChangeListener() {
			@Override
			public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
				try {
					mDb.setMarkedTrig(mTrigId, isChecked);
				} catch (Exception e) {
					e.printStackTrace();
				}
				// Request parent to refresh header immediately
				try {
					android.app.Activity parent = getParent();
					if (parent instanceof TrigDetailsActivity) {
						((TrigDetailsActivity) parent).refreshHeaderNow();
					}
				} catch (Exception ignored) {}
			}
		});

		// View on map link
		TextView viewOnMap = findViewById(R.id.triginfo_view_on_map);
		if (viewOnMap != null) {
			viewOnMap.setOnClickListener(new OnClickListener() {
				@Override
				public void onClick(View v) {
					try {
						// Disable auto-centre on GPS position for this session and request centering on this trig
						SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(TrigDetailsInfoTab.this);
						SharedPreferences.Editor ed = prefs.edit();
						ed.putBoolean("leaflet_disable_autolocate_once", true);
						ed.putFloat("leaflet_center_lat_once", (float) mLatitude);
						ed.putFloat("leaflet_center_lon_once", (float) mLongitude);
						ed.apply();
						Intent i = new Intent(TrigDetailsInfoTab.this, LeafletMapActivity.class);
						startActivity(i);
					} catch (Exception e) {
						Toast.makeText(TrigDetailsInfoTab.this, "Unable to open map", Toast.LENGTH_LONG).show();
					}
				}
			});
		}
		
    }


	@Override
	protected void onResume() {
		super.onResume();
		populateFields();
	}

	
	private void populateFields() {
		// get trig info from database
        mDb = new DbHelper(TrigDetailsInfoTab.this);
        mDb.open();        
        Cursor c = mDb.fetchTrigInfo(mTrigId);
        if (c == null || !c.moveToFirst()) {
            if (c != null) c.close();
            Toast.makeText(this, "Trig not found in database", Toast.LENGTH_LONG).show();
            return;
        }

		mLatitude  = c.getDouble(c.getColumnIndex(DbHelper.TRIG_LAT));
		mLongitude = c.getDouble(c.getColumnIndex(DbHelper.TRIG_LON));
		
		mTUKUrl   = Uri.parse( "https://trigpointing.uk/trigs/trig-details.php?t="+c.getLong(c.getColumnIndex(DbHelper.TRIG_ID)) );
		mNavUrl   = Uri.parse( String.format("google.navigation:ll=%3.5f,%3.5f",mLatitude, mLongitude)); 
		mWaypoint = String.format("TP%04d", c.getLong(c.getColumnIndex(DbHelper.TRIG_ID)));
		
		TextView tv;
		ImageView iv;
		
		tv = findViewById(R.id.triginfo_waypoint);
		tv.setText(mWaypoint);

		tv = findViewById(R.id.triginfo_condition);
		tv.setText(Condition.fromCode(c.getString(c.getColumnIndex(DbHelper.TRIG_CONDITION))).toString());

		LatLon ll = new LatLon(c.getDouble(c.getColumnIndex(DbHelper.TRIG_LAT)), c.getDouble(c.getColumnIndex(DbHelper.TRIG_LON)));

		tv = findViewById(R.id.triginfo_gridref);
		tv.setText(ll.getOSGB10());
		
		tv = findViewById(R.id.triginfo_wgs84);
		tv.setText(ll.getWGS());
		
		tv = findViewById(R.id.triginfo_current);
		tv.setText(Trig.Current.fromCode(c.getString(c.getColumnIndex(DbHelper.TRIG_CURRENT))).toString());

		tv = findViewById(R.id.triginfo_historic);
		tv.setText(Trig.Historic.fromCode(c.getString(c.getColumnIndex(DbHelper.TRIG_HISTORIC))).toString());

		tv = findViewById(R.id.triginfo_type);
		tv.setText(Trig.Physical.fromCode(c.getString(c.getColumnIndex(DbHelper.TRIG_TYPE))).toString());

		tv = findViewById(R.id.triginfo_fb);
		tv.setText(c.getString(c.getColumnIndex(DbHelper.TRIG_FB)));

		c.close();

		mMark.setChecked(mDb.isMarkedTrig(mTrigId));
		
	
	}
	
	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		boolean result = super.onCreateOptionsMenu(menu);
		getMenuInflater().inflate(R.menu.trigdetailsinfomenu, menu);
		return result;
	}


	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		int itemId = item.getItemId();
		
		if (itemId == R.id.directions) {
			Log.i(TAG, "directions");
			try {
				startActivity( new Intent( Intent.ACTION_VIEW, mNavUrl ) );
			} catch (ActivityNotFoundException e) {
				Toast.makeText(this, "Unable to launch navigator", Toast.LENGTH_LONG).show();
			} 
			return true;
		} else if (itemId == R.id.browser) {
			Log.i(TAG, "browser");
			try {
				startActivity( new Intent( Intent.ACTION_VIEW, mTUKUrl ) );
			} catch (ActivityNotFoundException e) {
				Toast.makeText(this, "Unable to launch browser", Toast.LENGTH_LONG).show();
			}
			return true;
		} else if (itemId == R.id.radar) {
			Log.i(TAG, "radar");
			try {
				Intent i = new Intent("com.google.android.radar.SHOW_RADAR") ;
				i.putExtra("latitude",  (float) mLatitude);
				i.putExtra("longitude", (float) mLongitude);
				i.putExtra("name", mWaypoint);
				startActivity(i);
			} catch (ActivityNotFoundException e) {
				showDialog(RADAR);
			} 
			return true;	     
		} else if (itemId == R.id.map) {
			Log.i(TAG, "map");
			// remove existing map bounding box preferences
			Editor editor = mPrefs.edit();
			editor.remove("north");
			editor.remove("south");
			editor.remove("east");
			editor.remove("west");
			editor.putInt("zoomLevel", 12);
			editor.putInt("latitude", (int)(mLatitude * 1E6));
			editor.putInt("longitude", (int)(mLongitude * 1E6));
			editor.apply();
			Intent i = new Intent(TrigDetailsInfoTab.this, LeafletMapActivity.class);
			startActivityForResult(i, R.id.map);
			return true;	     
		}
		return super.onOptionsItemSelected(item);
	}
	
	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		super.onActivityResult(requestCode, resultCode, data);
		populateFields();
		
	}

	protected Dialog onCreateDialog(int id) {
	    Dialog dialog;
	    switch(id) {
	    case RADAR:
			dialog = new Dialog(this);
			dialog.setContentView(R.layout.radardialog);
			dialog.setTitle(R.string.radartitle);
			Button yes = dialog.findViewById(R.id.yes);
			yes.setOnClickListener(new OnClickListener() {
				@Override
				public void onClick(View v) {
					dismissDialog(RADAR);
					Intent intent = new Intent(Intent.ACTION_VIEW);
					intent.setData(Uri.parse("market://details?id=com.eclipsim.gpsstatus2"));
					startActivity(intent);
				}
			});
			Button no = dialog.findViewById(R.id.no);
			no.setOnClickListener(new OnClickListener() {
				@Override
				public void onClick(View v) {
					dismissDialog(RADAR);
				}
			});
	        break;
	    default:
	        dialog = null;
	    }
	    return dialog;
	}
	
	
	@Override
	protected void onDestroy() {
		if (mDb != null) {
			mDb.close();
		}
		super.onDestroy();
	}


	
}