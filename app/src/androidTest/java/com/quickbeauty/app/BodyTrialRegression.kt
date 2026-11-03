package com.quickbeauty.app

import android.app.Instrumentation
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Bundle
import com.google.mlkit.vision.pose.PoseLandmark
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Explicit local development fixture only. Never writes to Gallery or logs in production. */
internal object BodyTrialRegression {
    fun run(i: Instrumentation, args: Bundle): String {
        val bitmap = checkNotNull(BitmapFactory.decodeFile(args.getString("input")))
        val detector = FaceBodyDetector()
        try {
            val latch = CountDownLatch(1)
            var result: BeautyLandmarks? = null
            detector.detect(bitmap) { result = it; latch.countDown() }
            check(latch.await(90, TimeUnit.SECONDS)) { "Detection timeout" }
            val lm = checkNotNull(result)
            val diagnostics = buildString {
                append("image=${bitmap.width}x${bitmap.height} faces=${lm.allFaces.size} bodies=${lm.bodiesByFaceIndex.keys}\n")
                for (f in lm.allFaces) append("face ${f.index}: ${f.bounds} nose=${f.noseBase} body=${lm.bodiesByFaceIndex[f.index]}\n")
                for (type in listOf(PoseLandmark.NOSE, PoseLandmark.LEFT_SHOULDER, PoseLandmark.RIGHT_SHOULDER, PoseLandmark.LEFT_HIP, PoseLandmark.RIGHT_HIP)) {
                    val p = detector.lastDetectedPose?.getPoseLandmark(type)
                    append("full pose $type: ${p?.position} confidence=${p?.inFrameLikelihood}\n")
                }
            }
            i.sendStatus(0, Bundle().apply { putString("stream", diagnostics) })
            val target = lm.allFaces.maxByOrNull { it.bounds.bottom } ?: error("No faces")
            detector.switchToFace(lm, target.index)
            check(lm.canAdjustBody()) { "Foreground target body unavailable" }
            val amount=args.getString("amount")?.toIntOrNull()?.coerceIn(0,100) ?: 85
            val tone=args.getString("tone")?.toIntOrNull()?.coerceIn(0,100) ?: 0
            val edits = mapOf(target.index to BeautyAdjustParams(bodySlim = amount))
            val verts = BeautyFilterEngine.computeWarpedVertices(bitmap.width, bitmap.height, lm,
                BeautyAdjustParams(bodySlim = amount, coolTone = tone), faceParamsByIndex = edits)
            for (r in 0..60) for (c in 0 until 60) {
                val at = (r * 61 + c) * 2
                check(verts[at + 2] > verts[at]) { "Folded mesh" }
            }
            val out = checkNotNull(BeautyFilterEngine.process(bitmap, lm, BeautyAdjustParams(bodySlim = amount, coolTone = tone), edits))
            File(i.targetContext.cacheDir, "body-trial-85.png").outputStream().use { out.compress(Bitmap.CompressFormat.PNG, 100, it) }
            if (out !== bitmap) out.recycle()
            return "Foreground body selected=${target.index}, independent body warp and monotone mesh passed\n"
        } finally {
            detector.release(); bitmap.recycle()
        }
    }
}
