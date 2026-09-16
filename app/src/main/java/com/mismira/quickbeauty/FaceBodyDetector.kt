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

    var lastDetectedPose: Pose? = null
        private set

    init {
        try {
            val faceOptions = FaceDetectorOptions.Builder()
                .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE)
                .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL)
                .setContourMode(FaceDetectorOptions.CONTOUR_MODE_ALL)
                .setMinFaceSize(0.05f)
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
                    val poseResult = if (poseTask.isSuccessful) poseTask.result else null

                    // 1. 다중 얼굴 분석 및 스마트 스코어링
                    try {
                        if (faceTask.isSuccessful && !faceTask.result.isNullOrEmpty()) {
                            val faces = faceTask.result!!
                            val centerX = w * 0.5f
                            val centerY = h * 0.40f
                            val maxDist = kotlin.math.sqrt(centerX * centerX + centerY * centerY)
                            var maxArea = 1f
                            for (f in faces) {
                                val area = f.boundingBox.width().toFloat() * f.boundingBox.height().toFloat()
                                if (area > maxArea) maxArea = area
                            }

                            val faceInfoList = mutableListOf<BeautyLandmarks.FaceInfo>()
                            for ((idx, f) in faces.withIndex()) {
                                val info = createFaceInfo(f, idx)
                                val area = f.boundingBox.width().toFloat() * f.boundingBox.height().toFloat()
                                val dx = f.boundingBox.exactCenterX() - centerX
                                val dy = f.boundingBox.exactCenterY() - centerY
                                val dist = kotlin.math.sqrt(dx * dx + dy * dy)
                                val normArea = (area / maxArea).coerceIn(0f, 1f)
                                val normCenter = (1.0f - (dist / maxDist)).coerceIn(0f, 1f)
                                // 스코어링: 얼굴 크기 65% + 중앙 근접도 35%
                                info.score = normArea * 0.65f + normCenter * 0.35f
                                faceInfoList.add(info)
                            }

                            // 점수 높은 순으로 정렬하여 0순위 기본 메인 인물 선정
                            faceInfoList.sortByDescending { it.score }
                            faceInfoList.forEachIndexed { i, info -> info.index = i }

                            landmarks.allFaces.clear()
                            landmarks.allFaces.addAll(faceInfoList)
                            landmarks.selectedFaceIndex = 0

                            val primary = faceInfoList[0]
                            landmarks.applyFaceInfo(primary)
                        }
                    } catch (e: Throwable) {
                        Log.w(TAG, "얼굴 분석 중 예외: ${e.message}")
                    }

                    lastDetectedPose = poseResult

                    // 2. 포즈 분석 파싱 (키메라 방지: 선택된 얼굴과 일치하는 경우에만 바인딩)
                    try {
                        if (poseResult != null && landmarks.hasFace) {
                            parsePoseIfMatched(poseResult, landmarks, w, h)
                        }
                    } catch (e: Throwable) {
                        Log.w(TAG, "포즈 분석 중 예외: ${e.message}")
                    }

                    // 감지 누락된 영역 인체 비율로 안전 보완 (소형 인물 과왜곡 방지)
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

    /**
     * 사용자가 다른 얼굴을 탭했을 때 활성 랜드마크 즉시 재바인딩
     * 보관된 포즈(lastDetectedPose)를 전달하여 대상 인물과의 포즈 매칭을 재수행
     */
    fun switchToFace(landmarks: BeautyLandmarks, targetIndex: Int, pose: Pose? = lastDetectedPose) {
        if (targetIndex !in landmarks.allFaces.indices) return
        landmarks.selectedFaceIndex = targetIndex
        val targetFace = landmarks.allFaces[targetIndex]
        landmarks.applyFaceInfo(targetFace)

        landmarks.hasBody = false
        if (pose != null) {
            parsePoseIfMatched(pose, landmarks, landmarks.imageWidth, landmarks.imageHeight)
        }
        landmarks.setupDefaultsIfEmpty()
    }

    private fun createFaceInfo(face: Face, originalIndex: Int): BeautyLandmarks.FaceInfo {
        val info = BeautyLandmarks.FaceInfo(index = originalIndex)
        val box = face.boundingBox
        info.bounds.set(box.left.toFloat(), box.top.toFloat(), box.right.toFloat(), box.bottom.toFloat())
        info.center.set(box.exactCenterX(), box.exactCenterY())

        val chinContour = face.getContour(FaceContour.FACE)
        if (chinContour != null && chinContour.points.isNotEmpty()) {
            val points = chinContour.points
            var lowest = points[0]
            for (pt in points) {
                if (pt.y > lowest.y) lowest = pt
            }
            info.chinPoint.set(lowest.x, lowest.y)

            val total = points.size
            val lj = points[max(0, (total * 0.25f).toInt())]
            info.leftJaw.set(lj.x, lj.y)
            val rj = points[min(total - 1, (total * 0.75f).toInt())]
            info.rightJaw.set(rj.x, rj.y)
        } else {
            info.chinPoint.set(box.exactCenterX(), box.bottom.toFloat())
            info.leftJaw.set(box.left + box.width() * 0.15f, box.exactCenterY() + box.height() * 0.25f)
            info.rightJaw.set(box.right - box.width() * 0.15f, box.exactCenterY() + box.height() * 0.25f)
        }

        val leftCheek = face.getLandmark(FaceLandmark.LEFT_CHEEK)
        val rightCheek = face.getLandmark(FaceLandmark.RIGHT_CHEEK)
        info.leftCheek.set(leftCheek?.position?.x ?: (box.left + box.width() * 0.2f), leftCheek?.position?.y ?: box.exactCenterY())
        info.rightCheek.set(rightCheek?.position?.x ?: (box.right - box.width() * 0.2f), rightCheek?.position?.y ?: box.exactCenterY())

        val mouthBottom = face.getLandmark(FaceLandmark.MOUTH_BOTTOM)
        info.mouthPoint.set(mouthBottom?.position?.x ?: box.exactCenterX(), mouthBottom?.position?.y ?: (box.top + box.height() * 0.72f))

        val noseBase = face.getLandmark(FaceLandmark.NOSE_BASE)
        info.noseBase.set(noseBase?.position?.x ?: box.exactCenterX(), noseBase?.position?.y ?: (box.top + box.height() * 0.55f))
        return info
    }

    private fun parsePoseIfMatched(pose: Pose, landmarks: BeautyLandmarks, width: Int, height: Int) {
        val leftShoulder = pose.getPoseLandmark(PoseLandmark.LEFT_SHOULDER)
        val rightShoulder = pose.getPoseLandmark(PoseLandmark.RIGHT_SHOULDER)
        if (leftShoulder == null || rightShoulder == null) return

        val shoulderMidX = (leftShoulder.position.x + rightShoulder.position.x) * 0.5f
        val shoulderMidY = (leftShoulder.position.y + rightShoulder.position.y) * 0.5f

        val fc = landmarks.faceCenter
        val fh = max(20f, landmarks.faceBounds.height())
        val fw = max(20f, landmarks.faceBounds.width())

        // 1. 키메라 판정: 어깨 중심이 얼굴 중심에서 X축으로 fw * 2.0 이내, Y축으로 fh * 3.8 이내 (얼굴 아래)에 있어야 같은 사람!
        // 전신/원거리 사진에서 어깨가 얼굴 높이보다 3배 이상 아래에 있는 정상 체형도 버려지지 않도록 3.8f로 여유 확보
        val dx = kotlin.math.abs(shoulderMidX - fc.x)
        val dy = shoulderMidY - fc.y
        val isMatched = dx <= (fw * 2.0f) && dy in (fh * 0.15f)..(fh * 3.8f)

        if (!isMatched) {
            Log.d(TAG, "Pose와 Face 불일치 감지: 키메라 방지를 위해 포즈 바인딩 건너뜀 (dx=$dx, dy=$dy)")
            return
        }

        // 2. 어깨 너비 이상치 방어 (아기/소형 인물인데 어깨가 지나치게 넓게 잡히는 오류 방지)
        val rawSpan = kotlin.math.abs(rightShoulder.position.x - leftShoulder.position.x)
        if (rawSpan > fw * 4.2f || rawSpan < fw * 0.6f) {
            return
        }

        landmarks.hasBody = true
        landmarks.leftShoulder.set(leftShoulder.position.x, leftShoulder.position.y)
        landmarks.rightShoulder.set(rightShoulder.position.x, rightShoulder.position.y)

        val topY = min(leftShoulder.position.y, rightShoulder.position.y)
        landmarks.bodyTopY = topY

        val leftHip = pose.getPoseLandmark(PoseLandmark.LEFT_HIP)
        val rightHip = pose.getPoseLandmark(PoseLandmark.RIGHT_HIP)
        if (leftHip != null && rightHip != null && rightHip.position.y > topY) {
            landmarks.leftHip.set(leftHip.position.x, leftHip.position.y)
            landmarks.rightHip.set(rightHip.position.x, rightHip.position.y)
            val maxHipY = max(leftHip.position.y, rightHip.position.y)
            // 엉덩이 아래로 무한정 늘어지지 않도록 얼굴 높이 fh * 3.0f까지만 하단 제한 (다리 번짐 원천 차단)
            landmarks.bodyBottomY = min(maxHipY + fh * 0.6f, topY + fh * 3.0f)
        } else {
            landmarks.bodyBottomY = min(height.toFloat(), topY + fh * 2.5f)
        }
        landmarks.bodyCenterY = (landmarks.bodyTopY + landmarks.bodyBottomY) * 0.5f

        val minX = min(landmarks.leftShoulder.x, landmarks.rightShoulder.x)
        val maxX = max(landmarks.leftShoulder.x, landmarks.rightShoulder.x)
        landmarks.bodyBounds.set(minX - fw * 0.2f, landmarks.bodyTopY, maxX + fw * 0.2f, landmarks.bodyBottomY)
    }

    fun release() {
        faceDetector?.close()
        poseDetector?.close()
        executor.shutdown()
    }
}

