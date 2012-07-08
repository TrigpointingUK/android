package com.trigpointinguk.android.trigdetails;

import java.util.ArrayList;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.TextView;

import com.trigpointinguk.android.R;
import com.trigpointinguk.android.types.TrigLog;

public class TrigDetailsLoglistAdapter extends ArrayAdapter<TrigLog> {
	private ArrayList<TrigLog>   mLogs;
	private Context 			 mContext;
	
	public TrigDetailsLoglistAdapter(Context context, int rowResourceId, ArrayList<TrigLog> logs) {
		super(context, rowResourceId, logs);
    	mContext = context;
        mLogs=logs;
	}

    public int getCount() {
        return mLogs.size();
    }
    
	@Override
	public TrigLog getItem(int position) {
		return mLogs.get(position);
	}

	@Override
	public View getView(int position, View convertView, ViewGroup parent) {
		View row;
		 
		if (null == convertView) {
			row = LayoutInflater.from(mContext).inflate(R.layout.triglogrow, parent, false);
		} else {
			row = convertView;
		}
		 		
		TextView  tu = (TextView)  row.findViewById(R.id.logDateUser);
		TextView  tt = (TextView)  row.findViewById(R.id.logText);
		ImageView tc = (ImageView) row.findViewById(R.id.trigLogCondition);

		TrigLog tl = getItem(position);
		if (tl.getText() != null && !tl.getText().equals("")) {
			tt.setText(tl.getText());
			tt.setVisibility(View.VISIBLE);
		} else {
			tt.setText("");
			tt.setVisibility(View.GONE);
		}
			
		tu.setText(tl.getDate()+"   "+tl.getUsername());
		tc.setImageResource(tl.getCondition().icon());
		
		return row;
	}

	
}
