package uk.trigpointing.android.ar;

import android.content.Context;
import android.graphics.Matrix;
import android.graphics.SurfaceTexture;
import android.util.AttributeSet;
import android.view.TextureView;

/**
 * TextureView-based preview surface for Camera2.
 * Exposes a listener to notify when the SurfaceTexture is available or size changes.
 */
public class Camera2Preview extends TextureView implements TextureView.SurfaceTextureListener {

    public interface SurfaceEventsListener {
        void onSurfaceAvailable(SurfaceTexture surface, int width, int height);
        void onSurfaceSizeChanged(SurfaceTexture surface, int width, int height);
        void onSurfaceDestroyed();
    }

    private SurfaceEventsListener surfaceEventsListener;

    public Camera2Preview(Context context, AttributeSet attrs) {
        super(context, attrs);
        setSurfaceTextureListener(this);
    }

    public void setSurfaceEventsListener(SurfaceEventsListener listener) {
        this.surfaceEventsListener = listener;
        if (isAvailable() && listener != null) {
            listener.onSurfaceAvailable(getSurfaceTexture(), getWidth(), getHeight());
        }
    }

    @Override
    public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
        if (surfaceEventsListener != null) {
            surfaceEventsListener.onSurfaceAvailable(surface, width, height);
        }
    }

    @Override
    public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {
        if (surfaceEventsListener != null) {
            surfaceEventsListener.onSurfaceSizeChanged(surface, width, height);
        }
    }

    @Override
    public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
        if (surfaceEventsListener != null) {
            surfaceEventsListener.onSurfaceDestroyed();
        }
        return true; // We will release the SurfaceTexture
    }

    @Override
    public void onSurfaceTextureUpdated(SurfaceTexture surface) {
        // no-op
    }

    /**
     * Apply a transform so the preview fills the view while keeping aspect ratio.
     */
    public void applyTransform(int viewWidth, int viewHeight, int bufferWidth, int bufferHeight, int rotationDegrees) {
        if (viewWidth <= 0 || viewHeight <= 0 || bufferWidth <= 0 || bufferHeight <= 0) return;
        float viewW = viewWidth;
        float viewH = viewHeight;
        float bufW = bufferWidth;
        float bufH = bufferHeight;

        Matrix matrix = new Matrix();

        // Account for buffer rotation
        boolean swap = (rotationDegrees == 90 || rotationDegrees == 270);
        float effectiveBw = swap ? bufH : bufW;
        float effectiveBh = swap ? bufW : bufH;

        // Center crop: scale so both dimensions fill, preserving aspect
        float scaleX = viewW / effectiveBw;
        float scaleY = viewH / effectiveBh;
        float scale = Math.max(scaleX, scaleY);

        float scaledW = effectiveBw * scale;
        float scaledH = effectiveBh * scale;
        float dx = (viewW - scaledW) / 2f;
        float dy = (viewH - scaledH) / 2f;

        // Apply rotation around center so that buffer aligns to view orientation
        if (rotationDegrees != 0) {
            matrix.postRotate(rotationDegrees, viewW / 2f, viewH / 2f);
        }

        // Then scale about top-left and translate to center crop
        matrix.postScale(scale, scale, 0f, 0f);
        matrix.postTranslate(dx, dy);

        setTransform(matrix);
    }
}


