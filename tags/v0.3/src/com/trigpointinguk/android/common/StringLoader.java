package com.trigpointinguk.android.common;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.net.URL;
import java.net.URLConnection;

import android.content.Context;
import android.util.Log;

public class StringLoader {
	private static final String TAG="StringLoader";
    private static MemoryCache mMemoryCache=new MemoryCache();
    FileCache   mFileCache;

    public StringLoader(Context context) {
         mFileCache=new FileCache(context, "strings");
    }
	
	public String getString(String url, boolean reload) {
	    String strResult = null;
	    StringBuilder text;
	    
    	File file=mFileCache.getFile(url);

	    if (!reload) {
		    // try softreference memory cache
	    	strResult =  mMemoryCache.getString(url);
	    	if(strResult != null) {
	    		Log.i(TAG, "Got "+url+" from memory");
	    		return strResult;
	    	}

	    	// try file cache
	    	if (file.exists()) {
	    		// get from filesystem cache	
	    		text = new StringBuilder();
	    		try {
	    			BufferedReader in =  new BufferedReader(new InputStreamReader(new FileInputStream(file), "UTF-8"));
	    			String line = null;
	    			try {
	    				while ((line = in.readLine()) != null) {
	    					text.append(line+"\n");
	    				}
	    				mMemoryCache.put(url, text.toString());
	    				Log.i(TAG, "Got "+url+" from SD cache");
	    				return text.toString();
	    			} finally { 
	    				in.close();
	    			}
	    		} catch (Exception e) {
	    			e.printStackTrace();
	    		}
	    	}
	    }
        
	    //from web
	    try {
	    	Log.i(TAG, "Downloading " + url);
	    	URLConnection conn = new URL(url).openConnection();
	        conn.setConnectTimeout(30000);
	        conn.setReadTimeout(30000);
			BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream()));
	        
	        OutputStream os = new FileOutputStream(file);
			BufferedWriter out = new BufferedWriter(new OutputStreamWriter(os, "UTF-8"));
	        
	        text = new StringBuilder();
	        String line = null;
	        try {
	        	while ((line = in.readLine()) != null) {
	        		text.append(line);
	        		text.append(System.getProperty("line.separator"));
	        		out.write(line + "\n");
	        	}
    			mMemoryCache.put(url, text.toString());
	        	Log.i(TAG, "Got "+url+" from network");
	        	return text.toString();
	        } finally { 
	        	in.close();
	        	out.close();
	        }
	    } catch (Exception e) {
	    	e.printStackTrace();
	    }

	    Log.i(TAG, "FAILED to get "+url);
		return null;
	}



}
