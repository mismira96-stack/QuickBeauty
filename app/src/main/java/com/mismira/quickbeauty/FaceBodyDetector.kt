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
    private var contourDetector: FaceDetector? = null
    private var poseDetector: PoseDetector? = null

    var lastDetectedPose: Pose? = null
        private set

    init {
        try {
            val faceOptions = FaceDetectorOptions.Builder()
                .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE)
                .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL)
                // 윤곽선 모드를 켜면 가장 뚜렷한 한 얼굴만 반환되므로 다중 얼굴 검출에서는 끈다.
                .setContourMode(FaceDetectorOptions.CONTOUR_MODE_NONE)
                .setMinFaceSize(0.04f)
                .build()
            faceDetector = FaceDetection.getClient(faceOptions)

            // 대표 얼굴의 턱 윤곽은 별도로 얻어 기존 얼굴 길이 보정 품질을 유지한다.
            contourDetector = FaceDetection.getClient(FaceDetectorOptions.Builder()
                .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
                .setContourMode(FaceDetectorOptions.CONTOUR_MODE_ALL)
                .setMinFaceSize(0.04f)
                .build())

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
            val empty = BeautyLandmarks(0, 0)
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
                val contourTask: Task<List<Face>> = contourDetector?.process(image) ?: Tasks.forResult(emptyList())
                val poseTask: Task<Pose> = poseDetector?.process(image) ?: Tasks.forResult(null)

                Tasks.whenAllComplete(faceTask, contourTask, poseTask).addOnCompleteListener {
                    val poseResult = if (poseTask.isSuccessful) poseTask.result else null
                    val contourFace = if (contourTask.isSuccessful) contourTask.result?.firstOrNull() else null

                    // 1. 다중 얼굴 분석 및 스마트 스코어링
                    try {
                        val faces = if (faceTask.isSuccessful) {
                            faceTask.result.orEmpty().filter { isSelectableFace(it, w, h) }
                        } else emptyList()
                        if (faces.isNotEmpty()) {
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
                                val matchedContour = contourFace?.takeIf { contourMatches(f, it) }
                                val info = createFaceInfo(f, idx, matchedContour)
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
                            landmarks.applyFaceInfo(faceInfoList[0])
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
        landmarks.hasMatchedPose = false
        if (pose != null) {
            parsePoseIfMatched(pose, landmarks, landmarks.imageWidth, landmarks.imageHeight)
        }
        landmarks.setupDefaultsIfEmpty()
    }

    private fun contourMatches(face: Face, contourFace: Face): Boolean {
        val a = face.boundingBox
        val b = contourFace.boundingBox
        val overlapW = max(0, min(a.right, b.right) - max(a.left, b.left))
        val overlapH = max(0, min(a.bottom, b.bottom) - max(a.top, b.top))
        val overlap = overlapW.toFloat() * overlapH
        val smallerArea = min(a.width() * a.height(), b.width() * b.height()).toFloat()
        return smallerArea > 0f && overlap / smallerArea > 0.5f
    }

    private fun isSelectableFace(face: Face, imageWidth: Int, imageHeight: Int): Boolean {
        val box = face.boundingBox
        // 갤러리 캡처의 하단 썸네일처럼 작은 얼굴은 편집 대상에서 제외한다.
        return box.width() >= max(60f, imageWidth * 0.035f) &&
            box.height() >= max(60f, imageHeight * 0.035f)
    }

    private fun createFaceInfo(face: Face, originalIndex: Int, contourFace: Face?): BeautyLandmarks.FaceInfo {
        val info = BeautyLandmarks.FaceInfo(index = originalIndex)
        val box = face.boundingBox
        info.bounds.set(box.left.toFloat(), box.top.toFloat(), box.right.toFloat(), box.bottom.toFloat())
        info.center.set(box.exactCenterX(), box.exactCenterY())

        val chinContour = contourFace?.getContour(FaceContour.FACE)
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

        // 2. 얼굴이 작고 상체가 큰 사진은 허용하되, 어깨가 얼굴을 감싸지 않으면 다른 사람의 포즈로 본다.
        val rawSpan = kotlin.math.abs(rightShoulder.position.x - leftShoulder.position.x)
        val shoulderLeftX = min(leftShoulder.position.x, rightShoulder.position.x)
        val shoulderRightX = max(leftShoulder.position.x, rightShoulder.position.x)
        if (rawSpan > fw * 6.0f || rawSpan < fw * 0.6f || fc.x !in shoulderLeftX..shoulderRightX) {
            return
        }

        landmarks.hasBody = true
        landmarks.hasMatchedPose = true
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
        contourDetector?.close()
        poseDetector?.close()
        executor.shutdown()
    }
}

