package com.trigpointinguk.android.trigdetails;

import android.content.Context;
import android.content.res.TypedArray;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Gallery;
import android.widget.ImageView;

import com.trigpointinguk.android.R;
import com.trigpointinguk.android.common.LazyImageLoader;

public class TrigDetailsOSMapAdapter extends BaseAdapter {
    
    private String[] mUrls;
    public LazyImageLoader imageLoader;
	private int mGalleryItemBackground; 
	private Context mContext;
    
    public TrigDetailsOSMapAdapter(Context context, String[] urls) {
        mUrls=urls;
        mContext=context;
        imageLoader=new LazyImageLoader(context);
        
        TypedArray attr = context.obtainStyledAttributes(R.styleable.TrigpointingUK);
        mGalleryItemBackground = attr.getResourceId(R.styleable.TrigpointingUK_android_galleryItemBackground, 0);
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
        ImageView imageView = new ImageView(mContext);
        imageLoader.DisplayImage(mUrls[position], imageView);

        imageView.setLayoutParams(new Gallery.LayoutParams(300, 300));
        imageView.setScaleType(ImageView.ScaleType.FIT_XY);
        imageView.setBackgroundResource(mGalleryItemBackground);

        return imageView;
    }
}