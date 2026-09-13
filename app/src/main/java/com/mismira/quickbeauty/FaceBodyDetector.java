package com.mismira.quickbeauty;

import android.graphics.Bitmap;
import android.graphics.PointF;
import android.graphics.Rect;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.face.Face;
import com.google.mlkit.vision.face.FaceContour;
import com.google.mlkit.vision.face.FaceDetection;
import com.google.mlkit.vision.face.FaceDetector;
import com.google.mlkit.vision.face.FaceDetectorOptions;
import com.google.mlkit.vision.face.FaceLandmark;
import com.google.mlkit.vision.pose.Pose;
import com.google.mlkit.vision.pose.PoseDetection;
import com.google.mlkit.vision.pose.PoseDetector;
import com.google.mlkit.vision.pose.PoseLandmark;
import com.google.mlkit.vision.pose.defaults.PoseDetectorOptions;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * ML Kit瑜??댁슜???쇨뎬 諛??좎껜 ?쒕뱶留덊겕瑜?異붿텧?섎뒗 ?먯?湲? */
public class FaceBodyDetector {
    private static final String TAG = "FaceBodyDetector";

    public interface Callback {
        void onDetected(BeautyLandmarks landmarks);
    }

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private FaceDetector faceDetector;
    private PoseDetector poseDetector;

    public FaceBodyDetector() {
        try {
            FaceDetectorOptions faceOptions = new FaceDetectorOptions.Builder()
                    .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
                    .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL)
                    .setContourMode(FaceDetectorOptions.CONTOUR_MODE_ALL)
                    .build();
            faceDetector = FaceDetection.getClient(faceOptions);

            PoseDetectorOptions poseOptions = new PoseDetectorOptions.Builder()
                    .setDetectorMode(PoseDetectorOptions.SINGLE_IMAGE_MODE)
                    .build();
            poseDetector = PoseDetection.getClient(poseOptions);
        } catch (Throwable t) {
            Log.e(TAG, "ML Kit 珥덇린??以??ㅻ쪟 諛쒖깮 (湲곕낯媛??대갚 ?ъ슜): " + t.getMessage(), t);
        }
    }

    public void detect(Bitmap bitmap, Callback callback) {
        if (bitmap == null || bitmap.isRecycled()) {
            BeautyLandmarks empty = new BeautyLandmarks(100, 100);
            empty.setupDefaultsIfEmpty();
            mainHandler.post(() -> callback.onDetected(empty));
            return;
        }

        final int w = bitmap.getWidth();
        final int h = bitmap.getHeight();
        final BeautyLandmarks landmarks = new BeautyLandmarks(w, h);

        executor.execute(() -> {
            try {
                InputImage image = InputImage.fromBitmap(bitmap, 0);

                Task<List<Face>> faceTask = (faceDetector != null) ? faceDetector.process(image) : Tasks.forResult(null);
                Task<Pose> poseTask = (poseDetector != null) ? poseDetector.process(image) : Tasks.forResult(null);

                Tasks.whenAllComplete(faceTask, poseTask).addOnCompleteListener(task -> {
                    // 1. ?쇨뎬 寃곌낵 ?뚯떛
                    try {
                        if (faceTask.isSuccessful() && faceTask.getResult() != null && !faceTask.getResult().isEmpty()) {
                            // 媛?????쇨뎬 ?좏깮
                            Face primaryFace = faceTask.getResult().get(0);
                            for (Face f : faceTask.getResult()) {
                                if (f.getBoundingBox().width() * f.getBoundingBox().height() >
                                        primaryFace.getBoundingBox().width() * primaryFace.getBoundingBox().height()) {
                                    primaryFace = f;
                                }
                            }
                            parseFace(primaryFace, landmarks);
                        }
                    } catch (Throwable e) {
                        Log.w(TAG, "?쇨뎬 遺꾩꽍 以??덉쇅: " + e.getMessage());
                    }

                    // 2. ?ъ쫰 寃곌낵 ?뚯떛
                    try {
                        if (poseTask.isSuccessful() && poseTask.getResult() != null) {
                            parsePose(poseTask.getResult(), landmarks, w, h);
                        }
                    } catch (Throwable e) {
                        Log.w(TAG, "?ъ쫰 遺꾩꽍 以??덉쇅: " + e.getMessage());
                    }

                    // 鍮?媛믪? 湲곕낯 鍮꾩쑉濡?蹂댁셿
                    landmarks.setupDefaultsIfEmpty();

                    mainHandler.post(() -> callback.onDetected(landmarks));
                });
            } catch (Throwable t) {
                Log.e(TAG, "?먯? ?뚯씠?꾨씪???먮윭: " + t.getMessage(), t);
                landmarks.setupDefaultsIfEmpty();
                mainHandler.post(() -> callback.onDetected(landmarks));
            }
        });
    }

    private void parseFace(Face face, BeautyLandmarks landmarks) {
        landmarks.hasFace = true;
        Rect box = face.getBoundingBox();
        landmarks.faceBounds.set(box.left, box.top, box.right, box.bottom);
        landmarks.faceCenter.set(box.centerX(), box.centerY());

        // ????醫뚰몴
        FaceContour chinContour = face.getContour(FaceContour.FACE);
        if (chinContour != null && !chinContour.getPoints().isEmpty()) {
            List<PointF> points = chinContour.getPoints();
            // FACE ?ㅺ낸?좎쓽 理쒗븯???ъ씤?몃뱾?????쇱씤?쇰줈 異붿텧
            PointF lowest = points.get(0);
            for (PointF pt : points) {
                if (pt.y > lowest.y) {
                    lowest = pt;
                }
            }
            landmarks.chinPoint.set(lowest.x, lowest.y);

            // 좌우 볼 턱선 포인트
            int total = points.size();
            PointF lj = points.get(Math.max(0, (int)(total * 0.25f)));
            landmarks.leftJaw.set(lj.x, lj.y);
            PointF rj = points.get(Math.min(total - 1, (int)(total * 0.75f)));
            landmarks.rightJaw.set(rj.x, rj.y);
        } else {
            landmarks.chinPoint.set(box.centerX(), box.bottom);
            landmarks.leftJaw.set(box.left + box.width() * 0.15f, box.centerY() + box.height() * 0.25f);
            landmarks.rightJaw.set(box.right - box.width() * 0.15f, box.centerY() + box.height() * 0.25f);
        }

        // 蹂??쒕뱶留덊겕
        FaceLandmark leftCheek = face.getLandmark(FaceLandmark.LEFT_CHEEK);
        FaceLandmark rightCheek = face.getLandmark(FaceLandmark.RIGHT_CHEEK);
        if (leftCheek != null) {
            landmarks.leftCheek.set(leftCheek.getPosition().x, leftCheek.getPosition().y);
        } else {
            landmarks.leftCheek.set(box.left + box.width() * 0.2f, box.centerY());
        }
        if (rightCheek != null) {
            landmarks.rightCheek.set(rightCheek.getPosition().x, rightCheek.getPosition().y);
        } else {
            landmarks.rightCheek.set(box.right - box.width() * 0.2f, box.centerY());
        }
    }

    private void parsePose(Pose pose, BeautyLandmarks landmarks, int width, int height) {
        PoseLandmark leftShoulder = pose.getPoseLandmark(PoseLandmark.LEFT_SHOULDER);
        PoseLandmark rightShoulder = pose.getPoseLandmark(PoseLandmark.RIGHT_SHOULDER);
        PoseLandmark leftHip = pose.getPoseLandmark(PoseLandmark.LEFT_HIP);
        PoseLandmark rightHip = pose.getPoseLandmark(PoseLandmark.RIGHT_HIP);

        if (leftShoulder != null && rightShoulder != null) {
            landmarks.hasBody = true;
            landmarks.leftShoulder.set(leftShoulder.getPosition().x, leftShoulder.getPosition().y);
            landmarks.rightShoulder.set(rightShoulder.getPosition().x, rightShoulder.getPosition().y);

            float topY = Math.min(leftShoulder.getPosition().y, rightShoulder.getPosition().y);
            landmarks.bodyTopY = topY;

            if (leftHip != null && rightHip != null) {
                landmarks.leftHip.set(leftHip.getPosition().x, leftHip.getPosition().y);
                landmarks.rightHip.set(rightHip.getPosition().x, rightHip.getPosition().y);
                landmarks.bodyBottomY = Math.max(leftHip.getPosition().y, rightHip.getPosition().y) + height * 0.15f;
            } else {
                landmarks.bodyBottomY = topY + height * 0.45f;
            }
            landmarks.bodyCenterY = (landmarks.bodyTopY + landmarks.bodyBottomY) * 0.5f;

            float minX = Math.min(landmarks.leftShoulder.x, landmarks.rightShoulder.x);
            float maxX = Math.max(landmarks.leftShoulder.x, landmarks.rightShoulder.x);
            landmarks.bodyBounds.set(minX - width * 0.1f, landmarks.bodyTopY, maxX + width * 0.1f, landmarks.bodyBottomY);
        }
    }

    public void release() {
        if (faceDetector != null) {
            faceDetector.close();
        }
        if (poseDetector != null) {
            poseDetector.close();
        }
        executor.shutdown();
    }
}
