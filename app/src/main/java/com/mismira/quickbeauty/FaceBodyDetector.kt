package com.quickbeauty.app

import android.graphics.Bitmap
import android.graphics.PointF
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceContour
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetector
import com.google.mlkit.vision.face.FaceDetectorOptions
import com.google.mlkit.vision.face.FaceLandmark
import com.google.mlkit.vision.pose.Pose
import com.google.mlkit.vision.pose.PoseDetection
import com.google.mlkit.vision.pose.PoseDetector
import com.google.mlkit.vision.pose.PoseLandmark
import com.google.mlkit.vision.pose.defaults.PoseDetectorOptions
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.math.max
import kotlin.math.min

/**
 * ML Kit 기반 얼굴 및 신체 랜드마크 추출 엔진
 */
class FaceBodyDetector {
    companion object {
        private const val TAG = "FaceBodyDetector"
    }

    fun interface Callback {
        fun onDetected(landmarks: BeautyLandmarks)
    }

    private val executor: ExecutorService = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())

    private var faceDetector: FaceDetector? = null
    private var poseDetector: PoseDetector? = null

    init {
        try {
            val faceOptions = FaceDetectorOptions.Builder()
                .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
                .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL)
                .setContourMode(FaceDetectorOptions.CONTOUR_MODE_ALL)
                .build()
            faceDetector = FaceDetection.getClient(faceOptions)

            val poseOptions = PoseDetectorOptions.Builder()
                .setDetectorMode(PoseDetectorOptions.SINGLE_IMAGE_MODE)
                .build()
            poseDetector = PoseDetection.getClient(poseOptions)
        } catch (t: Throwable) {
            Log.e(TAG, "ML Kit 초기화 중 오류: ${t.message}", t)
        }
    }

    fun detect(bitmap: Bitmap?, callback: Callback) {
        if (bitmap == null || bitmap.isRecycled) {
            val empty = BeautyLandmarks(100, 100).apply { setupDefaultsIfEmpty() }
            mainHandler.post { callback.onDetected(empty) }
            return
        }

        val w = bitmap.width
        val h = bitmap.height
        val landmarks = BeautyLandmarks(w, h)

        executor.execute {
            try {
                val image = InputImage.fromBitmap(bitmap, 0)

                val faceTask: Task<List<Face>> = faceDetector?.process(image) ?: Tasks.forResult(emptyList())
                val poseTask: Task<Pose> = poseDetector?.process(image) ?: Tasks.forResult(null)

                Tasks.whenAllComplete(faceTask, poseTask).addOnCompleteListener {
                    // 1. 얼굴 분석 파싱
                    try {
                        if (faceTask.isSuccessful && !faceTask.result.isNullOrEmpty()) {
                            val faces = faceTask.result!!
                            var primaryFace = faces[0]
                            for (f in faces) {
                                if (f.boundingBox.width() * f.boundingBox.height() >
                                    primaryFace.boundingBox.width() * primaryFace.boundingBox.height()
                                ) {
                                    primaryFace = f
                                }
                            }
                            parseFace(primaryFace, landmarks)
                        }
                    } catch (e: Throwable) {
                        Log.w(TAG, "얼굴 분석 중 예외: ${e.message}")
                    }

                    // 2. 포즈 분석 파싱
                    try {
                        if (poseTask.isSuccessful && poseTask.result != null) {
                            parsePose(poseTask.result!!, landmarks, w, h)
                        }
                    } catch (e: Throwable) {
                        Log.w(TAG, "포즈 분석 중 예외: ${e.message}")
                    }

                    // 감지 누락된 영역 기본 인체 비율로 보완
                    landmarks.setupDefaultsIfEmpty()

                    mainHandler.post { callback.onDetected(landmarks) }
                }
            } catch (t: Throwable) {
                Log.e(TAG, "감지 파이프라인 오류: ${t.message}", t)
                landmarks.setupDefaultsIfEmpty()
                mainHandler.post { callback.onDetected(landmarks) }
            }
        }
    }

    private fun parseFace(face: Face, landmarks: BeautyLandmarks) {
        landmarks.hasFace = true
        val box = face.boundingBox
        landmarks.faceBounds.set(box.left.toFloat(), box.top.toFloat(), box.right.toFloat(), box.bottom.toFloat())
        landmarks.faceCenter.set(box.exactCenterX(), box.exactCenterY())

        val chinContour = face.getContour(FaceContour.FACE)
        if (chinContour != null && chinContour.points.isNotEmpty()) {
            val points = chinContour.points
            var lowest = points[0]
            for (pt in points) {
                if (pt.y > lowest.y) {
                    lowest = pt
                }
            }
            landmarks.chinPoint.set(lowest.x, lowest.y)

            val total = points.size
            val lj = points[max(0, (total * 0.25f).toInt())]
            landmarks.leftJaw.set(lj.x, lj.y)
            val rj = points[min(total - 1, (total * 0.75f).toInt())]
            landmarks.rightJaw.set(rj.x, rj.y)
        } else {
            landmarks.chinPoint.set(box.exactCenterX(), box.bottom.toFloat())
            landmarks.leftJaw.set(box.left + box.width() * 0.15f, box.exactCenterY() + box.height() * 0.25f)
            landmarks.rightJaw.set(box.right - box.width() * 0.15f, box.exactCenterY() + box.height() * 0.25f)
        }

        val leftCheek = face.getLandmark(FaceLandmark.LEFT_CHEEK)
        val rightCheek = face.getLandmark(FaceLandmark.RIGHT_CHEEK)
        if (leftCheek != null) {
            landmarks.leftCheek.set(leftCheek.position.x, leftCheek.position.y)
        } else {
            landmarks.leftCheek.set(box.left + box.width() * 0.2f, box.exactCenterY())
        }
        if (rightCheek != null) {
            landmarks.rightCheek.set(rightCheek.position.x, rightCheek.position.y)
        } else {
            landmarks.rightCheek.set(box.right - box.width() * 0.2f, box.exactCenterY())
        }

        val mouthBottom = face.getLandmark(FaceLandmark.MOUTH_BOTTOM)
        if (mouthBottom != null) {
            landmarks.mouthPoint.set(mouthBottom.position.x, mouthBottom.position.y)
        } else {
            landmarks.mouthPoint.set(box.exactCenterX(), box.top + box.height() * 0.72f)
        }

        val noseBase = face.getLandmark(FaceLandmark.NOSE_BASE)
        if (noseBase != null) {
            landmarks.noseBase.set(noseBase.position.x, noseBase.position.y)
        } else {
            landmarks.noseBase.set(box.exactCenterX(), box.top + box.height() * 0.55f)
        }
    }

    private fun parsePose(pose: Pose, landmarks: BeautyLandmarks, width: Int, height: Int) {
        val leftShoulder = pose.getPoseLandmark(PoseLandmark.LEFT_SHOULDER)
        val rightShoulder = pose.getPoseLandmark(PoseLandmark.RIGHT_SHOULDER)
        val leftHip = pose.getPoseLandmark(PoseLandmark.LEFT_HIP)
        val rightHip = pose.getPoseLandmark(PoseLandmark.RIGHT_HIP)

        if (leftShoulder != null && rightShoulder != null) {
            landmarks.hasBody = true
            landmarks.leftShoulder.set(leftShoulder.position.x, leftShoulder.position.y)
            landmarks.rightShoulder.set(rightShoulder.position.x, rightShoulder.position.y)

            val topY = min(leftShoulder.position.y, rightShoulder.position.y)
            landmarks.bodyTopY = topY

            if (leftHip != null && rightHip != null) {
                landmarks.leftHip.set(leftHip.position.x, leftHip.position.y)
                landmarks.rightHip.set(rightHip.position.x, rightHip.position.y)
                landmarks.bodyBottomY = max(leftHip.position.y, rightHip.position.y) + height * 0.15f
            } else {
                landmarks.bodyBottomY = topY + height * 0.45f
            }
            landmarks.bodyCenterY = (landmarks.bodyTopY + landmarks.bodyBottomY) * 0.5f

            val minX = min(landmarks.leftShoulder.x, landmarks.rightShoulder.x)
            val maxX = max(landmarks.leftShoulder.x, landmarks.rightShoulder.x)
            landmarks.bodyBounds.set(minX - width * 0.1f, landmarks.bodyTopY, maxX + width * 0.1f, landmarks.bodyBottomY)
        }
    }

    fun release() {
        faceDetector?.close()
        poseDetector?.close()
        executor.shutdown()
    }
}

