package com.trigpointinguk;

import android.app.Activity;
import android.content.Context;
import android.content.res.TypedArray;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Gallery;
import android.widget.ImageView;

public class TrigOSMapAdapter extends BaseAdapter {
    
    private Activity mActivity;
    private Context mContext;
    private String[] mUrls;
    public ImageLoader imageLoader;
	private int mGalleryItemBackground; 
    
    public TrigOSMapAdapter(Activity activity, String[] urls) {
    	mActivity = activity;
    	mContext = activity.getApplicationContext();
        mUrls=urls;
        imageLoader=new ImageLoader(mActivity);
        
        TypedArray attr = mContext.obtainStyledAttributes(R.styleable.HelloGallery);
        mGalleryItemBackground = attr.getResourceId(R.styleable.HelloGallery_android_galleryItemBackground, 0);
    }

    public int getCount() {
        return mUrls.length;
    }

    public Object getItem(int position) {
        return position;
    }

    public long getItemId(int position) {
        return position;
    }
    
    public View getView(int position, View convertView, ViewGroup parent) {
        ImageView imageView = new ImageView(mActivity);
        imageLoader.DisplayImage(mUrls[position], mActivity, imageView);

        imageView.setLayoutParams(new Gallery.LayoutParams(300, 300));
        imageView.setScaleType(ImageView.ScaleType.FIT_XY);
        imageView.setBackgroundResource(mGalleryItemBackground);

        return imageView;
    }
}