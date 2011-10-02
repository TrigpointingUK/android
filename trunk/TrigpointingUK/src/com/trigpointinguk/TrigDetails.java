package com.trigpointinguk;

import android.app.TabActivity;
import android.content.Intent;
import android.content.res.Resources;
import android.os.Bundle;
import android.widget.TabHost;

public class TrigDetails extends TabActivity {

	public void onCreate(Bundle savedInstanceState) {
	    super.onCreate(savedInstanceState);
	    setContentView(R.layout.trigdetails);

		Bundle extras = getIntent().getExtras();
	    
	    Resources res = getResources();
	    TabHost tabHost = getTabHost();
	    TabHost.TabSpec spec;
	    Intent intent;

	    intent = new Intent().setClass(this, TrigInfo.class);
	    intent.putExtras(extras);
	    spec = tabHost.newTabSpec("info").setIndicator("",
	                    res.getDrawable(android.R.drawable.ic_menu_info_details))
	                    .setContent(intent);
	    tabHost.addTab(spec);

	    intent = new Intent().setClass(this, TrigLogs.class);
	    intent.putExtras(extras);
	    spec = tabHost.newTabSpec("logs").setIndicator("",
	                      res.getDrawable(android.R.drawable.ic_menu_agenda))
	                  .setContent(intent);
	    tabHost.addTab(spec);

	    intent = new Intent().setClass(this, TrigAlbum.class);
	    intent.putExtras(extras);
	    spec = tabHost.newTabSpec("album").setIndicator("",
	                      res.getDrawable(android.R.drawable.ic_menu_gallery))
	                  .setContent(intent);
	    tabHost.addTab(spec);

	    intent = new Intent().setClass(this, TrigOSMap.class);
	    intent.putExtras(extras);
	    spec = tabHost.newTabSpec("map").setIndicator("",
	                      res.getDrawable(android.R.drawable.ic_menu_mapmode))
	                  .setContent(intent);
	    tabHost.addTab(spec);

	    intent = new Intent().setClass(this, TrigAlbum.class);
	    intent.putExtras(extras);
	    spec = tabHost.newTabSpec("mylog").setIndicator("",
	                      res.getDrawable(android.R.drawable.ic_menu_edit))
	                  .setContent(intent);
	    tabHost.addTab(spec);

	    
	    tabHost.setCurrentTab(0);
	}
}