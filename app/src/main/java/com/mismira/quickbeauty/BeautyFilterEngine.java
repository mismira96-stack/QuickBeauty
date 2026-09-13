package com.mismira.quickbeauty;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.Paint;

public class BeautyFilterEngine {
    private static final int MESH_W = 40;
    private static final int MESH_H = 40;

    public static float[] getCoolToneMatrixArray(int coolTone) {
        if (coolTone <= 0) {
            return new float[] {
                1, 0, 0, 0, 0,
                0, 1, 0, 0, 0,
                0, 0, 1, 0, 0,
                0, 0, 0, 1, 0
            };
        }

        float f = coolTone / 100.0f;
        float rScale = 1.0f - f * 0.01f;
        float gScale = 1.0f - f * 0.07f;
        float bScale = 1.0f + f * 0.16f;
        float brightness = f * 20.0f;

        return new float[] {
            rScale, 0,      0,      0, brightness * 0.85f,
            0,      gScale, 0,      0, brightness * 0.75f,
            0,      0,      bScale, 0, brightness * 1.25f,
            0,      0,      0,      1, 0
        };
    }

    public static ColorMatrix createCoolToneMatrix(int coolTone) {
        return new ColorMatrix(getCoolToneMatrixArray(coolTone));
    }

    public static float[] computeWarpedVertices(int width, int height,
                                                BeautyLandmarks landmarks,
                                                BeautyAdjustParams params) {
        int totalVerts = (MESH_W + 1) * (MESH_H + 1);
        float[] verts = new float[totalVerts * 2];

        float faceSlimFactor = (params.getFaceSlim() / 100.0f) * 0.28f;
        float chinSlimFactor = (params.getChinSlim() / 100.0f) * 0.35f;
        float bodySlimFactor = (params.getBodySlim() / 100.0f) * 0.25f;

        BeautyLandmarks.Point fc = landmarks.faceCenter;
        float faceRadius = Math.max(landmarks.faceBounds.width(), landmarks.faceBounds.height()) * 0.55f;
        if (faceRadius <= 10f) faceRadius = Math.min(width, height) * 0.2f;

        float chinY = landmarks.chinPoint.y;
        float chinX = landmarks.chinPoint.x;

        float bodyCenterY = landmarks.bodyCenterY;
        float bodyCenterX = landmarks.bodyBounds.centerX();
        float bodyHalfHeight = Math.max(20f, (landmarks.bodyBottomY - landmarks.bodyTopY) * 0.5f);
        float bodyHalfWidth = Math.max(20f, landmarks.bodyBounds.width() * 0.6f);

        int index = 0;
        for (int r = 0; r <= MESH_H; r++) {
            float origY = (float) r * height / MESH_H;
            for (int c = 0; c <= MESH_W; c++) {
                float origX = (float) c * width / MESH_W;
                float currX = origX;
                float currY = origY;

                // 1. Face Slim
                if (faceSlimFactor > 0.001f && landmarks.hasFace) {
                    float dx = origX - fc.x;
                    float dy = origY - fc.y;
                    float dist = (float) Math.sqrt(dx * dx + dy * dy);
                    float maxR = faceRadius * 1.5f;
                    if (dist < maxR && dist > 1f) {
                        float norm = dist / maxR;
                        float weight = (1.0f - norm) * (1.0f - norm) * faceSlimFactor;
                        currX -= dx * weight;
                        currY -= dy * weight * 0.8f;
                    }
                }

                // 2. Chin Slim (V-Line Jaw & Chin Lift)
                if (chinSlimFactor > 0.001f && landmarks.hasFace) {
                    float dx = origX - chinX;
                    float dy = origY - chinY;
                    float maxDx = faceRadius * 1.2f;
                    float maxUpperY = Math.max(20f, (chinY - fc.y) * 0.95f);
                    float maxLowerY = Math.max(20f, faceRadius * 0.45f);

                    float yWeight = 0f;
                    if (dy < 0 && -dy < maxUpperY) {
                        float yNorm = (-dy) / maxUpperY;
                        yWeight = (1.0f - yNorm) * (1.0f - yNorm);
                    } else if (dy >= 0 && dy < maxLowerY) {
                        float yNorm = dy / maxLowerY;
                        yWeight = (1.0f - yNorm) * (1.0f - yNorm);
                    }

                    if (yWeight > 0f) {
                        float absDx = Math.abs(dx);
                        if (absDx < maxDx) {
                            float xNorm = absDx / maxDx;
                            float xWeight = (1.0f - xNorm) * (1.0f - xNorm);
                            float totalWeight = yWeight * xWeight * chinSlimFactor;

                            currX -= dx * totalWeight * 0.85f;
                            if (dy > 0) {
                                currY -= dy * totalWeight * 0.35f;
                            }
                        }
                    }
                }

                // 3. Body Slim
                if (bodySlimFactor > 0.001f && landmarks.hasBody) {
                    if (origY >= landmarks.bodyTopY && origY <= landmarks.bodyBottomY) {
                        float dy = Math.abs(origY - bodyCenterY);
                        float yWeight = 1.0f - (dy / bodyHalfHeight);
                        if (yWeight > 0f) {
                            yWeight = (float) Math.sin(yWeight * Math.PI * 0.5);
                            float dx = origX - bodyCenterX;
                            float absDx = Math.abs(dx);
                            if (absDx < bodyHalfWidth * 2.0f) {
                                float xNorm = absDx / (bodyHalfWidth * 2.0f);
                                float xWeight = (1.0f - xNorm) * (1.0f - xNorm);
                                float totalWeight = yWeight * xWeight * bodySlimFactor;
                                currX -= dx * totalWeight;
                            }
                        }
                    }
                }

                verts[index++] = currX;
                verts[index++] = currY;
            }
        }
        return verts;
    }

    public static Bitmap process(Bitmap source, BeautyLandmarks landmarks, BeautyAdjustParams params) {
        if (source == null || source.isRecycled()) return null;

        int w = source.getWidth();
        int h = source.getHeight();

        Bitmap output = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(output);

        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);

        if (params.getCoolTone() > 0) {
            ColorMatrix cm = createCoolToneMatrix(params.getCoolTone());
            paint.setColorFilter(new ColorMatrixColorFilter(cm));
        }

        boolean needWarp = params.getFaceSlim() > 0 || params.getChinSlim() > 0 || params.getBodySlim() > 0;
        if (needWarp) {
            BeautyLandmarks scaledLandmarks = (landmarks.imageWidth == w && landmarks.imageHeight == h) ?
                    landmarks : landmarks.scaleTo(w, h);
            float[] verts = computeWarpedVertices(w, h, scaledLandmarks, params);
            canvas.drawBitmapMesh(source, MESH_W, MESH_H, verts, 0, null, 0, paint);
        } else {
            canvas.drawBitmap(source, 0, 0, paint);
        }

        return output;
    }
}