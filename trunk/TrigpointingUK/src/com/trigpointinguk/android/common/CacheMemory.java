package com.trigpointinguk.android.common;

import java.lang.ref.SoftReference;
import java.util.HashMap;
import android.graphics.Bitmap;

public class CacheMemory {
    private HashMap<String, SoftReference<Object>> cache=new HashMap<String, SoftReference<Object>>();
    
    public Bitmap getBitmap(String id){
        if(!cache.containsKey(id))
            return null;
        SoftReference<Object> ref=cache.get(id);
        return (Bitmap)ref.get();
    }
    
    public String getString(String id){
        if(!cache.containsKey(id))
            return null;
        SoftReference<Object> ref=cache.get(id);
        return (String)ref.get();
    }
    
    public void put(String id, Bitmap bitmap){
        cache.put(id, new SoftReference<Object>(bitmap));
    }

    public void clear() {
        cache.clear();
    }
}