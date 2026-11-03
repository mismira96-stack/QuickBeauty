package com.quickbeauty.app

import android.app.Instrumentation
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import android.view.KeyEvent
import android.view.View
import java.io.File

/** Synthetic fixture only; the saved MediaStore entry is deleted after the test. */
internal object EditFlowRegression {
    fun run(i: Instrumentation): String {
        // Assertion failures belong on the instrumentation thread. Throwing from
        // runOnMainSync crashes the target app instead of reporting a test failure.
        fun onMain(block: () -> Unit) {
            var failure: Throwable? = null
            i.runOnMainSync { try { block() } catch (t: Throwable) { failure = t } }
            failure?.let { throw it }
        }
        val fixture = File(i.targetContext.cacheDir, "edit-flow.png")
        Bitmap.createBitmap(240, 320, Bitmap.Config.ARGB_8888).apply {
            eraseColor(Color.rgb(160, 120, 100))
            fixture.outputStream().use { compress(Bitmap.CompressFormat.PNG, 100, it) }
            recycle()
        }
        val monitor = i.addMonitor(MainActivity::class.java.name, null, false)
        onMain {
            i.targetContext.startActivity(Intent(i.targetContext, MainActivity::class.java).apply {
                action = Intent.ACTION_VIEW; data = Uri.fromFile(fixture)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            })
        }
        val a = checkNotNull(i.waitForMonitorWithTimeout(monitor, 15000)) as MainActivity
        i.removeMonitor(monitor)
        fun field(name: String) = MainActivity::class.java.getDeclaredField(name).apply { isAccessible = true }
        fun call(name: String) = MainActivity::class.java.getDeclaredMethod(name).apply { isAccessible = true }.invoke(a)
        fun await(checkOnMain: () -> Boolean) {
            val end = System.currentTimeMillis() + 45000
            var ok = false
            while (!ok && System.currentTimeMillis() < end) {
                onMain { ok = checkOnMain() }
                if (!ok) Thread.sleep(100)
            }
            check(ok) { "Timed out waiting for editor state" }
        }
        fun clickDialogText(id: Int) {
            i.waitForIdleSync();Thread.sleep(200)
            val deadline=android.os.SystemClock.uptimeMillis()+3000
            var node: android.view.accessibility.AccessibilityNodeInfo?=null
            while(node==null && android.os.SystemClock.uptimeMillis()<deadline) {
                node=i.uiAutomation.rootInActiveWindow?.findAccessibilityNodeInfosByText(a.getString(id))?.firstOrNull()
                if(node==null) Thread.sleep(100)
            }
            val bounds=android.graphics.Rect();checkNotNull(node).getBoundsInScreen(bounds)
            val time=android.os.SystemClock.uptimeMillis()
            for(action in listOf(android.view.MotionEvent.ACTION_DOWN,android.view.MotionEvent.ACTION_UP)) {
                val event=android.view.MotionEvent.obtain(time,android.os.SystemClock.uptimeMillis(),action,bounds.centerX().toFloat(),bounds.centerY().toFloat(),0)
                i.uiAutomation.injectInputEvent(event,true);event.recycle()
            }
            i.waitForIdleSync()
        }
        fun galleryCount()=i.targetContext.contentResolver.query(android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            arrayOf(android.provider.MediaStore.Images.Media._ID),null,null,null)?.use { it.count } ?: 0
        var saved: Uri? = null
        var story: SnsStoryActivity?=null
        try {
            await { a.findViewById<View>(R.id.progressBar).visibility != View.VISIBLE }
            onMain {
                (field("params").get(a) as BeautyAdjustParams).coolTone = 35
                call("updateAdjustmentAvailability")
                check(call("hasUnsavedEdits") == true)
            }
            onMain { call("showPrivacyInformation") }
            i.waitForIdleSync();Thread.sleep(200)
            i.uiAutomation.takeScreenshot()?.let { bitmap ->
                File(i.targetContext.cacheDir,"privacy-sheet.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG,100,it) };bitmap.recycle()
            }
            clickDialogText(R.string.close)
            onMain { call("requestPhotoChange") }
            i.waitForIdleSync();Thread.sleep(200)
            i.uiAutomation.takeScreenshot()?.let { bitmap ->
                File(i.targetContext.cacheDir,"unsaved-sheet.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG,100,it) };bitmap.recycle()
            }
            clickDialogText(R.string.cancel)
            onMain { check(call("hasUnsavedEdits")==true && !field("isPickingImage").getBoolean(a)) }
            val beforeGalleryCount=galleryCount()
            val storyMonitor=i.addMonitor(SnsStoryActivity::class.java.name,null,false)
            onMain {
                check(field("lastSavedUri").get(a)==null)
                check(a.findViewById<View>(R.id.btnStoryTemplate).isEnabled)
                a.findViewById<View>(R.id.btnStoryTemplate).performClick()
            }
            story=checkNotNull(i.waitForMonitorWithTimeout(storyMonitor,20000)) as SnsStoryActivity
            i.removeMonitor(storyMonitor)
            val storyUri=checkNotNull(story!!.intent.data)
            check(galleryCount()==beforeGalleryCount) { "Direct story created a gallery image" }
            check(storyUri.scheme=="content" && storyUri.authority=="${i.targetContext.packageName}.storyfiles" && storyUri.path?.contains("storyinputs")==true)
            i.targetContext.contentResolver.openInputStream(storyUri).use {
                val bitmap=checkNotNull(android.graphics.BitmapFactory.decodeStream(it))
                val source=android.graphics.BitmapFactory.decodeFile(fixture.absolutePath)
                val expected=checkNotNull(BeautyFilterEngine.process(source,null,BeautyAdjustParams(coolTone=35)))
                val actualColor=bitmap.getPixel(bitmap.width/2,bitmap.height/2)
                val expectedColor=expected.getPixel(expected.width/2,expected.height/2)
                for(shift in listOf(0,8,16)) check(kotlin.math.abs(((actualColor shr shift) and 255)-((expectedColor shr shift) and 255))==0) { "Direct story lost current correction" }
                bitmap.recycle();if(expected!==source) expected.recycle();source.recycle()
            }
            onMain { story!!.finish() }
            story=null;i.waitForIdleSync()
            onMain {
                check(field("lastSavedUri").get(a)==null && call("hasUnsavedEdits")==true) { "Direct story changed gallery-save state" }
                check((field("params").get(a) as BeautyAdjustParams).coolTone==35)
                check(field("targetImageUri").get(a)==Uri.fromFile(fixture))
                a.findViewById<View>(R.id.btnSave).performClick()
            }
            await { !field("isSaving").getBoolean(a) }
            onMain {
                saved = field("lastSavedUri").get(a) as Uri?
                check(saved != null) { "Save failed" }
                check(!a.isFinishing) { "Editor closed after save" }
                check(a.findViewById<View>(R.id.saveResultPanel).visibility == View.VISIBLE)
                val panel = a.findViewById<View>(R.id.saveResultPanel)
                val action = a.findViewById<View>(R.id.btnViewSaved)
                val density = a.resources.displayMetrics.density
                check(panel.height <= 56 * density) { "Saved bar occupies too much photo space" }
                check(action.height >= 48 * density) { "View photo touch target too small" }
                check(call("hasUnsavedEdits") == false)
                check(field("targetImageUri").get(a) == Uri.fromFile(fixture)) { "Original source replaced" }
                (field("params").get(a) as BeautyAdjustParams).coolTone = 40
                check(call("hasUnsavedEdits") == true)
                (field("params").get(a) as BeautyAdjustParams).coolTone = 35
            }
            onMain {
                (field("params").get(a) as BeautyAdjustParams).coolTone = 40
                a.onBackPressedDispatcher.onBackPressed()
                check(!field("isPickingImage").getBoolean(a)) { "Back must not open Gallery" }
            }
            clickDialogText(R.string.cancel)
            onMain {
                check(!a.isFinishing && call("hasUnsavedEdits")==true)
                (field("params").get(a) as BeautyAdjustParams).coolTone = 35
            }
            i.waitForIdleSync()
            i.uiAutomation.takeScreenshot()?.let { screenshot ->
                File(i.targetContext.cacheDir, "saved-bar.png").outputStream().use {
                    screenshot.compress(Bitmap.CompressFormat.PNG, 100, it)
                }
                screenshot.recycle()
            }
            onMain {
                a.findViewById<View>(R.id.btnClose).performClick()
                check(field("isPickingImage").getBoolean(a)) { "Change photo did not open picker" }
            }
            i.waitForIdleSync()
            // OEM galleries can have nested album screens. Navigate back until
            // the picker actually returns, without sending Back to the editor.
            repeat(4) {
                var picking = false
                onMain { picking = field("isPickingImage").getBoolean(a) }
                if (picking) {
                    i.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
                    Thread.sleep(400)
                }
            }
            await { !field("isPickingImage").getBoolean(a) }
            onMain {
                check(!a.isFinishing) { "Cancel picker closed editor" }
                check(field("targetImageUri").get(a) == Uri.fromFile(fixture)) {
                    "Picker returned another photo; cancellation-only test was interrupted by a selection"
                }
                check((field("params").get(a) as BeautyAdjustParams).coolTone == 35)
                a.onBackPressedDispatcher.onBackPressed()
                check(a.isFinishing) { "Back from saved editor must exit instead of reopening Gallery" }
            }
            return "Direct Story without gallery save preserves correction and source; save keeps editor and original, saved snapshot clears dirty state, later changes are dirty, photo-change opens picker, cancel preserves edits, Back exits saved editor: passed\n"
        } finally {
            saved?.let { i.targetContext.contentResolver.delete(it, null, null) }
            onMain { story?.finish();a.finish() }
            fixture.delete()
        }
    }
}
