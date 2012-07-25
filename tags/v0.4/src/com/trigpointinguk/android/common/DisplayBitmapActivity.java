package com.trigpointinguk.android.common;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;

import com.sonyericsson.zoom.DynamicZoomControl;
import com.sonyericsson.zoom.ImageZoomView;
import com.sonyericsson.zoom.LongPressZoomListener;
import com.trigpointinguk.android.R;

public class DisplayBitmapActivity extends Activity {
	private static final String TAG="DisplayBitmapActivity";

    private static final int MENU_ID_RESET = 0;
    private ImageZoomView 			mZoomView;
    private DynamicZoomControl 		mZoomControl;
    private Bitmap 					mBitmap;
    private LongPressZoomListener 	mZoomListener;
    private String					mUrl;
    private BitmapLoader 			mBitmapLoader; 
    
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.displaybitmap);

        // get URL from extras
        Bundle extras = getIntent().getExtras();
		if (extras == null) {return;}
		mUrl = extras.getString("URL");
		Log.i(TAG, "Loading URL: "+mUrl);

        // setup zoom control
        mZoomControl = new DynamicZoomControl();
        mZoomListener = new LongPressZoomListener(getApplicationContext());
        mZoomListener.setZoomControl(mZoomControl);
        
        mZoomView = (ImageZoomView)findViewById(R.id.zoomview);
        mZoomView.setZoomState(mZoomControl.getZoomState());
        mZoomView.setOnTouchListener(mZoomListener);
        
        mZoomView.setImage(BitmapFactory.decodeResource(getResources(), R.drawable.imageloading));
        mZoomControl.setAspectQuotient(mZoomView.getAspectQuotient());
        resetZoomState();

		mBitmapLoader = new BitmapLoader(this);
		new DisplayBitmapTask().execute();
    
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        if (mBitmap != null) {mBitmap.recycle();}
        mZoomView.setOnTouchListener(null);
        mZoomControl.getZoomState().deleteObservers();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        menu.add(Menu.NONE, MENU_ID_RESET, 2, R.string.reset);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case MENU_ID_RESET:
                resetZoomState();
                break;
        }
        return super.onOptionsItemSelected(item);
    }

    /**
     * Reset zoom state and notify observers
     */
    private void resetZoomState() {
        mZoomControl.getZoomState().setPanX(0.5f);
        mZoomControl.getZoomState().setPanY(0.5f);
        mZoomControl.getZoomState().setZoom(1f);
        mZoomControl.getZoomState().notifyObservers();
    }


    
    
    
	private class DisplayBitmapTask extends AsyncTask<Void, Integer, Integer> {
		protected Integer doInBackground(Void... arg0) {
	        // download photo, or obtain from cache
	        mBitmap = mBitmapLoader.getBitmap(mUrl, false);
			return null;
		}
		protected void onPreExecute() {
		}
		protected void onProgressUpdate(Integer... progress) {
		}
		protected void onPostExecute(Integer arg0) {
	        mZoomView.setImage(mBitmap);
	        resetZoomState();
		}
	}    
    
    
}
