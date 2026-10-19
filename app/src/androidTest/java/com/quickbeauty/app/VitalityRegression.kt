package com.quickbeauty.app

import android.app.Instrumentation
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.net.Uri
import android.view.View
import java.io.File
import kotlin.math.abs

/** Synthetic pixel tests: no user photos, gallery writes, or screenshots. */
internal object VitalityRegression {
    fun run(instrumentation: Instrumentation): String {
        val source = Bitmap.createBitmap(600, 1000, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.rgb(220, 205, 190)) }
        val lm = BeautyLandmarks(600, 1000)
        val face = BeautyLandmarks.FaceInfo(0).apply {
            bounds.set(180f, 250f, 420f, 470f); center.set(300f, 360f)
            leftCheek.set(235f, 355f); rightCheek.set(365f, 355f); noseBase.set(300f, 350f)
            mouthPoint.set(300f, 420f); chinPoint.set(300f, 465f)
            upperLipPoints.addAll(listOf(BeautyLandmarks.Point(260f, 390f), BeautyLandmarks.Point(300f, 384f),
                BeautyLandmarks.Point(340f, 390f), BeautyLandmarks.Point(300f, 396f)))
            lowerLipPoints.addAll(listOf(BeautyLandmarks.Point(260f, 410f), BeautyLandmarks.Point(300f, 404f),
                BeautyLandmarks.Point(340f, 410f), BeautyLandmarks.Point(300f, 420f)))
        }
        lm.allFaces.add(face); lm.applyFaceInfo(face)
        lm.hasLegs = true; lm.legHipY = 650f; lm.legLowerY = 950f
        lm.leftAnkle.set(240f, 920f); lm.rightAnkle.set(360f, 950f)
        lm.safetyFaceBounds.add(BeautyLandmarks.Rect(180f, 250f, 420f, 470f))
        check(lm.canAdjustLegs())
        val messages = mutableListOf<String>()
        val tinted = checkNotNull(BeautyFilterEngine.process(source, lm, BeautyAdjustParams(vitality = 100)))
        check(tinted.getPixel(300, 400) == source.getPixel(300, 400)) { "Teeth gap tinted" }
        check(tinted.getPixel(300, 430) == source.getPixel(300, 430)) { "Skin below lips tinted" }
        check(tinted.getPixel(251, 354) != source.getPixel(251, 354)) { "Blush not rendered" }
        messages.add("lip/teeth/skin mask pixels passed")

        // A full-photo source composite is the independent oracle. The cropped
        // production layer must move like the same colored pixels in this photo.
        for (amount in listOf(0, 50, 100)) {
            val p = BeautyAdjustParams(vitality = 100, legStretch = amount, faceSize = amount,
                chinSlim = amount, faceLength = amount)
            val actual = checkNotNull(BeautyFilterEngine.process(source, lm, p))
            val verts = BeautyFilterEngine.computeWarpedVertices(600, 1000, lm, p)
            val expected = Bitmap.createBitmap(600, 1000, Bitmap.Config.ARGB_8888)
            Canvas(expected).drawBitmapMesh(tinted, 60, 60, verts, 0, null, 0,
                Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))
            var totalError = 0L
            for (y in 240 until 480) for (x in 170 until 430) {
                val a = actual.getPixel(x, y); val b = expected.getPixel(x, y)
                for (shift in listOf(0, 8, 16)) totalError += abs(((a shr shift) and 255) - ((b shr shift) and 255))
            }
            val mean = totalError / (240.0 * 260 * 3)
            check(mean < .35) { "Makeup/warp mismatch amount=$amount mean=$mean" }
            messages.add("combined warp $amount: mean pixel error=$mean")
            instrumentation.runOnMainSync {
                val preview = BeautyPreviewView(instrumentation.targetContext)
                preview.layout(0, 0, 600, 1000)
                preview.setSource(source, lm); preview.setParams(p)
                val screen = Bitmap.createBitmap(600, 1000, Bitmap.Config.ARGB_8888)
                preview.draw(Canvas(screen))
                check(screen.sameAs(actual)) { "Preview/export differ at strength $amount" }
                preview.setShowOriginal(true); preview.draw(Canvas(screen))
                check(screen.sameAs(source)) { "Original comparison contains makeup" }
                preview.setSource(null, null); screen.recycle()
            }
            actual.recycle(); expected.recycle()
        }
        // Reuse a landmark scaled for full-resolution export.
        val high = Bitmap.createScaledBitmap(source, 1200, 2000, true)
        val highOut = checkNotNull(BeautyFilterEngine.process(high, lm,
            BeautyAdjustParams(vitality = 75, legStretch = 100, faceSize = 100)))
        check(highOut.width == 1200 && highOut.height == 2000)
        highOut.recycle(); high.recycle()

        // Protect another face even when the cheek's circle overlaps its bounds.
        val other = BeautyLandmarks.FaceInfo(1).apply {
            bounds.set(340f, 280f, 480f, 460f); center.set(410f, 370f)
        }
        lm.allFaces.add(other)
        val one = checkNotNull(BeautyFilterEngine.process(source, lm, BeautyAdjustParams(),
            mapOf(0 to BeautyAdjustParams(vitality = 100), 1 to BeautyAdjustParams())))
        check(one.getPixel(350, 354) == source.getPixel(350, 354)) { "Other face tinted" }
        check(one.getPixel(251, 354) != source.getPixel(251, 354))
        lm.allFaces.remove(other)
        face.upperLipPoints.clear(); face.lowerLipPoints.clear(); lm.applyFaceInfo(face)
        val noContour = checkNotNull(BeautyFilterEngine.process(source, lm, BeautyAdjustParams(vitality = 100)))
        check(noContour.getPixel(300, 390) == source.getPixel(300, 390)) { "Guessed lip fallback returned" }
        one.recycle(); noContour.recycle(); tinted.recycle()
        checkActivityButtons(instrumentation, source)
        source.recycle()
        messages.add("preview/export/original, high-resolution scaling, sibling face and missing contour passed")
        return messages.joinToString("\n") + "\n"
    }

    private fun checkActivityButtons(i: Instrumentation, source: Bitmap) {
        val fixture = File(i.targetContext.cacheDir, "makeup-state-test.png")
        fixture.outputStream().use { source.compress(Bitmap.CompressFormat.PNG, 100, it) }
        val monitor = i.addMonitor(MainActivity::class.java.name, null, false)
        i.runOnMainSync { i.targetContext.startActivity(Intent(i.targetContext, MainActivity::class.java).apply {
            action = Intent.ACTION_VIEW; data = Uri.fromFile(fixture); addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }) }
        val activity = checkNotNull(i.waitForMonitorWithTimeout(monitor, 15000))
        i.removeMonitor(monitor)
        try {
            val deadline = System.currentTimeMillis() + 45000
            var ready = false
            while (!ready && System.currentTimeMillis() < deadline) {
                i.runOnMainSync { ready = activity.findViewById<View>(R.id.progressBar).visibility != View.VISIBLE }
                if (!ready) Thread.sleep(100)
            }
            check(ready) { "Activity photo load did not complete" }
            fun field(name: String) = MainActivity::class.java.getDeclaredField(name).apply { isAccessible = true }
            i.runOnMainSync {
                val tiny = BeautyLandmarks(600, 1000)
                repeat(2) { idx -> tiny.allFaces.add(BeautyLandmarks.FaceInfo(idx).apply {
                    bounds.set(100f + idx * 140f, 200f, 180f + idx * 140f, 290f)
                    center.set(bounds.centerX(), bounds.centerY())
                }) }
                tiny.selectedFaceIndex = 1; tiny.applyFaceInfo(tiny.allFaces[1])
                check(!tiny.canAdjust() && !tiny.canAdjustBody() && !tiny.canAdjustLegs())
                field("detectedLandmarks").set(activity, tiny)
                (field("params").get(activity) as BeautyAdjustParams).reset()
                @Suppress("UNCHECKED_CAST")
                val perFace = field("faceParamsByIndex").get(activity) as MutableMap<Int, BeautyAdjustParams>
                perFace.clear(); perFace[0] = BeautyAdjustParams(vitality = 70)
                for (method in listOf("updateAdjustmentAvailability", "updateResetButton"))
                    MainActivity::class.java.getDeclaredMethod(method).apply { isAccessible = true }.invoke(activity)
                check(activity.findViewById<View>(R.id.btnSave).isEnabled) { "Another face's makeup cannot be saved" }
                check(activity.findViewById<View>(R.id.btnReset).visibility == View.VISIBLE) { "Another face's edits cannot be reset" }
                MainActivity::class.java.getDeclaredMethod("resetAll").apply { isAccessible = true }.invoke(activity)
                check(perFace.isEmpty())
                check(activity.findViewById<View>(R.id.btnReset).visibility == View.GONE)
            }
        } finally {
            i.runOnMainSync { activity.finish() }
            fixture.delete()
        }
    }
}
