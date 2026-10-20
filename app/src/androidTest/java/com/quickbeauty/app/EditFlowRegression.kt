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
        val fixture = File(i.targetContext.cacheDir, "edit-flow.png")
        Bitmap.createBitmap(240, 320, Bitmap.Config.ARGB_8888).apply {
            eraseColor(Color.rgb(160, 120, 100))
            fixture.outputStream().use { compress(Bitmap.CompressFormat.PNG, 100, it) }
            recycle()
        }
        val monitor = i.addMonitor(MainActivity::class.java.name, null, false)
        i.runOnMainSync {
            i.targetContext.startActivity(Intent(i.targetContext, MainActivity::class.java).apply {
                action = Intent.ACTION_VIEW; data = Uri.fromFile(fixture)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
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
                i.runOnMainSync { ok = checkOnMain() }
                if (!ok) Thread.sleep(100)
            }
            check(ok) { "Timed out waiting for editor state" }
        }
        var saved: Uri? = null
        try {
            await { a.findViewById<View>(R.id.progressBar).visibility != View.VISIBLE }
            i.runOnMainSync {
                (field("params").get(a) as BeautyAdjustParams).coolTone = 35
                call("updateAdjustmentAvailability")
                check(call("hasUnsavedEdits") == true)
                a.findViewById<View>(R.id.btnSave).performClick()
            }
            await { !field("isSaving").getBoolean(a) }
            i.runOnMainSync {
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
            i.waitForIdleSync()
            i.uiAutomation.takeScreenshot()?.let { screenshot ->
                File(i.targetContext.cacheDir, "saved-bar.png").outputStream().use {
                    screenshot.compress(Bitmap.CompressFormat.PNG, 100, it)
                }
                screenshot.recycle()
            }
            i.runOnMainSync {
                a.onBackPressedDispatcher.onBackPressed()
                check(field("isPickingImage").getBoolean(a)) { "Back did not open picker" }
            }
            i.waitForIdleSync()
            i.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
            await { !field("isPickingImage").getBoolean(a) }
            i.runOnMainSync {
                check(!a.isFinishing) { "Cancel picker closed editor" }
                check(field("targetImageUri").get(a) == Uri.fromFile(fixture))
                check((field("params").get(a) as BeautyAdjustParams).coolTone == 35)
            }
            return "Save keeps editor and original, saved snapshot clears dirty state, later changes are dirty, back opens picker, cancel preserves edits: passed\n"
        } finally {
            saved?.let { i.targetContext.contentResolver.delete(it, null, null) }
            i.runOnMainSync { a.finish() }
            fixture.delete()
        }
    }
}
