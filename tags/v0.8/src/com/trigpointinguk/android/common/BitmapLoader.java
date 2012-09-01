package com.trigpointinguk.android.common;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;
import java.net.URLConnection;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Log;

public class BitmapLoader {
	private static final String TAG="BitmapLoader";
	private static MemoryCache mMemoryCache=new MemoryCache();
	FileCache   mFileCache;

	public BitmapLoader(Context context) {
		mFileCache=new FileCache(context, "bitmaps");
	}

	public Bitmap getBitmap(String url, boolean reload) {
		Log.i(TAG, "getBitmap " + url + " , reload : " + reload);
		Bitmap bResult = null;

		File file=mFileCache.getFile(url);

		if (!reload) {
			// try softreference memory cache
			bResult =  mMemoryCache.getBitmap(url);
			if(bResult != null) {
				Log.i(TAG, "Got "+url+" from memory");
				return bResult;
			}

			// try file cache
			try {
				//from SD cache
				bResult = BitmapFactory.decodeFile(file.getAbsolutePath());
				if(bResult != null) {
					Log.i(TAG, "Got "+url+" from SD cache");
					return bResult;
				}
			} catch (Exception e) {
				e.printStackTrace();
			}
		} else {
			Log.i(TAG, "Explicit request to reload from web");
		}

		//from web
		try {
			Log.i(TAG, "Downloading from web " + url);
			URLConnection conn = new URL(url).openConnection();
			conn.setConnectTimeout(30000);
			conn.setReadTimeout(30000);
			InputStream is=conn.getInputStream();
			OutputStream os = new FileOutputStream(file);
			Utils.CopyStream(is, os);
			os.close();
			bResult = BitmapFactory.decodeFile(file.getAbsolutePath());
			if(bResult != null) {
				Log.i(TAG, "Got "+url+" from SD cache");
				return bResult;
			}
		} catch (Exception e) {
			e.printStackTrace();
		}

		Log.i(TAG, "FAILED to get "+url);
		return null;
	}
}
