package uk.trigpointing.android.common;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Collections;
import java.util.Map;
import java.util.Stack;
import java.util.WeakHashMap;

import android.app.Activity;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Log;
import android.widget.ImageView;

import uk.trigpointing.android.R;

// Code from https://github.com/thest1/LazyList

public class LazyImageLoader {
	private static final String TAG = "BitmapLoader";

    final int stub_id=R.drawable.imageloading;

    MemoryCache memoryCache=new MemoryCache();
    FileCache fileCache;
    private final Map<ImageView, String> imageViews=Collections.synchronizedMap(new WeakHashMap<>());

    PhotosLoader photoLoaderThread=new PhotosLoader();
    PhotosQueue photosQueue= new PhotosQueue();

    
    public LazyImageLoader(Context context){
        //Make the background thread low priority. This way it will not affect the UI performance
        photoLoaderThread.setPriority(Thread.NORM_PRIORITY-1);
        
        fileCache=new FileCache(context, "images");
    }
    
    public void DisplayImage(String url, ImageView imageView)
    {
        imageViews.put(imageView, url);
        Bitmap bitmap=memoryCache.getBitmap(url);
        if(bitmap!=null) {
            imageView.setImageBitmap(bitmap);
            Log.i(TAG, "Got "+url+" from memory");
        } else {
            queuePhoto(url, imageView);
            imageView.setImageResource(stub_id);
        }    
    }
        
    private void queuePhoto(String url, ImageView imageView)
    {
        //This ImageView may be used for other images before. So there may be some old tasks in the queue. We need to discard them. 
        photosQueue.Clean(imageView);
        PhotoToLoad p= new PhotoToLoad(url, imageView);
        synchronized(photosQueue.photosToLoad){
            photosQueue.photosToLoad.push(p);
            photosQueue.photosToLoad.notifyAll();
        }
        
        //start thread if it's not started yet
        if(photoLoaderThread.getState()==Thread.State.NEW)
            photoLoaderThread.start();
    }
    
    private Bitmap getBitmap(String url) 
    {
        File f=fileCache.getFile(url);
        
        //from SD cache
        Bitmap b = decodeFile(f);
        if(b!=null) {
        	Log.i(TAG, "Got "+url+" from SD cache");
            return b;
        }
        
        //from web
        try {
            Bitmap bitmap;
            URL imageUrl = new URL(url);
            HttpURLConnection conn = (HttpURLConnection)imageUrl.openConnection();
            conn.setConnectTimeout(30000);
            conn.setReadTimeout(30000);
            InputStream is=conn.getInputStream();
            OutputStream os = new FileOutputStream(f);
            Utils.CopyStream(is, os);
            os.close();
            bitmap = decodeFile(f);
            Log.i(TAG, "Got "+url+" from network");
            return bitmap;
        } catch (Exception ex){
           Log.e(TAG, "Error loading image from URL: " + url, ex);
           // Clean up the failed download file
           if (f.exists()) {
               boolean ok = f.delete();
               if (!ok) {
                   Log.w(TAG, "Failed to delete partial image file: " + f.getAbsolutePath());
               }
           }
           return null;
        }
    }

    //decodes image and scales it to reduce memory consumption
    private Bitmap decodeFile(File f){
        if (!f.exists()) {
            return null; // File doesn't exist, no need to log error
        }
        
        try {
            //decode image size
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inJustDecodeBounds = true;
            BitmapFactory.decodeStream(new FileInputStream(f),null,o);
            
            //Find the correct scale value. It should be the power of 2.
            final int REQUIRED_SIZE=600;
            int width_tmp=o.outWidth, height_tmp=o.outHeight;
            int scale=1;
            while (width_tmp / 2 >= REQUIRED_SIZE && height_tmp / 2 >= REQUIRED_SIZE) {
                width_tmp /= 2;
                height_tmp /= 2;
                scale *= 2;
            }
            
            //decode with inSampleSize
            BitmapFactory.Options o2 = new BitmapFactory.Options();
            o2.inSampleSize=scale;
            return BitmapFactory.decodeStream(new FileInputStream(f), null, o2);
        } catch (FileNotFoundException e) {
            Log.d(TAG, "File not found: " + f.getAbsolutePath()); // Changed to debug level
        } catch (Exception e) {
            Log.e(TAG, "Error decoding file: " + f.getAbsolutePath(), e);
        }
        return null;
    }
    
    //Task for the queue
    private static class PhotoToLoad
    {
        public String url;
        public ImageView imageView;
        public PhotoToLoad(String u, ImageView i){
            url=u; 
            imageView=i;
        }
    }


    //stores list of photos to download
    static class PhotosQueue
    {
        private final Stack<PhotoToLoad> photosToLoad= new Stack<>();
        
        //removes all instances of this ImageView
        public void Clean(ImageView image)
        {
            for(int j=0 ;j<photosToLoad.size();){
                if(photosToLoad.get(j).imageView==image)
                    photosToLoad.remove(j);
                else
                    ++j;
            }
        }
    }
    
    class PhotosLoader extends Thread {
        public void run() {
            try {
                do {
                    //thread waits until there are any images to load in the queue
                    if (photosQueue.photosToLoad.isEmpty())
                        synchronized (photosQueue.photosToLoad) {
                            photosQueue.photosToLoad.wait();
                        }
                    if (!photosQueue.photosToLoad.isEmpty()) {
                        PhotoToLoad photoToLoad;
                        synchronized (photosQueue.photosToLoad) {
                            photoToLoad = photosQueue.photosToLoad.pop();
                        }
                        Bitmap bmp = getBitmap(photoToLoad.url);
                        memoryCache.put(photoToLoad.url, bmp);
                        String tag = imageViews.get(photoToLoad.imageView);
                        if (tag != null && tag.equals(photoToLoad.url)) {
                            BitmapDisplayer bd = new BitmapDisplayer(bmp, photoToLoad.imageView);
                            Activity a = (Activity) photoToLoad.imageView.getContext();
                            a.runOnUiThread(bd);
                        }
                    }
                } while (!Thread.interrupted());
            } catch (InterruptedException e) {
                //allow thread to exit
            }
        }
    }
    
    
    //Used to display bitmap in the UI thread
    class BitmapDisplayer implements Runnable
    {
        Bitmap bitmap;
        ImageView imageView;
        public BitmapDisplayer(Bitmap b, ImageView i){
        	bitmap=b;
        	imageView=i;
        }
        public void run()
        {
            if(bitmap!=null) {
                imageView.setImageBitmap(bitmap);
                Log.d(TAG, "Displayed bitmap successfully");
            } else {
                imageView.setImageResource(stub_id);
                Log.w(TAG, "Bitmap was null, showing placeholder image");
            }
        }
    }

}
