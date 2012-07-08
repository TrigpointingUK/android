package com.trigpointinguk.android.trigdetails;

import java.util.ArrayList;

import android.app.ListActivity;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;

import com.trigpointinguk.android.DbHelper;
import com.trigpointinguk.android.R;
import com.trigpointinguk.android.common.StringLoader;
import com.trigpointinguk.android.types.Condition;
import com.trigpointinguk.android.types.TrigLog;

public class TrigDetailsLoglistTab extends ListActivity {
	private static final String TAG="TrigDetailsLoglistTab";
	
	private long mTrigId;
	private StringLoader mStrLoader;
    private ArrayList<TrigLog> mTrigLogs;
    private TrigDetailsLoglistAdapter mTrigLogsAdapter;

	
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.triglogs);
        
        // get trig_id from extras
        Bundle extras = getIntent().getExtras();
		if (extras == null) {return;}
		mTrigId = extras.getLong(DbHelper.TRIG_ID);
		Log.i(TAG, "Trig_id = "+mTrigId);

        // attach the array adapter
		mTrigLogs = new ArrayList<TrigLog>();
        mTrigLogsAdapter = new TrigDetailsLoglistAdapter(TrigDetailsLoglistTab.this, R.layout.triglogrow, mTrigLogs); 
		setListAdapter(mTrigLogsAdapter);

		// get list of photos
        new PopulateLogsTask().execute(false);
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
	            new PopulateLogsTask().execute(true);
	            return true;
	        }
			return super.onOptionsItemSelected(item);
	}




	private class PopulateLogsTask extends AsyncTask<Boolean, Integer, Integer> {
		protected Integer doInBackground(Boolean... arg0) {
			int count=0;
			mTrigLogs.clear();
			
			// get triglogs from T:UK, refresh if requested
	        String url = String.format("http://www.trigpointinguk.com/trigs/down-android-triglogs.php?t=%d", mTrigId);
	        String list = mStrLoader.getString(url, arg0[0]);
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
	        		//System.out.println(java.util.Arrays.toString(csv));
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
	        mStrLoader = new StringLoader(TrigDetailsLoglistTab.this);
		}
		protected void onProgressUpdate(Integer... progress) {
		}
		protected void onPostExecute(Integer arg0) {
	        mTrigLogsAdapter.notifyDataSetChanged();
		}
	}

    
}