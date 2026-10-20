package com.quickbeauty.app

import android.app.Instrumentation
import android.content.Intent
import android.view.View
import android.view.MotionEvent
import android.os.SystemClock
import android.graphics.Bitmap
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.SeekBar
import android.widget.TextView
import android.net.Uri
import android.os.Bundle
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Local device regression harness. Photos never leave the device. */
class PhotoRegressionRunner : Instrumentation() {
    private lateinit var args: Bundle
    override fun onCreate(arguments: Bundle?) {
        args = arguments ?: Bundle()
        super.onCreate(arguments)
        start()
    }

    override fun onStart() {
        if (args.getString("editflow") == "true") {
            try { finish(0, Bundle().apply { putString("stream", EditFlowRegression.run(this@PhotoRegressionRunner)) }) }
            catch (t: Throwable) { finish(1, Bundle().apply { putString("stream", t.stackTraceToString()) }) }
            return
        }
        if (args.getString("makeup") == "true") {
            try { finish(0, Bundle().apply { putString("stream", VitalityRegression.run(this@PhotoRegressionRunner)) }) }
            catch (t: Throwable) { finish(1, Bundle().apply { putString("stream", t.stackTraceToString()) }) }
            return
        }
        val detector = FaceBodyDetector()
        try {
            val directory = File(args.getString("directory") ?: "/sdcard/Download/Quick Share")
            val prefix = args.getString("prefix") ?: "20261017_"
            val photos = directory.listFiles().orEmpty().filter { it.name.startsWith(prefix) && it.extension == "jpg" }.sortedBy { it.name }
            check(photos.isNotEmpty()) { "No regression photos found" }
            if (args.getString("shoes") == "true") {
                val bitmap = checkNotNull(BeautyBitmapUtils.decodeSampledBitmapFromUri(targetContext, Uri.fromFile(photos.single()), 4000))
                val preview = checkNotNull(BeautyBitmapUtils.decodeSampledBitmapFromUri(targetContext, Uri.fromFile(photos.single()), BeautyBitmapUtils.PREVIEW_MAX_DIMENSION))
                val latch = CountDownLatch(1)
                var result: BeautyLandmarks? = null
                detector.detect(preview) { result = it; latch.countDown() }
                check(latch.await(45, TimeUnit.SECONDS))
                val lm = checkNotNull(result)
                check(lm.canAdjustLegs())
                val scaled = lm.scaleTo(bitmap.width, bitmap.height)
                val out = File(targetContext.filesDir, "shoes").apply { mkdirs() }
                for (amount in listOf(0, 50, 100)) {
                    val transform = BeautyFilterEngine.legTransform(scaled, amount)
                    val output = checkNotNull(BeautyFilterEngine.process(bitmap, lm, BeautyAdjustParams(legStretch = amount)))
                    File(out, "leg-$amount.png").outputStream().use { output.compress(Bitmap.CompressFormat.PNG, 100, it) }
                    if (amount > 0) {
                        val start = kotlin.math.ceil(transform.end).toInt() + 2
                        var maxError = 0
                        // Pixel-level check of protected shoe/floor rows in actual exported render.
                        for (y in start until bitmap.height step 3) for (x in 2 until bitmap.width - 2 step 3) {
                            val before = bitmap.getPixel(x, y)
                            val after = output.getPixel(x, y)
                            for (shift in listOf(0, 8, 16)) maxError = maxOf(maxError, kotlin.math.abs(((before shr shift) and 255) - ((after shr shift) and 255)))
                        }
                        check(maxError <= 2) { "Protected pixels changed: $maxError" }
                        sendStatus(0, Bundle().apply { putString("stream", "amount=$amount crop=${transform.crop} protection=${transform.end} maxProtectedPixelError=$maxError\n") })
                    }
                    if (output !== bitmap) output.recycle()
                }
                preview.recycle(); bitmap.recycle()
                finish(0, Bundle().apply { putString("stream", "Full-resolution shoe protection passed\n") })
                return
            }
            if (args.getString("legs") == "true") {
                var bitmap = checkNotNull(BeautyBitmapUtils.decodeSampledBitmapFromUri(targetContext, Uri.fromFile(photos.single()), BeautyBitmapUtils.PREVIEW_MAX_DIMENSION))
                repeat(3) { pass ->
                    val latch = CountDownLatch(1)
                    var result: BeautyLandmarks? = null
                    detector.detect(bitmap) { result = it; latch.countDown() }
                    check(latch.await(45, TimeUnit.SECONDS))
                    val lm = checkNotNull(result)
                    check(lm.canAdjustLegs()) { "Legs locked on reopen $pass" }
                    val params = BeautyAdjustParams(legStretch = 70)
                    val expected = BeautyFilterEngine.computeWarpedVertices(bitmap.width, bitmap.height, lm, params)
                    for (face in lm.allFaces) {
                        detector.switchToFace(lm, face.index)
                        check(lm.canAdjustLegs()) { "Legs locked after selecting face ${face.index}" }
                        val actual = BeautyFilterEngine.computeWarpedVertices(bitmap.width, bitmap.height, lm, params)
                        check(expected.contentEquals(actual)) { "Face selection changed global leg warp" }
                    }
                    val output = checkNotNull(BeautyFilterEngine.process(bitmap, lm, params))
                    val saved = File(targetContext.cacheDir, "leg-roundtrip.jpg")
                    saved.outputStream().use { output.compress(Bitmap.CompressFormat.JPEG, 95, it) }
                    if (output !== bitmap) output.recycle()
                    bitmap.recycle()
                    bitmap = checkNotNull(BeautyBitmapUtils.decodeSampledBitmapFromUri(targetContext, Uri.fromFile(saved), BeautyBitmapUtils.PREVIEW_MAX_DIMENSION))
                    sendStatus(0, Bundle().apply { putString("stream", "Leg roundtrip ${pass + 1}: enabled for every selected face, saved and reopened\n") })
                }
                bitmap.recycle()
                finish(0, Bundle().apply { putString("stream", "Leg roundtrip passed\n") })
                return
            }
            if (args.getString("render") == "true") {
                for (photo in photos) {
                    val bitmap = checkNotNull(BeautyBitmapUtils.decodeSampledBitmapFromUri(targetContext, Uri.fromFile(photo), BeautyBitmapUtils.PREVIEW_MAX_DIMENSION))
                    val latch = CountDownLatch(1)
                    var result: BeautyLandmarks? = null
                    detector.detect(bitmap) { result = it; latch.countDown() }
                    check(latch.await(45, TimeUnit.SECONDS))
                    val lm = checkNotNull(result)
                    val face = lm.allFaces.minBy { it.center.x }
                    detector.switchToFace(lm, face.index)
                    val vertices = BeautyFilterEngine.computeWarpedVertices(bitmap.width, bitmap.height, lm, BeautyAdjustParams(faceSize = 100))
                    fun mapped(x: Float, y: Float, axis: Int): Float {
                        val gx = x / bitmap.width * BeautyFilterEngine.MESH_W
                        val gy = y / bitmap.height * BeautyFilterEngine.MESH_H
                        val c = gx.toInt().coerceIn(0, BeautyFilterEngine.MESH_W - 1)
                        val r = gy.toInt().coerceIn(0, BeautyFilterEngine.MESH_H - 1)
                        fun at(dx: Int, dy: Int) = vertices[((r + dy) * (BeautyFilterEngine.MESH_W + 1) + c + dx) * 2 + axis]
                        val tx = gx - c
                        val ty = gy - r
                        return (at(0, 0) * (1 - tx) + at(1, 0) * tx) * (1 - ty) +
                            (at(0, 1) * (1 - tx) + at(1, 1) * tx) * ty
                    }
                    val cheekY = (face.leftCheek.y + face.rightCheek.y) / 2f
                    val widthScale = (mapped(face.bounds.right, cheekY, 0) - mapped(face.bounds.left, cheekY, 0)) / face.bounds.width()
                    val heightScale = (mapped(face.center.x, face.bounds.bottom, 1) - mapped(face.center.x, face.bounds.top, 1)) / face.bounds.height()
                    sendStatus(0, Bundle().apply { putString("stream", "At strength 100: cheek width scale=$widthScale face height scale=$heightScale\n") })
                    val out = File(targetContext.filesDir, "render").apply { mkdirs() }
                    for (strength in listOf(0, 50, 100)) {
                        val output = checkNotNull(BeautyFilterEngine.process(bitmap, lm, BeautyAdjustParams(faceSize = strength)))
                        File(out, "face-$strength.png").outputStream().use { output.compress(Bitmap.CompressFormat.PNG, 100, it) }
                        if (output !== bitmap) output.recycle()
                    }
                    sendStatus(0, Bundle().apply { putString("stream", "Render ${photo.name} ${bitmap.width}x${bitmap.height} selected=$face\n") })
                    bitmap.recycle()
                }
                finish(0, Bundle().apply { putString("stream", "Render completed\n") })
                return
            }
            if (args.getString("diagnostic") == "true") {
                for (photo in photos) for (size in listOf(1000, BeautyBitmapUtils.PREVIEW_MAX_DIMENSION)) {
                    val bitmap = checkNotNull(BeautyBitmapUtils.decodeSampledBitmapFromUri(targetContext, Uri.fromFile(photo), size))
                    repeat(3) {
                        val latch = CountDownLatch(1)
                        var result: BeautyLandmarks? = null
                        detector.detect(bitmap) { result = it; latch.countDown() }
                        check(latch.await(45, TimeUnit.SECONDS))
                        val lm = checkNotNull(result)
                        sendStatus(0, Bundle().apply { putString("stream", "legs=${lm.canAdjustLegs()} hip=${lm.legHip()} lower=${lm.legLower()} overlap=${lm.hasSittingPersonNearLegs()} rawFaces=${lm.safetyFaceBounds}\n") })
                        val faces = lm.allFaces.map { face ->
                            val selected = lm.scaleTo(lm.imageWidth, lm.imageHeight)
                            selected.applyFaceInfo(face)
                            "center=${face.center.x.toInt()},${face.center.y.toInt()} bounds=${face.bounds.width().toInt()}x${face.bounds.height().toInt()} enabled=${selected.canAdjust()}"
                        }
                        sendStatus(0, Bundle().apply { putString("stream", "${photo.name} size=${bitmap.width}x${bitmap.height} pass=${it + 1} faces=$faces\n") })
                    }
                    bitmap.recycle()
                }
                finish(0, Bundle().apply { putString("stream", "Diagnostic completed\n") })
                return
            }
            repeat(3) { iteration ->
            for (photo in photos) {
                val bitmap = checkNotNull(BeautyBitmapUtils.decodeSampledBitmapFromUri(targetContext, Uri.fromFile(photo), BeautyBitmapUtils.PREVIEW_MAX_DIMENSION))
                val latch = CountDownLatch(1)
                var result: BeautyLandmarks? = null
                detector.detect(bitmap) { result = it; latch.countDown() }
                check(latch.await(45, TimeUnit.SECONDS)) { "Detection timed out: ${photo.name}" }
                val lm = checkNotNull(result)
                check(lm.allFaces.size == 2) { "Expected two foreground faces in ${photo.name}" }
                val faces = lm.allFaces.map { face ->
                    val selected = lm.scaleTo(lm.imageWidth, lm.imageHeight)
                    selected.applyFaceInfo(face)
                    selected.selectedFaceIndex = face.index
                    check(selected.canAdjust()) { "Face disabled in ${photo.name}: ${face.bounds.width()}x${face.bounds.height()}" }
                    for (params in listOf(BeautyAdjustParams(faceSize = 100), BeautyAdjustParams(chinSlim = 100), BeautyAdjustParams(faceLength = 100))) {
                        val original = BeautyFilterEngine.computeWarpedVertices(bitmap.width, bitmap.height, selected, BeautyAdjustParams())
                        val adjusted = BeautyFilterEngine.computeWarpedVertices(bitmap.width, bitmap.height, selected, params)
                        check(original.indices.any { kotlin.math.abs(original[it] - adjusted[it]) > 0.1f }) { "Warp has no effect: ${photo.name} face=${face.index} params=$params" }
                    }
                    "${face.bounds.width().toInt()}x${face.bounds.height().toInt()}:enabled=${selected.canAdjust()}"
                }
                sendStatus(0, Bundle().apply { putString("stream", "pass=${iteration + 1} ${photo.name} ${bitmap.width}x${bitmap.height} faces=$faces body=${lm.canAdjustBody()}\n") })
                bitmap.recycle()
            }
            }
            val monitor = addMonitor(MainActivity::class.java.name, null, false)
            val launchIntent = Intent(targetContext, MainActivity::class.java).apply {
                action = Intent.ACTION_VIEW
                data = Uri.fromFile(photos.first())
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            runOnMainSync { targetContext.startActivity(launchIntent) }
            val activity = checkNotNull(waitForMonitorWithTimeout(monitor, 15000)) { "Activity did not open" }
            removeMonitor(monitor)
            try {
                repeat(photos.size) { pass ->
                    if (pass > 0) runOnMainSync {
                        MainActivity::class.java.getDeclaredMethod("loadImage", Uri::class.java).apply { isAccessible = true }
                            .invoke(activity, Uri.fromFile(photos[pass % photos.size]))
                    }
                    val deadline = System.currentTimeMillis() + 45000
                    var ready = false
                    while (!ready && System.currentTimeMillis() < deadline) {
                        runOnMainSync { ready = activity.findViewById<View>(R.id.progressBar).visibility != View.VISIBLE }
                        if (!ready) Thread.sleep(100)
                    }
                    check(ready) { "Activity detection timed out" }
                    runOnMainSync {
                        val lmField = MainActivity::class.java.getDeclaredField("detectedLandmarks").apply { isAccessible = true }
                        val lm = lmField.get(activity) as BeautyLandmarks
                        val leftFace = lm.allFaces.minBy { it.center.x }
                        val preview = activity.findViewById<BeautyPreviewView>(R.id.previewView)
                        val scale = minOf(preview.width.toFloat() / lm.imageWidth, preview.height.toFloat() / lm.imageHeight)
                        val x = (preview.width - lm.imageWidth * scale) / 2f + leftFace.center.x * scale
                        val y = (preview.height - lm.imageHeight * scale) / 2f + leftFace.center.y * scale
                        val now = SystemClock.uptimeMillis()
                        for (action in listOf(MotionEvent.ACTION_DOWN, MotionEvent.ACTION_UP)) {
                            val event = MotionEvent.obtain(now, now, action, x, y, 0)
                            preview.dispatchTouchEvent(event)
                            event.recycle()
                        }
                    }
                    Thread.sleep(350) // Single-tap confirmation must wait for the double-tap window.
                    runOnMainSync {
                        val lm = MainActivity::class.java.getDeclaredField("detectedLandmarks").apply { isAccessible = true }.get(activity) as BeautyLandmarks
                        val leftFace = lm.allFaces.minBy { it.center.x }
                        check(lm.selectedFaceIndex == leftFace.index) { "Left face tap did not select left person" }
                        for (tab in listOf(R.id.tabFaceSize, R.id.tabChinSlim, R.id.tabFaceLength)) {
                            activity.findViewById<View>(tab).performClick()
                            val slider = activity.findViewById<SeekBar>(R.id.seekBarAdjust)
                            check(slider.isEnabled) { "UI slider disabled" }
                            check(slider.progress == 0) { "Stale slider value after loading image" }
                            slider.performAccessibilityAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SET_PROGRESS.id,
                                Bundle().apply { putFloat(AccessibilityNodeInfo.ACTION_ARGUMENT_PROGRESS_VALUE, 70f) })
                            check(slider.progress == 70 && activity.findViewById<TextView>(R.id.txtParamValue).text.toString() == "70")
                        }
                    }
                }
                val preview = activity.findViewById<BeautyPreviewView>(R.id.previewView)
                fun viewport() = BeautyPreviewView::class.java.getDeclaredField("viewport").apply { isAccessible = true }.get(preview) as PreviewViewport
                fun touch(action: Int, x: Float, y: Float, down: Long) {
                    runOnMainSync {
                        val e = MotionEvent.obtain(down, SystemClock.uptimeMillis(), action, x, y, 0)
                        preview.dispatchTouchEvent(e); e.recycle()
                    }
                }
                val x = preview.width / 2f
                val y = preview.height / 2f
                repeat(2) {
                    val down = SystemClock.uptimeMillis()
                    touch(MotionEvent.ACTION_DOWN, x, y, down)
                    touch(MotionEvent.ACTION_UP, x, y, down)
                    Thread.sleep(80)
                }
                waitForIdleSync()
                runOnMainSync { check(viewport().zoom == 3f) { "Double tap did not zoom" } }
                val down = SystemClock.uptimeMillis()
                touch(MotionEvent.ACTION_DOWN, x, y, down)
                Thread.sleep(700)
                runOnMainSync {
                    check(activity.findViewById<View>(R.id.badgeOriginal).visibility == View.VISIBLE) { "Hold did not compare" }
                    check(viewport().zoom == 3f) { "Compare changed zoom" }
                }
                touch(MotionEvent.ACTION_UP, x, y, down)
                runOnMainSync {
                    check(activity.findViewById<View>(R.id.badgeOriginal).visibility == View.GONE)
                    val lm = MainActivity::class.java.getDeclaredField("detectedLandmarks").apply { isAccessible = true }.get(activity) as BeautyLandmarks
                    val face = lm.allFaces.first()
                    val v = viewport()
                    check(preview.findFaceAt(v.left + face.center.x * v.scale, v.top + face.center.y * v.scale) == face.index)
                }
                Thread.sleep(350)
                val pinchDown = SystemClock.uptimeMillis()
                fun pinch(action: Int, span: Float, count: Int) {
                    runOnMainSync {
                        val properties = Array(count) { i -> MotionEvent.PointerProperties().apply { id = i; toolType = MotionEvent.TOOL_TYPE_FINGER } }
                        val coords = Array(count) { i -> MotionEvent.PointerCoords().apply {
                            this.x = x + if (i == 0) -span else span
                            this.y = y; pressure = 1f; size = 1f
                        } }
                        val event = MotionEvent.obtain(pinchDown, SystemClock.uptimeMillis(), action, count, properties, coords, 0, 0, 1f, 1f, 0, 0, android.view.InputDevice.SOURCE_TOUCHSCREEN, 0)
                        preview.dispatchTouchEvent(event); event.recycle()
                    }
                    Thread.sleep(25)
                }
                pinch(MotionEvent.ACTION_DOWN, 50f, 1)
                pinch(MotionEvent.ACTION_POINTER_DOWN or (1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT), 50f, 2)
                for (span in listOf(70f, 100f, 130f, 160f)) pinch(MotionEvent.ACTION_MOVE, span, 2)
                pinch(MotionEvent.ACTION_POINTER_UP or (1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT), 160f, 2)
                pinch(MotionEvent.ACTION_UP, 160f, 1)
                runOnMainSync { check(viewport().zoom > 3f) { "Pinch did not enlarge" } }
                val panDown = SystemClock.uptimeMillis()
                var oldLeft = 0f
                runOnMainSync { oldLeft = viewport().left }
                touch(MotionEvent.ACTION_DOWN, x, y, panDown)
                touch(MotionEvent.ACTION_MOVE, x + 60f, y, panDown)
                touch(MotionEvent.ACTION_MOVE, x + 120f, y, panDown)
                touch(MotionEvent.ACTION_UP, x + 120f, y, panDown)
                runOnMainSync { check(viewport().left > oldLeft) { "Zoomed image did not pan" } }
                sendStatus(0, Bundle().apply { putString("stream", "UI: two-finger pinch and one-finger pan passed\n") })
                sendStatus(0, Bundle().apply { putString("stream", "UI: zoom 3x, original comparison at same zoom, transformed face hit passed\n") })
                sendStatus(0, Bundle().apply { putString("stream", "UI: left face selected and three face sliders responded across ${photos.size} image loads\n") })
            } finally { runOnMainSync { activity.finish() } }
            finish(0, Bundle().apply { putString("stream", "Completed ${photos.size} photos\n") })
        } catch (t: Throwable) {
            finish(1, Bundle().apply { putString("stream", t.stackTraceToString()) })
        } finally {
            detector.release()
        }
    }
}
