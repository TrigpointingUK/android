package com.trigpointinguk.android.common;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;

public class Utils {
    public static void CopyStream(InputStream is, OutputStream os)
    {
        final int buffer_size=1024;
        try
        {
            byte[] bytes=new byte[buffer_size];
            for(;;)
            {
              int count=is.read(bytes, 0, buffer_size);
              if(count==-1)
                  break;
              os.write(bytes, 0, count);
            }
        }
        catch(Exception ex){}
    }
    
    
	public static Bitmap decodeUri(Context ctx, Uri selectedImage, int requiredSize) throws FileNotFoundException {

        // Decode image size
        BitmapFactory.Options o = new BitmapFactory.Options();
        o.inJustDecodeBounds = true;
        BitmapFactory.decodeStream(ctx.getContentResolver().openInputStream(selectedImage), null, o);

        // Find the correct scale value. It should be the power of 2.
        int width_tmp = o.outWidth, height_tmp = o.outHeight;
        int scale = 1;
        while (true) {
            if (width_tmp / 2 < requiredSize
               || height_tmp / 2 < requiredSize) {
                break;
            }
            width_tmp /= 2;
            height_tmp /= 2;
            scale *= 2;
        }

        // Decode with inSampleSize
        BitmapFactory.Options o2 = new BitmapFactory.Options();
        o2.inSampleSize = scale;
        return BitmapFactory.decodeStream(ctx.getContentResolver().openInputStream(selectedImage), null, o2);

    }

	
	public static void saveBitmapToFile(String fileName, Bitmap bmp, Integer quality) throws IOException
	{
	    File f = new File(fileName);
	    f.createNewFile();
	    FileOutputStream fOut = null;
	    try {
	        fOut = new FileOutputStream(f);
	    } catch (FileNotFoundException e) {
	        e.printStackTrace();
	    }

	    bmp.compress(Bitmap.CompressFormat.JPEG, quality, fOut);
	    try {
	        fOut.flush();
	    } catch (IOException e) {
	        e.printStackTrace();
	    }
	    try {
	        fOut.close();
	    } catch (IOException e) {
	        e.printStackTrace();
	    }
	}

    
}