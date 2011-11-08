package com.trigpointinguk.android;

import android.app.Activity;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.TimePicker;
import android.widget.Toast;

public class TrigDetailsLogTrigTab extends Activity {
	private static final String TAG="TrigDetailsLogTrigTab";

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.logtrig);

		TimePicker tp = (TimePicker) findViewById(R.id.logTime);
		tp.setIs24HourView(true);
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		boolean result = super.onCreateOptionsMenu(menu);
		getMenuInflater().inflate(R.menu.logtrigmenu, menu);
		return result;
	}    

	public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
        case R.id.addphoto:
        	Toast.makeText(this, "Start camera intent and grab results", Toast.LENGTH_SHORT).show();
            return true;
        case R.id.addlocation:
        	Toast.makeText(this, "Start location listener and grab results", Toast.LENGTH_SHORT).show();
        	return true;
        }
        return false;
    }
}
