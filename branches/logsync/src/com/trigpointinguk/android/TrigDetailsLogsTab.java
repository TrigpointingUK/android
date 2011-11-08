package com.trigpointinguk.android;

import java.util.ArrayList;

import android.app.ListActivity;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;

import com.trigpointinguk.android.common.StringLoader;

public class TrigDetailsLogsTab extends ListActivity {
	private static final String TAG="TrigDetailsLogsTab";
	
	private long mTrigId;
	private StringLoader mStrLoader;
    private ArrayList<TrigLog> mTrigLogs;
    private TrigDetailsLogsAdapter mTrigLogsAdapter;

	
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        // get trig_id from extras
        Bundle extras = getIntent().getExtras();
		if (extras == null) {return;}
		mTrigId = extras.getLong(DbHelper.TRIG_ID);
		Log.i(TAG, "Trig_id = "+mTrigId);

        // attach the array adapter
		mTrigLogs = new ArrayList<TrigLog>();
        mTrigLogsAdapter = new TrigDetailsLogsAdapter(TrigDetailsLogsTab.this, R.layout.triglogrow, mTrigLogs); 
		setListAdapter(mTrigLogsAdapter);

		// get list of photos
        new PopulatePhotosTask().execute();
    }
    
    
    
    
    
    
    
	private class PopulatePhotosTask extends AsyncTask<Void, Integer, Integer> {
		protected Integer doInBackground(Void... arg0) {
			int count=0;
			mTrigLogs.clear();
			
	        String url = String.format("http://www.trigpointinguk.com/trigs/down-android-triglogs.php?t=%d", mTrigId);
	        String list = mStrLoader.getString(url, false);
	        if (list == null || list.trim().length()==0) {
	    		Log.i(TAG, "No logs for "+mTrigId);        	
	    		return count;
	        }
			//Log.d(TAG,list);        	

	        TrigLog tl;

	        String[] lines = list.split("\n");
			//System.out.println(java.util.Arrays.toString(lines));
			Log.i(TAG, "Logs found : "+lines.length);
			
	        for (String line : lines) {
	        	if (!(line.trim().equals(""))) { 
	        		String[] csv = line.split("\t");
	        		System.out.println(java.util.Arrays.toString(csv));
	        		try {
	        			tl= new TrigLog(
	        					csv[3],							//username 
	        					csv[0], 						//date
	        					Condition.fromCode(csv[2]), 	//condition
	        					csv[1]);						//text
	        			mTrigLogs.add(tl);
	        			count++;
	        		} catch (Exception e) {
	        			System.out.println(e);
	        		}
	        	}
	        }
	        return count;
		}
		protected void onPreExecute() {
			// create string loader class
	        mStrLoader = new StringLoader(TrigDetailsLogsTab.this);
		}
		protected void onProgressUpdate(Integer... progress) {
		}
		protected void onPostExecute(Integer arg0) {
	        mTrigLogsAdapter.notifyDataSetChanged();
		}
	}

    
}