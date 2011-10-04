package com.trigpointinguk;

import java.util.ArrayList;

import org.osmdroid.DefaultResourceProxyImpl;
import org.osmdroid.api.IGeoPoint;
import org.osmdroid.events.MapListener;
import org.osmdroid.events.ScrollEvent;
import org.osmdroid.events.ZoomEvent;
import org.osmdroid.tileprovider.tilesource.TileSourceFactory;
import org.osmdroid.util.BoundingBoxE6;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapController;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.ItemizedIconOverlay;
import org.osmdroid.views.overlay.OverlayItem;
import org.osmdroid.views.overlay.ScaleBarOverlay;
import org.osmdroid.views.overlay.SimpleLocationOverlay;
import org.osmdroid.views.overlay.ItemizedIconOverlay.OnItemGestureListener;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.database.Cursor;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.Toast;





public class TrigMap extends Activity implements MapListener {

	private MapView mMapView;
	private MapController mMapController;
	private SimpleLocationOverlay mMyLocationOverlay;
	private ScaleBarOverlay mScaleBarOverlay;  
	private SharedPreferences mPrefs;
	
	public static final int DOWNLOAD_ID = Menu.FIRST;
	public static final String TAG = "MapTest";
	private Drawable mTrigIcon;
	private TrigDbHelper mDb;
	private ItemizedIconOverlay<OverlayItem> mTrigOverlay;
	private BoundingBoxE6 mBigBB = new BoundingBoxE6(0, 0, 0, 0);
	private boolean mTooManyTrigs;

	
	
	/** Called when the activity is first created. */
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.mapview);
		mPrefs = PreferenceManager.getDefaultSharedPreferences(this);
		mDb = new TrigDbHelper(this);
		mDb.open();

		mMapView = (MapView) findViewById(R.id.mapview);  
		switch (Integer.parseInt(mPrefs.getString("mapChoice", "1"))) {
		case 1:
			mMapView.setTileSource(TileSourceFactory.MAPNIK);
			break;
		case 2:
			mMapView.setTileSource(TileSourceFactory.OSMARENDER);
			break;
		case 3:
			mMapView.setTileSource(TileSourceFactory.CYCLEMAP);
			break;
		case 4:
			mMapView.setTileSource(TileSourceFactory.MAPQUESTOSM);
			break;
		}
		mMapView.setBuiltInZoomControls(true);
		mMapView.setMultiTouchControls(true);
		mMapController = mMapView.getController();

		mMyLocationOverlay = new SimpleLocationOverlay(this);      
		mMyLocationOverlay.setLocation(new GeoPoint(50931280,-1450510));
		mMapView.getOverlays().add(mMyLocationOverlay);

		mScaleBarOverlay = new ScaleBarOverlay(this);                          
		mMapView.getOverlays().add(mScaleBarOverlay);


		mTrigIcon = this.getResources().getDrawable(R.drawable.icon);

		mTrigOverlay = new ItemizedIconOverlay<OverlayItem>(
				new ArrayList<OverlayItem>(),
				mTrigIcon,
				new OnItemGestureListener<OverlayItem>() {
					@Override
					public boolean onItemSingleTapUp(final int index, final OverlayItem item) {
						Intent i = new Intent(TrigMap.this, TrigDetails.class);
						i.putExtra(TrigDbHelper.TRIG_ID, (long)Long.parseLong(item.mTitle));
						startActivity(i);
						return true; // We 'handled' this event.
					}

					@Override
					public boolean onItemLongPress(final int index,
							final OverlayItem item) {
						Toast.makeText(TrigMap.this, "Item '" + item.mTitle + item.mDescription + "' (index=" + index + ") got long pressed", Toast.LENGTH_LONG).show();
						return false;
					}
				}, new DefaultResourceProxyImpl(getApplicationContext()));
		mMapView.getOverlays().add(mTrigOverlay);
		mMapView.setMapListener(this);

		loadPrefs();
	}




	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		boolean result = super.onCreateOptionsMenu(menu);
		menu.add(0, DOWNLOAD_ID, 0, R.string.downMaps);
		return result;
	}    

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		Intent i;
		switch (item.getItemId()) {
		case DOWNLOAD_ID:
			i = new Intent(TrigMap.this, DownloadMaps.class);
			startActivity(i);
			return true;
		}
		return super.onOptionsItemSelected(item);
	}


	@Override
	protected void onPause() {
		Editor editor = mPrefs.edit();

		IGeoPoint mapCenter = mMapView.getMapCenter();
		editor.putInt("latitude", mapCenter.getLatitudeE6());
		editor.putInt("longitude", mapCenter.getLongitudeE6());
		editor.putInt("zoomLevel", mMapView.getZoomLevel());
		BoundingBoxE6 bb = mMapView.getBoundingBox();
		editor.putInt("north", bb.getLatNorthE6());
		editor.putInt("south", bb.getLatSouthE6());
		editor.putInt("east", bb.getLonEastE6());
		editor.putInt("west", bb.getLonWestE6());
		editor.commit();
		super.onPause();
	}


	@Override
	protected void onResume() {
		loadPrefs();
		super.onResume();
	}


	@Override
	protected void onDestroy() {
		mDb.close();
		super.onDestroy();
	}


	private void loadPrefs() {
		try {
			mMapController.setZoom(mPrefs.getInt("zoomLevel", 12));
			mMapController.setCenter(new GeoPoint(mPrefs.getInt("latitude", 50931280), mPrefs.getInt("longitude", -1450510)));
			BoundingBoxE6 bb = new BoundingBoxE6(mPrefs.getInt("north", 50997336), mPrefs.getInt("east", -1369857), mPrefs.getInt("south", 50875311), mPrefs.getInt("west", -1534652));
			populateTrigOverlay(bb);
		} catch (ClassCastException e) {
			// bad coordinates, do nothing
		}		
	}




	@Override
	public boolean onScroll(ScrollEvent event) {
		populateTrigOverlay(mMapView.getBoundingBox());
		return false;
	}


	@Override
	public boolean onZoom(ZoomEvent event) {
		populateTrigOverlay(mMapView.getBoundingBox());
		return false;
	}

	private void populateTrigOverlay(BoundingBoxE6 bb) {
		ArrayList<OverlayItem> newitems = new ArrayList<OverlayItem>();

		// Only repopulate if the new bounding box extends beyond region previously queried
		if (bb.getLatNorthE6() < mBigBB.getLatNorthE6() 
				&& bb.getLatSouthE6() > mBigBB.getLatSouthE6() 
				&& bb.getLonEastE6() < mBigBB.getLonEastE6() 
				&& bb.getLonWestE6() > mBigBB.getLonWestE6()
				&& mTooManyTrigs == false) {
			return;
		}
		// Cope with empty regions (when map initially loading)
		if (bb.getLatitudeSpanE6() == 0 || bb.getLongitudeSpanE6() == 0) {
			return;
		}
		
		Log.w(TAG, "Reloading trigs");
		mTrigOverlay.removeAllItems();
		
		// query a larger region than currently shown, to reduce number of reloads
		mBigBB = bb.increaseByScale(2);
		

		Cursor c = mDb.fetchTrigMapList(mBigBB);
		if (c != null) {
			Log.i(TAG, "Found " + c.getCount() + " trigs");
	
			if (c.getCount() < Integer.parseInt(mPrefs.getString("mapcount", "80"))) {
				mTooManyTrigs = false;
				c.moveToFirst();
				while (c.isAfterLast() == false) {
					GeoPoint point = new GeoPoint((int)(c.getDouble(2)*1000000), (int)(c.getDouble(3)*1000000));
					newitems.add(new OverlayItem(c.getString(0), c.getString(1), point));
					c.moveToNext();
				}
				mTrigOverlay.addItems(newitems);
			} else {
				mTooManyTrigs = true;
				Log.i(TAG, "Too Many Trigs");
			}
			c.close();
		}
	}
}