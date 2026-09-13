package com.mismira.quickbeauty;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.media.ExifInterface;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * ?덉쟾??鍮꾪듃留??붿퐫?? EXIF ?뚯쟾 泥섎━ 諛?媛ㅻ윭由?????좏떥由ы떚
 */
public class BeautyBitmapUtils {
    private static final String TAG = "BeautyBitmapUtils";

    /**
     * ?붾㈃ ?꾨━酉곗슜?쇰줈 ?덉쟾?섍쾶 異뺤냼 ?붿퐫??     */
    public static Bitmap decodeSampledBitmapFromUri(Context context, Uri uri, int maxDimension) {
        try {
            ContentResolver resolver = context.getContentResolver();

            // 1. ?ш린留??쎄린
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inJustDecodeBounds = true;
            try (InputStream in = resolver.openInputStream(uri)) {
                if (in == null) return null;
                BitmapFactory.decodeStream(in, null, options);
            }

            int w = options.outWidth;
            int h = options.outHeight;
            if (w <= 0 || h <= 0) return null;

            // 2. inSampleSize 怨꾩궛
            int inSampleSize = 1;
            while (w / inSampleSize > maxDimension || h / inSampleSize > maxDimension) {
                inSampleSize *= 2;
            }

            // 3. 실제 디코드
            options.inJustDecodeBounds = false;
            options.inSampleSize = inSampleSize;
            options.inPreferredConfig = Bitmap.Config.ARGB_8888;

            Bitmap bitmap;
            try (InputStream in = resolver.openInputStream(uri)) {
                bitmap = BitmapFactory.decodeStream(in, null, options);
            }

            if (bitmap == null) return null;

            // 4. EXIF 회전 적용
            int orientation = getExifOrientation(context, uri);
            return applyExifOrientation(bitmap, orientation);
        } catch (Throwable t) {
            Log.e(TAG, "비트맵 로드 실패: " + t.getMessage(), t);
            return null;
        }
    }

    /**
     * 최종 저장용 고해상도 디코드 (OOM 방지를 위해 최대 4096px 이내로 안전하게 디코딩)
     */
    public static Bitmap decodeFullBitmapFromUri(Context context, Uri uri) {
        return decodeSampledBitmapFromUri(context, uri, 4096);
    }

    private static int getExifOrientation(Context context, Uri uri) {
        try (InputStream in = context.getContentResolver().openInputStream(uri)) {
            if (in == null) return ExifInterface.ORIENTATION_NORMAL;
            ExifInterface exif = new ExifInterface(in);
            return exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL);
        } catch (Throwable t) {
            return ExifInterface.ORIENTATION_NORMAL;
        }
    }

    private static Bitmap applyExifOrientation(Bitmap bitmap, int orientation) {
        if (orientation == ExifInterface.ORIENTATION_NORMAL || orientation == ExifInterface.ORIENTATION_UNDEFINED) {
            return bitmap;
        }

        Matrix matrix = new Matrix();
        switch (orientation) {
            case ExifInterface.ORIENTATION_ROTATE_90:
                matrix.postRotate(90);
                break;
            case ExifInterface.ORIENTATION_ROTATE_180:
                matrix.postRotate(180);
                break;
            case ExifInterface.ORIENTATION_ROTATE_270:
                matrix.postRotate(270);
                break;
            case ExifInterface.ORIENTATION_FLIP_HORIZONTAL:
                matrix.postScale(-1, 1);
                break;
            case ExifInterface.ORIENTATION_FLIP_VERTICAL:
                matrix.postScale(1, -1);
                break;
            default:
                return bitmap;
        }

        try {
            Bitmap rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);
            if (rotated != bitmap) {
                bitmap.recycle();
            }
            return rotated;
        } catch (Throwable t) {
            return bitmap;
        }
    }

    /**
     * 蹂댁젙??鍮꾪듃留듭쓣 媛ㅻ윭由ъ뿉 JPEG濡????     */
    public static Uri saveBitmapToGallery(Context context, Bitmap bitmap) {
        if (bitmap == null || bitmap.isRecycled()) return null;

        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
        String fileName = "Beauty_" + timeStamp + ".jpg";

        ContentValues values = new ContentValues();
        values.put(MediaStore.Images.Media.DISPLAY_NAME, fileName);
        values.put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg");
        values.put(MediaStore.Images.Media.DATE_ADDED, System.currentTimeMillis() / 1000);
        values.put(MediaStore.Images.Media.DATE_TAKEN, System.currentTimeMillis());

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            values.put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/QuickBeauty");
            values.put(MediaStore.Images.Media.IS_PENDING, 1);
        }

        ContentResolver resolver = context.getContentResolver();
        Uri imageUri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);

        if (imageUri == null) {
            return null;
        }

        try {
            try (OutputStream out = resolver.openOutputStream(imageUri)) {
                if (out == null) throw new IllegalStateException("異쒕젰 ?ㅽ듃由쇱쓣 ?????놁뒿?덈떎.");
                bitmap.compress(Bitmap.CompressFormat.JPEG, 96, out);
                out.flush();
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                values.clear();
                values.put(MediaStore.Images.Media.IS_PENDING, 0);
                resolver.update(imageUri, values, null, null);
            } else {
                MediaScannerConnection.scanFile(context, new String[]{imageUri.getPath()}, new String[]{"image/jpeg"}, null);
            }
            return imageUri;
        } catch (Throwable t) {
            Log.e(TAG, "媛ㅻ윭由?????ㅽ뙣: " + t.getMessage(), t);
            try {
                resolver.delete(imageUri, null, null);
            } catch (Throwable ignored) {}
            return null;
        }
    }
}
