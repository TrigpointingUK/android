package com.trigpointinguk.android.common;

import java.io.File;
import android.content.Context;

public class FileCache {

	private File cacheDir;

	public FileCache(Context context, String cachedir){
		//Find the dir to save cached objects
		if (android.os.Environment.getExternalStorageState().equals(android.os.Environment.MEDIA_MOUNTED)) {
			cacheDir=new File(android.os.Environment.getExternalStorageDirectory() + "/Android/data/com.trigpointinguk/cache/", cachedir);
		} else {
			cacheDir=context.getCacheDir();
		}
		if(!cacheDir.exists())
			cacheDir.mkdirs();
	}

	public File getFile(String url){
		String filename=String.valueOf(url.hashCode());
		File f = new File(cacheDir, filename);
		return f;
	}

	public int clear(){
		File[] files=cacheDir.listFiles();
		for(File f:files) {
			f.delete();
		}
		return files.length;
	}

}
