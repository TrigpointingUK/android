package com.trigpointinguk.android.trigdetails;

import java.util.ArrayList;

import android.app.ListActivity;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ListView;
import android.widget.TextView;

import com.trigpointinguk.android.DbHelper;
import com.trigpointinguk.android.R;
import com.trigpointinguk.android.common.DisplayBitmapActivity;
import com.trigpointinguk.android.common.StringLoader;
import com.trigpointinguk.android.types.TrigPhoto;

public class TrigDetailsAlbumTab extends ListActivity {
	private long mTrigId;
	private static final String TAG="TrigDetailsAlbumTab";
	private StringLoader 			mStrLoader;
    private ArrayList<TrigPhoto> 	mTrigPhotos;
    private TrigDetailsAlbumAdapter mTrigAlbumAdapter;
	private TextView 				mEmptyView; 

    
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.trigalbum);
        
        // get trig_id from extras
        Bundle extras = getIntent().getExtras();
		if (extras == null) {return;}
		mTrigId = extras.getLong(DbHelper.TRIG_ID);
		Log.i(TAG, "Trig_id = "+mTrigId);

        // attach the array adapter
		mTrigPhotos = new ArrayList<TrigPhoto>();
        mTrigAlbumAdapter = new TrigDetailsAlbumAdapter(TrigDetailsAlbumTab.this, R.layout.trigalbumrow, mTrigPhotos); 
		setListAdapter(mTrigAlbumAdapter);

		// find view for empty list notification
		mEmptyView = (TextView) findViewById(android.R.id.empty);

		// get list of photos
        new PopulatePhotosTask().execute(false);	    
    }
    
    
    protected void onListItemClick(ListView l, View v, int position, long id) {
        super.onListItemClick(l, v, position, id);
        String url = mTrigPhotos.get(position).getPhotoURL();
        Intent i = new Intent(this, DisplayBitmapActivity.class);
        i.putExtra("URL", url);
        Log.i(TAG, "Clicked photo at URL: " +url);
        startActivity(i);
    }
	
	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		boolean result = super.onCreateOptionsMenu(menu);
		getMenuInflater().inflate(R.menu.trigdetailsmenu, menu);
		return result;
	}


	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
	       switch (item.getItemId()) {
	        // refresh the trig logs
	        case R.id.refresh:
	        	Log.i(TAG, "refresh");
	            new PopulatePhotosTask().execute(true);
	            return true;
	        }
			return super.onOptionsItemSelected(item);
	}

    
    
    
	private class PopulatePhotosTask extends AsyncTask<Boolean, Integer, Integer> {
		protected Integer doInBackground(Boolean... arg0) {
			int count=0;
			mTrigPhotos.clear();
			
	        String url = String.format("http://www.trigpointinguk.com/trigs/down-android-trigphotos.php?t=%d", mTrigId);
	        String list = mStrLoader.getString(url, arg0[0]);
	        if (list == null || list.trim().length()==0) {
	    		Log.i(TAG, "No photos for "+mTrigId);        	
	    		return count;
	        }

	        TrigPhoto tp;

	        String[] lines = list.split("\n");
			Log.i(TAG, "Photos found : "+lines.length);
			
	        for (String line : lines) {
	        	if (!(line.trim().equals(""))) { 
	        		String[] csv = line.split("\t");
	        		try {
	        			tp = new TrigPhoto(
	        					csv[2],		//name 
	        					csv[3], 	//descr
	        					csv[1],		//photo
	        					csv[0],		//icon
	        					csv[5],		//user
	        					csv[4]);	//date
	        			mTrigPhotos.add(tp);
	        			count++;
	        		} catch (Exception e) {
	        			System.out.println(e);
	        		}
	        	}
	        }
	        return count;
		}
		protected void onPreExecute() {
			mEmptyView.setText(R.string.downloadingPhotos);
			// create string loader class
	        mStrLoader = new StringLoader(TrigDetailsAlbumTab.this);
		}
		protected void onProgressUpdate(Integer... progress) {
		}
		protected void onPostExecute(Integer arg0) {
			mEmptyView.setText(R.string.noPhotos);
	        mTrigAlbumAdapter.notifyDataSetChanged();
		}
	}

    
}