package com.quickbeauty.app

import android.graphics.Bitmap
import android.graphics.Rect
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import java.util.concurrent.TimeUnit

/** Called on a worker once; panning is still available if detection misses or crop cannot fit a face. */
object StoryCropFocus {
    fun find(bitmap: Bitmap): StoryPhotoPosition {
        val detector=FaceDetection.getClient(FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST).build())
        return try {
            val faces=Tasks.await(detector.process(InputImage.fromBitmap(bitmap,0)),3,TimeUnit.SECONDS)
            if(faces.isEmpty()) StoryPhotoPosition(.5f,.25f) else {
                val all=Rect(faces.first().boundingBox)
                faces.drop(1).forEach { all.union(it.boundingBox) }
                StoryLayout.focusCrop(bitmap.width,bitmap.height,all.exactCenterX()/bitmap.width,all.exactCenterY()/bitmap.height)
            }
        } catch(_: Exception) { StoryPhotoPosition(.5f,.25f) }
        finally { detector.close() }
    }
}
