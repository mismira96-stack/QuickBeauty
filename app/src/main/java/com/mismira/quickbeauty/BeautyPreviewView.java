package com.mismira.quickbeauty;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

public class BeautyPreviewView extends View {
    private static final int MESH_W = 40;
    private static final int MESH_H = 40;

    private Bitmap sourceBitmap;
    private BeautyLandmarks landmarks;
    private BeautyAdjustParams params = new BeautyAdjustParams();
    private boolean showOriginal = false;

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final RectF destRect = new RectF();

    private float[] cachedVerts;
    private float[] transformedVerts;
    private boolean vertsDirty = true;
    private float lastLeft = -1;
    private float lastTop = -1;
    private float lastScale = -1;

    public BeautyPreviewView(Context context) {
        super(context);
        init();
    }

    public BeautyPreviewView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        setBackgroundColor(Color.BLACK);
        setLayerType(View.LAYER_TYPE_SOFTWARE, null);
    }

    public void setSource(Bitmap bitmap, BeautyLandmarks landmarks) {
        this.sourceBitmap = bitmap;
        this.landmarks = landmarks;
        this.vertsDirty = true;
        invalidate();
    }

    public void setParams(BeautyAdjustParams params) {
        this.params = params.copy();
        this.vertsDirty = true;
        invalidate();
    }

    public void setShowOriginal(boolean show) {
        if (this.showOriginal != show) {
            this.showOriginal = show;
            invalidate();
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (sourceBitmap == null || sourceBitmap.isRecycled()) return;

        int viewW = getWidth();
        int viewH = getHeight();
        if (viewW <= 0 || viewH <= 0) return;

        int bmpW = sourceBitmap.getWidth();
        int bmpH = sourceBitmap.getHeight();

        float scale = Math.min((float) viewW / bmpW, (float) viewH / bmpH);
        float drawW = bmpW * scale;
        float drawH = bmpH * scale;
        float left = (viewW - drawW) * 0.5f;
        float top = (viewH - drawH) * 0.5f;
        destRect.set(left, top, left + drawW, top + drawH);

        if (showOriginal || params.isDefault() || landmarks == null) {
            paint.setColorFilter(null);
            canvas.save();
            canvas.translate(left, top);
            canvas.scale(scale, scale);
            canvas.drawBitmap(sourceBitmap, 0, 0, paint);
            canvas.restore();
            return;
        }

        if (params.getCoolTone() > 0) {
            ColorMatrix cm = BeautyFilterEngine.createCoolToneMatrix(params.getCoolTone());
            paint.setColorFilter(new ColorMatrixColorFilter(cm));
        } else {
            paint.setColorFilter(null);
        }

        boolean needWarp = params.getFaceSlim() > 0 || params.getChinSlim() > 0 || params.getBodySlim() > 0;
        if (!needWarp) {
            canvas.save();
            canvas.translate(left, top);
            canvas.scale(scale, scale);
            canvas.drawBitmap(sourceBitmap, 0, 0, paint);
            canvas.restore();
        } else {
            boolean geometryChanged = (left != lastLeft || top != lastTop || scale != lastScale);
            if (vertsDirty || cachedVerts == null) {
                cachedVerts = BeautyFilterEngine.computeWarpedVertices(bmpW, bmpH, landmarks, params);
                vertsDirty = false;
                geometryChanged = true;
            }

            if (geometryChanged || transformedVerts == null || transformedVerts.length != cachedVerts.length) {
                int count = cachedVerts.length;
                if (transformedVerts == null || transformedVerts.length != count) {
                    transformedVerts = new float[count];
                }
                for (int i = 0; i < count; i += 2) {
                    transformedVerts[i] = left + cachedVerts[i] * scale;
                    transformedVerts[i + 1] = top + cachedVerts[i + 1] * scale;
                }
                lastLeft = left;
                lastTop = top;
                lastScale = scale;
            }

            canvas.drawBitmapMesh(sourceBitmap, MESH_W, MESH_H, transformedVerts, 0, null, 0, paint);
        }
    }
}