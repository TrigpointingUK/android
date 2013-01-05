package com.trigpointinguk.android.mapping;

import java.util.ArrayList;

import org.osmdroid.DefaultResourceProxyImpl;
import org.osmdroid.api.IGeoPoint;
import org.osmdroid.events.MapListener;
import org.osmdroid.events.ScrollEvent;
import org.osmdroid.events.ZoomEvent;
import org.osmdroid.tileprovider.tilesource.TileSourceFactory;
import org.osmdroid.tileprovider.tilesource.bing.BingMapTileSource;
import org.osmdroid.tileprovider.util.CloudmadeUtil;
import org.osmdroid.util.BoundingBoxE6;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapController;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.ItemizedIconOverlay;
import org.osmdroid.views.overlay.ItemizedIconOverlay.OnItemGestureListener;
import org.osmdroid.views.overlay.MyLocationOverlay;
import org.osmdroid.views.overlay.OverlayItem;
import org.osmdroid.views.overlay.OverlayItem.HotspotPlace;
import org.osmdroid.views.overlay.ScaleBarOverlay;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.database.Cursor;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.widget.Toast;

import com.trigpointinguk.android.DbHelper;
import com.trigpointinguk.android.R;
import com.trigpointinguk.android.filter.FilterActivity;
import com.trigpointinguk.android.mapping.MapIcon.colourScheme;
import com.trigpointinguk.android.trigdetails.TrigDetailsActivity;
import com.trigpointinguk.android.types.Condition;
import com.trigpointinguk.android.types.Trig;





public class MapActivity extends Activity implements MapListener {
	private static final int DEFAULT_LON = -1450510;
	private static final int DEFAULT_LAT = 50931280;
	public static final String TAG = "MapActivity";
	
	public enum TileSource 		{NONE, MAPNIK, CYCLEMAP, MAPQUEST, CLOUDMADE, BING_AERIAL, BING_ROAD, BING_AERIAL_LABELS, BING_OSGB};	

	private MapView            mMapView;
	private MapController      mMapController;
	private MyLocationOverlay  mMyLocationOverlay;
	private ScaleBarOverlay    mScaleBarOverlay;  

	private SharedPreferences  mPrefs;
	private DbHelper    	   mDb;
	
	private ItemizedIconOverlay<OverlayItem> mTrigOverlay;
	private BoundingBoxE6      		mBigBB         = new BoundingBoxE6(0, 0, 0, 0);
	private MapIcon.colourScheme    mIconColouring = colourScheme.NONE;
	private TileSource         		mTileSource    = TileSource.NONE;
	private boolean            		mTooManyTrigs;
	private com.trigpointinguk.android.mapping.ResourceProxyImpl mResourceProxy;
	
	/** Called when the activity is first created. */
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.mapview);
		mPrefs = PreferenceManager.getDefaultSharedPreferences(this);
		mDb = new DbHelper(this);
		mDb.open();

		// basic map setup
		mResourceProxy = new ResourceProxyImpl(getApplicationContext());
		mMapView = (MapView) findViewById(R.id.mapview);  
		mMapView.setBuiltInZoomControls(true);
		mMapView.setMultiTouchControls(true);
		mMapController = mMapView.getController();


		// setup current location overlay
		mMyLocationOverlay = new MyLocationOverlay(this, mMapView, mResourceProxy);      
		mMapView.getOverlays().add(mMyLocationOverlay);

		// add scalebar
		mScaleBarOverlay = new ScaleBarOverlay(this);                          
		mMapView.getOverlays().add(mScaleBarOverlay);

		// setup trigpoint overlay
		mTrigOverlay = new ItemizedIconOverlay<OverlayItem>(
				new ArrayList<OverlayItem>(),
				this.getResources().getDrawable(MapIcon.defaultIcon),
				new OnItemGestureListener<OverlayItem>() {
					@Override
					public boolean onItemSingleTapUp(final int index, final OverlayItem item) {
						// When icon is tapped, jump to TrigDetails activity
						Intent i = new Intent(MapActivity.this, TrigDetailsActivity.class);
						i.putExtra(DbHelper.TRIG_ID, (long)Long.parseLong(item.mTitle));
						startActivityForResult(i, index);
						return true; // We 'handled' this event.
					}

					@Override
					public boolean onItemLongPress(final int index,
							final OverlayItem item) {
						Toast.makeText(MapActivity.this, item.mDescription, Toast.LENGTH_SHORT).show();
						return false;
					}
				}, new DefaultResourceProxyImpl(getApplicationContext()));
		mMapView.getOverlays().add(mTrigOverlay);
		mMapView.setMapListener(this);
		
		loadViewFromPrefs();
	}


	private void setTileProvider(TileSource tileSource) {
		mTileSource = tileSource;
		switch (tileSource) {
		case MAPNIK:
			mMapView.setTileSource(TileSourceFactory.MAPNIK);
			break;
		case CYCLEMAP:
			mMapView.setTileSource(TileSourceFactory.CYCLEMAP);
			break;
		case MAPQUEST:
			mMapView.setTileSource(TileSourceFactory.MAPQUESTOSM);
			break;
		case CLOUDMADE:
            CloudmadeUtil.retrieveCloudmadeKey(getApplicationContext());
			mMapView.setTileSource(TileSourceFactory.CLOUDMADESTANDARDTILES);
			break;
		case BING_AERIAL:
            BingMapTileSource.retrieveBingKey(getApplicationContext());
            BingMapTileSource bingTileSource = new BingMapTileSource(null);
            bingTileSource.setStyle(BingMapTileSource.IMAGERYSET_AERIAL);
			mMapView.setTileSource(bingTileSource);
			break;
		case BING_AERIAL_LABELS:
            BingMapTileSource.retrieveBingKey(getApplicationContext());
            BingMapTileSource bingTileSource2 = new BingMapTileSource(null);
            bingTileSource2.setStyle(BingMapTileSource.IMAGERYSET_AERIALWITHLABELS);
			mMapView.setTileSource(bingTileSource2);
			break;
		case BING_ROAD:
            BingMapTileSource.retrieveBingKey(getApplicationContext());
            BingMapTileSource bingTileSource3 = new BingMapTileSource(null);
            bingTileSource3.setStyle(BingMapTileSource.IMAGERYSET_ROAD);
			mMapView.setTileSource(bingTileSource3);
			break;
		case BING_OSGB:
            BingMapTileSource.retrieveBingKey(getApplicationContext());
            BingMapTileSource bingTileSource4 = new BingMapTileSource(null);
            bingTileSource4.setStyle(BingMapTileSource.IMAGERYSET_OSGB);
			mMapView.setTileSource(bingTileSource4);
			break;
		case NONE:
			mMapView.setTileSource(TileSourceFactory.MAPNIK);
			break;
		}
		// save choice to prefs
		Editor editor = mPrefs.edit();
		editor.putString("tileSource", tileSource.toString());
		editor.commit();	
	}


	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		boolean result = super.onCreateOptionsMenu(menu);
		MenuInflater inflater = getMenuInflater();
		inflater.inflate(R.menu.mapmenu, menu);
		
		// initialise menu ticks
		switch (mIconColouring) {
		case BYCONDITION:
			menu.findItem(R.id.byCondition).setChecked(true);
			break;
		case BYLOGGED:
			menu.findItem(R.id.byLogged).setChecked(true);
			break;
		case NONE:
			menu.findItem(R.id.none).setChecked(true);
			break;
		}
		// initialise tile provider ticks
		switch (mTileSource) {
		case MAPNIK:
			menu.findItem(R.id.mapnik).setChecked(true);
			break;
		case MAPQUEST:
			menu.findItem(R.id.mapquest).setChecked(true);
			break;
		case CLOUDMADE:
			menu.findItem(R.id.cloudmade).setChecked(true);
			break;
		case CYCLEMAP:
			menu.findItem(R.id.cyclemap).setChecked(true);
			break;
		case BING_AERIAL:
			menu.findItem(R.id.bingaerial).setChecked(true);
			break;
		case BING_AERIAL_LABELS:
			menu.findItem(R.id.bingaeriallabels).setChecked(true);
			break;
		case BING_ROAD:
			menu.findItem(R.id.bingroad).setChecked(true);
			break;
		case BING_OSGB:
			menu.findItem(R.id.bingosgb).setChecked(true);
			break;
		case NONE:
			menu.findItem(R.id.mapnik).setChecked(true);
			break;
		}
		return result;
	}    

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		item.setChecked(true);
		switch (item.getItemId()) {
		// Tile provider options
		case R.id.cloudmade:
			setTileProvider(TileSource.CLOUDMADE);
			break;
		case R.id.cyclemap:
			setTileProvider(TileSource.CYCLEMAP);
			break;
		case R.id.mapnik:
			setTileProvider(TileSource.MAPNIK);
			break;
		case R.id.mapquest:
			setTileProvider(TileSource.MAPQUEST);
			break;
		case R.id.bingaerial:
			setTileProvider(TileSource.BING_AERIAL);
			break;
		case R.id.bingaeriallabels:
			setTileProvider(TileSource.BING_AERIAL_LABELS);
			break;
		case R.id.bingroad:
			setTileProvider(TileSource.BING_ROAD);
			break;
		case R.id.bingosgb:
			setTileProvider(TileSource.BING_OSGB);
			break;

		// Icon colouring options
		case R.id.byCondition:
			mIconColouring = colourScheme.BYCONDITION;
			refreshMap();
			break;
		case R.id.none:
			mIconColouring = colourScheme.NONE;
			refreshMap();
			break;
		case R.id.byLogged:
			mIconColouring = colourScheme.BYLOGGED;
			refreshMap();
			break;
			
		// Other
		case R.id.downloadmaps:
			Intent i = new Intent(MapActivity.this, DownloadMapsActivity.class);
			startActivity(i);
			return true;
		case R.id.location:
			mMyLocationOverlay.enableFollowLocation();
            return true;
		case R.id.compass:
			if (mMyLocationOverlay.isCompassEnabled()) {
				mMyLocationOverlay.disableCompass();
			} else {
				mMyLocationOverlay.enableCompass();
			}
			return true;
		case R.id.filter:
            i = new Intent(MapActivity.this, FilterActivity.class);
            startActivityForResult(i, R.id.filter);
            return true;
		}

		return super.onOptionsItemSelected(item);
	}


	@Override
	protected void onPause() {
		mMyLocationOverlay.disableMyLocation();
		
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
		editor.putString("iconColouring", mIconColouring.toString());
		editor.putBoolean("compass", mMyLocationOverlay.isCompassEnabled());
		editor.commit();
		super.onPause();
	}

	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		Log.i(TAG, "onActivityResult");
		refreshMap();
	}


	@Override
	protected void onResume() {
		super.onResume();
		loadViewFromPrefs();
		mMyLocationOverlay.enableMyLocation();
	}


	@Override
	protected void onDestroy() {
		mDb.close();
		super.onDestroy();
	}

	private void refreshMap() {
		mTooManyTrigs = true; // fudge to ensure redraw
		populateTrigOverlay(mMapView.getBoundingBox());
	}

	private void loadViewFromPrefs() {
		try {
			// set tilesource from prefs
			setTileProvider(TileSource.valueOf(mPrefs.getString("tileSource", TileSource.MAPNIK.toString())));
			// set colouring from prefs
			mIconColouring = colourScheme.valueOf(mPrefs.getString("iconColouring", colourScheme.NONE.toString()));
		} catch (IllegalArgumentException e) {
			// invalid preference
			setTileProvider(TileSource.MAPNIK);
			mIconColouring = colourScheme.NONE;
		}
		try {
			// set view from prefs
			mMapController.setZoom(mPrefs.getInt("zoomLevel", 12));
			mMapController.setCenter(new GeoPoint(mPrefs.getInt("latitude", DEFAULT_LAT)
												, mPrefs.getInt("longitude", DEFAULT_LON)));
			BoundingBoxE6 bb = new BoundingBoxE6( mPrefs.getInt("north", mPrefs.getInt("latitude",  DEFAULT_LAT)  + 80000) 
												, mPrefs.getInt("east",  mPrefs.getInt("longitude", DEFAULT_LON)  + 80000)
												, mPrefs.getInt("south", mPrefs.getInt("latitude",  DEFAULT_LAT)  - 80000)
												, mPrefs.getInt("west",  mPrefs.getInt("longitude", DEFAULT_LON)  - 80000) );
			populateTrigOverlay(bb);
		} catch (ClassCastException e) {
			// bad coordinates, do nothing
		}	
		if (mPrefs.getBoolean("compass", false)) {
			mMyLocationOverlay.enableCompass();
		} else {
			mMyLocationOverlay.disableCompass();
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
		
		MapIcon mapIcon = new MapIcon(this);

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
	
			if (c.getCount() < Integer.parseInt(mPrefs.getString("mapcount", DbHelper.DEFAULT_MAP_COUNT))) {
				mTooManyTrigs = false;
				c.moveToFirst();
				while (c.isAfterLast() == false) {
					GeoPoint point = new GeoPoint((int)(c.getDouble(c.getColumnIndex(DbHelper.TRIG_LAT))*1000000), (int)(c.getDouble(c.getColumnIndex(DbHelper.TRIG_LON))*1000000));
					OverlayItem oi = new OverlayItem(c.getString(c.getColumnIndex(DbHelper.TRIG_ID)), 
													 c.getString(c.getColumnIndex(DbHelper.TRIG_NAME)), point);
					
					// forward nulls
					String strUnsynced = c.getString(c.getColumnIndex(DbHelper.JOIN_UNSYNCED));
					Condition unsynced = null;
					if (strUnsynced != null) {
						unsynced = Condition.fromCode(strUnsynced);
					}
					
					oi.setMarker(mapIcon.getDrawable(	mIconColouring, 
														Trig.Physical.fromCode(c.getString(c.getColumnIndex(DbHelper.TRIG_TYPE))),
														Condition.fromCode(c.getString(c.getColumnIndex(DbHelper.TRIG_CONDITION))),
														Condition.fromCode(c.getString(c.getColumnIndex(DbHelper.TRIG_LOGGED))),
														unsynced,
														c.getString(c.getColumnIndex(DbHelper.JOIN_MARKED)) != null
													) );
					oi.setMarkerHotspot(HotspotPlace.CENTER);
					newitems.add(oi);
					c.moveToNext();
				}
				mTrigOverlay.addItems(newitems);
			} else {
				mTooManyTrigs = true;
				Log.i(TAG, "Too Many Trigs");
			}
			c.close();
		}
		mMapView.invalidate();
	}
	
}