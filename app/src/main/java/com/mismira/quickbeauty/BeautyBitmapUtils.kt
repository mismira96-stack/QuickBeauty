package com.quickbeauty.app

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object BeautyBitmapUtils {
    private const val TAG = "BeautyBitmapUtils"

    fun decodeSampledBitmapFromUri(context: Context, uri: Uri, maxDimension: Int): Bitmap? {
        return try {
            val resolver = context.contentResolver

            val options = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            resolver.openInputStream(uri)?.use { input ->
                BitmapFactory.decodeStream(input, null, options)
            }

            val w = options.outWidth
            val h = options.outHeight
            if (w <= 0 || h <= 0) return null

            var inSampleSize = 1
            while (w / inSampleSize > maxDimension || h / inSampleSize > maxDimension) {
                inSampleSize *= 2
            }

            options.inJustDecodeBounds = false
            options.inSampleSize = inSampleSize
            options.inPreferredConfig = Bitmap.Config.ARGB_8888

            val bitmap = resolver.openInputStream(uri)?.use { input ->
                BitmapFactory.decodeStream(input, null, options)
            } ?: return null

            val orientation = getExifOrientation(context, uri)
            applyExifOrientation(bitmap, orientation)
        } catch (t: Throwable) {
            Log.e(TAG, "비트맵 로드 실패: ${t.message}", t)
            null
        }
    }

    fun decodeFullBitmapFromUri(context: Context, uri: Uri): Bitmap? {
        val resolver = context.contentResolver
        // 1. 원본 해상도 100% 무손실 디코딩 (inSampleSize = 1)
        return try {
            val options = BitmapFactory.Options().apply {
                inJustDecodeBounds = false
                inSampleSize = 1
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            val bitmap = resolver.openInputStream(uri)?.use { input ->
                BitmapFactory.decodeStream(input, null, options)
            }
            if (bitmap != null) {
                val orientation = getExifOrientation(context, uri)
                applyExifOrientation(bitmap, orientation)
            } else {
                decodeSampledBitmapFromUri(context, uri, 8192)
            }
        } catch (oom: OutOfMemoryError) {
            Log.w(TAG, "원본 100% 로드 중 OOM 발생, 8K(8192px) 초고화질 샘플링으로 대체: ${oom.message}")
            decodeSampledBitmapFromUri(context, uri, 8192)
        } catch (t: Throwable) {
            Log.w(TAG, "원본 로드 예외, 8K 샘플링으로 대체: ${t.message}")
            decodeSampledBitmapFromUri(context, uri, 8192)
        }
    }

    private fun getExifOrientation(context: Context, uri: Uri): Int {
        return try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                val exif = ExifInterface(input)
                exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
            } ?: ExifInterface.ORIENTATION_NORMAL
        } catch (t: Throwable) {
            ExifInterface.ORIENTATION_NORMAL
        }
    }

    private fun applyExifOrientation(bitmap: Bitmap, orientation: Int): Bitmap {
        if (orientation == ExifInterface.ORIENTATION_NORMAL || orientation == ExifInterface.ORIENTATION_UNDEFINED) {
            return bitmap
        }

        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
            else -> return bitmap
        }

        return try {
            val rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
            if (rotated != bitmap) {
                bitmap.recycle()
            }
            rotated
        } catch (t: Throwable) {
            bitmap
        }
    }

    fun saveBitmapToGallery(context: Context, sourceUri: Uri?, bitmap: Bitmap?): Uri? {
        if (bitmap == null || bitmap.isRecycled) return null

        val isPng = sourceUri?.let { uri ->
            context.contentResolver.getType(uri)?.contains("png", ignoreCase = true) == true
        } ?: false

        val nowMs = System.currentTimeMillis()
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date(nowMs))
        val extension = if (isPng) "png" else "jpg"
        val mimeType = if (isPng) "image/png" else "image/jpeg"
        val fileName = "Beauty_$timeStamp.$extension"

        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
            put(MediaStore.Images.Media.MIME_TYPE, mimeType)
            put(MediaStore.Images.Media.DATE_ADDED, nowMs / 1000)
            put(MediaStore.Images.Media.DATE_TAKEN, nowMs)
            put(MediaStore.Images.Media.DATE_MODIFIED, nowMs / 1000)
            put(MediaStore.Images.Media.WIDTH, bitmap.width)
            put(MediaStore.Images.Media.HEIGHT, bitmap.height)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                // DCIM 폴더 아래 저장하여 삼성 갤러리 메인 앨범에 즉시 노출
                put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_DCIM + "/QuickBeauty")
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
        }

        val resolver = context.contentResolver
        val imageUri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return null

        return try {
            resolver.openOutputStream(imageUri)?.use { out ->
                if (isPng) {
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                } else {
                    // JPEG 품질 98: 눈으로 원본과 구별 불가능한 초고화질 무손실급 저장
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 98, out)
                }
                out.flush()
            }

            // 원본 사진의 카메라 기종, 렌즈 등 EXIF 복사 (촬영일시는 현재로 설정하여 최신 사진으로 정렬)
            if (!isPng && sourceUri != null) {
                copyExifMetadata(context, sourceUri, imageUri)
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                values.clear()
                values.put(MediaStore.Images.Media.IS_PENDING, 0)
                values.put(MediaStore.Images.Media.DATE_TAKEN, nowMs)
                values.put(MediaStore.Images.Media.DATE_ADDED, nowMs / 1000)
                values.put(MediaStore.Images.Media.DATE_MODIFIED, nowMs / 1000)
                resolver.update(imageUri, values, null, null)
            }

            // 갤러리 색인 즉시 갱신 (삼성 갤러리 최신 사진 탭에 즉각 반영)
            try {
                val projection = arrayOf(MediaStore.Images.Media.DATA)
                val filePath = resolver.query(imageUri, projection, null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) cursor.getString(0) else null
                }
                if (filePath != null) {
                    MediaScannerConnection.scanFile(context, arrayOf(filePath), arrayOf(mimeType), null)
                }
            } catch (_: Throwable) {}

            imageUri
        } catch (t: Throwable) {
            Log.e(TAG, "갤러리 저장 실패: ${t.message}", t)
            try {
                resolver.delete(imageUri, null, null)
            } catch (_: Throwable) {}
            null
        }
    }

    // 하위 호환용 오버로드
    fun saveBitmapToGallery(context: Context, bitmap: Bitmap?): Uri? {
        return saveBitmapToGallery(context, null, bitmap)
    }

    private fun copyExifMetadata(context: Context, sourceUri: Uri, targetUri: Uri) {
        try {
            val resolver = context.contentResolver

            val srcExif = try {
                resolver.openFileDescriptor(sourceUri, "r")?.use { pfd ->
                    ExifInterface(pfd.fileDescriptor)
                }
            } catch (_: Throwable) {
                null
            } ?: resolver.openInputStream(sourceUri)?.use { input ->
                ExifInterface(input)
            }

            resolver.openFileDescriptor(targetUri, "rw")?.use { pfd ->
                val dstExif = ExifInterface(pfd.fileDescriptor)

                if (srcExif != null) {
                    val tagsToCopy = arrayOf(
                        ExifInterface.TAG_MAKE,
                        ExifInterface.TAG_MODEL,
                        ExifInterface.TAG_F_NUMBER,
                        ExifInterface.TAG_EXPOSURE_TIME,
                        ExifInterface.TAG_ISO_SPEED_RATINGS,
                        ExifInterface.TAG_FOCAL_LENGTH,
                        ExifInterface.TAG_FOCAL_LENGTH_IN_35MM_FILM,
                        ExifInterface.TAG_WHITE_BALANCE,
                        ExifInterface.TAG_FLASH,
                        ExifInterface.TAG_COLOR_SPACE
                    )

                    for (tag in tagsToCopy) {
                        val value = srcExif.getAttribute(tag)
                        if (value != null) {
                            dstExif.setAttribute(tag, value)
                        }
                    }

                }

                // 갤러리 타임라인 최상단(오늘/방금 전 최신 사진)에 즉시 정렬되도록 날짜는 현재 시각으로 설정
                val nowFormat = SimpleDateFormat("yyyy:MM:dd HH:mm:ss", Locale.getDefault()).format(Date())
                dstExif.setAttribute(ExifInterface.TAG_DATETIME, nowFormat)
                dstExif.setAttribute(ExifInterface.TAG_DATETIME_ORIGINAL, nowFormat)
                dstExif.setAttribute(ExifInterface.TAG_DATETIME_DIGITIZED, nowFormat)

                // 이미지는 디코딩 시 정방향으로 회전 완료되었으므로 ORIENTATION_NORMAL로 고정
                dstExif.setAttribute(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL.toString())
                dstExif.saveAttributes()
            }
        } catch (t: Throwable) {
            Log.w(TAG, "EXIF 메타데이터 복사 건너뜀: ${t.message}")
        }
    }

}

