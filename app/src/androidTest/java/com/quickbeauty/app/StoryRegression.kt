package com.quickbeauty.app

import android.app.Instrumentation
import android.content.Intent
import android.graphics.*
import android.net.Uri
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import java.io.File

/** Runs on a dedicated emulator. No external sharing or posting. */
internal object StoryRegression {
    fun run(i: Instrumentation,previewsOnly: Boolean=false,focusOnly: Boolean=false): String {
        fun onMain(block: () -> Unit) {
            var error: Throwable?=null
            i.runOnMainSync { try { block() } catch(t: Throwable) { error=t } }
            error?.let { throw it }
        }
        val context=i.targetContext
        val out=File(context.filesDir,"story-review").apply { mkdirs() }
        val fixture=File(context.filesDir,"story-fixture.jpg")
        val photo=checkNotNull(BeautyBitmapUtils.decodeSampledBitmapFromUri(context,Uri.fromFile(fixture),1920))
        val engine=StoryRenderer(photo,
            androidx.core.content.res.ResourcesCompat.getFont(context,R.font.nanum_pen_script) ?: Typeface.DEFAULT,
            androidx.core.content.res.ResourcesCompat.getFont(context,R.font.gowun_batang_bold) ?: Typeface.SERIF,StoryCropFocus.find(photo))
        fun write(bitmap: Bitmap,name: String) {
            File(out,name).outputStream().use { check(bitmap.compress(Bitmap.CompressFormat.PNG,100,it)) }
            bitmap.recycle()
        }
        val vertical="오늘도좋은하루였어요"
        if(!focusOnly) {
        for(t in StoryTemplateType.entries) {
            val caption=if(t==StoryTemplateType.SIDE_JOURNAL) "오늘도좋은날" else "오늘도 좋은 날"
            write(engine.render(t,caption,date="2026.10.24"),"template-${t.name.lowercase()}.png")
            for(style in StoryTextStyle.entries) {
                val p=engine.captionPlan(t,caption,style,"2026.10.24",StoryTextPosition(-20f,20f))
                check(p.bounds.top>=200 && p.bounds.bottom<=1670)
                check(p.bounds.left>=24 && p.bounds.right<=1056)
                engine.render(t,caption,textStyle=style,date="2026.10.24",position=StoryLayout.center(p.bounds)).recycle()
            }
        }
        for(t in StoryLayout.availableTemplates) write(engine.render(t,"좋은하루",textStyle=StoryTextStyle.MEMO,date="2026.10.24"),"review-${t.name.lowercase()}.png")
        write(engine.render(StoryTemplateType.SIDE_JOURNAL,"좋은사람들과함께한오늘을오래도록기억하고싶은그런날이었어요",textStyle=StoryTextStyle.MEMO,date="2026.10.24"),"review-side-long.png")
        check(StoryCharacters.split(vertical).size==10)
        val verticalPlan=engine.captionPlan(StoryTemplateType.SIDE_JOURNAL,vertical,StoryTextStyle.TEMPLATE,null)
        check(verticalPlan.layout.lineCount==10) { "Vertical text grew another column" }
        for(line in 0 until 10) check(verticalPlan.layout.getEllipsisCount(line)==0)
        check(StoryCharacters.split("👨‍👩‍👧‍👦").size==1) { "Family emoji was split" }
        check(StoryCharacters.split("한").size==1) { "Composed Hangul was split" }
        for((w,h) in listOf(1600 to 600,600 to 1600,100 to 1600,800 to 800)) {
            val image=Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888).apply { eraseColor(Color.rgb(100,130,160)) }
            val r=StoryRenderer(image)
            for(t in StoryTemplateType.entries) {
                r.render(t,"").recycle()
                r.render(t,if(t==StoryTemplateType.SIDE_JOURNAL) "가족🎉좋아" else "아주 긴 문구와 이모지 🎉☕ 함께하는 좋은 하루").recycle()
            }
            image.recycle()
        }
        // Verify complete single-line glyph layout at extreme drags and sizes, including ink overhang.
        for(t in StoryLayout.availableTemplates) for(style in StoryTextStyle.availableFonts)
            for(text in listOf("가".repeat(30),"오늘도 좋은 날 🎉☕", "Ágj 한글 👨‍👩‍👧‍👦", ""))
                for(size in listOf(.5f,1f,2.2f)) for(pos in listOf(StoryTextPosition(-20f,-20f),StoryTextPosition(20f,20f))) {
                    val p=engine.captionPlan(t,text,style,"2026.10.24",pos,size)
                    val body=if(t==StoryTemplateType.SIDE_JOURNAL) StoryCharacters.split(text).joinToString("\n") else text
                    val expectedLines=if(t==StoryTemplateType.SIDE_JOURNAL) StoryCharacters.split(text).size.coerceAtLeast(1) else 1
                    check(p.layout.lineCount==expectedLines) { "Caption unexpectedly wrapped into another line or column" }
                    check((0 until p.layout.lineCount).all { p.layout.getEllipsisCount(it)==0 })
                    check(p.layout.getLineEnd(p.layout.lineCount-1)==body.length) { "Caption truncated" }
                    check(p.bounds.left>=24 && p.bounds.right<=1056 && p.bounds.top>=200 && p.bounds.bottom<=1670)
                    val ink=Bitmap.createBitmap(p.layout.width+32,p.layout.height+32,Bitmap.Config.ARGB_8888)
                    val c=Canvas(ink);c.translate(16f,16f);p.layout.draw(c)
                    for(x in 0 until ink.width) check(Color.alpha(ink.getPixel(x,0))==0 && Color.alpha(ink.getPixel(x,ink.height-1))==0) { "Vertical ink clipping" }
                    for(y in 0 until ink.height) check(Color.alpha(ink.getPixel(0,y))==0 && Color.alpha(ink.getPixel(ink.width-1,y))==0) { "Horizontal ink clipping" }
                    ink.recycle()
                }
        for(t in StoryLayout.availableTemplates) {
            val first=engine.captionPlan(t,"오늘의 기록",StoryTextStyle.MEMO,"2026.10.24",StoryTextPosition(.2f,.3f),.5f)
            val moved=engine.captionPlan(t,"오늘의 기록",StoryTextStyle.MEMO,"2026.10.24",StoryTextPosition(.8f,.8f),2.2f)
            val noDate=engine.captionPlan(t,"오늘의 기록",StoryTextStyle.MEMO,null,StoryTextPosition(.2f,.3f),.5f)
            val stamp=checkNotNull(first.fixedDate)
            check(stamp.bounds==moved.fixedDate?.bounds && stamp.paint.textSize==moved.fixedDate?.paint?.textSize) { "Date moved or scaled with caption" }
            check(stamp.bounds.left==72f && stamp.bounds.top==216f)
            check(stamp.paint.textSize>=50f) { "Date text too small" }
            check(stamp.background!=Color.TRANSPARENT && first.bounds==noDate.bounds)
        }
        // Neutral automatic contrast and selective backing on mixed bright/dark photography.
        for((background,expected) in listOf(Color.WHITE to Color.BLACK,Color.BLACK to Color.WHITE)) {
            val source=Bitmap.createBitmap(200,400,Bitmap.Config.ARGB_8888).apply { eraseColor(background) }
            val p=StoryRenderer(source).captionPlan(StoryTemplateType.FULL_DRIFT,"오늘",StoryTextStyle.MEMO,null)
            check(p.paint.color==expected && p.readabilityColor==null)
            source.recycle()
        }
        val mixed=Bitmap.createBitmap(1080,1920,Bitmap.Config.ARGB_8888)
        val mixedCanvas=Canvas(mixed)
        for(x in 0 until 1080 step 20) mixedCanvas.drawRect(x.toFloat(),0f,x+20f,1920f,
            Paint().apply { color=if(x%40==0) Color.BLACK else Color.WHITE })
        val mixedPlan=StoryRenderer(mixed).captionPlan(StoryTemplateType.FULL_DRIFT,"오늘의 기록",StoryTextStyle.MEMO,null)
        check(mixedPlan.readabilityColor!=null) { "Mixed background has no readability backing" }
        mixed.recycle()
        val tones=mutableListOf<Int>()
        for(color in listOf(Color.RED,Color.BLUE,Color.GREEN)) {
            val source=Bitmap.createBitmap(100,100,Bitmap.Config.ARGB_8888).apply { eraseColor(color) }
            val result=StoryRenderer(source).render(StoryTemplateType.MUTED_TONE,"")
            tones.add(result.getPixel(0,0));result.recycle();source.recycle()
        }
        check(tones.toSet().size==3) { "Color match ignored photo color" }
        }
        if(previewsOnly) { photo.recycle();return "5 template exports, safe drag bounds and single-column vertical text passed.\n" }
        val monitor=i.addMonitor(SnsStoryActivity::class.java.name,null,false)
        onMain { context.startActivity(Intent(context,SnsStoryActivity::class.java).apply {
            data=Uri.fromFile(fixture);addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }) }
        var activity=checkNotNull(i.waitForMonitorWithTimeout(monitor,15000)) as SnsStoryActivity
        i.removeMonitor(monitor)
        val type=SnsStoryActivity::class.java
        fun field(name: String)=type.getDeclaredField(name).apply { isAccessible=true }
        fun call(name: String)=type.getDeclaredMethod(name).apply { isAccessible=true }.invoke(activity)
        fun ready() {
            var done=false;val end=SystemClock.uptimeMillis()+20000
            while(!done && SystemClock.uptimeMillis()<end) {
                onMain { done=field("renderer").get(activity)!=null &&
                    (field("editor").get(activity) as StoryEditorView).captionInput.captionPlan!=null }
                if(!done) SystemClock.sleep(80)
            }
            check(done) { "Editor not ready" }
            i.waitForIdleSync()
        }
        var saved: Uri?=null
        try {
            ready()
            onMain {
                @Suppress("UNCHECKED_CAST") val tabs=field("tabs").get(activity) as List<View>
                check(tabs.size==3)
                check(field("template").get(activity)==StoryTemplateType.FULL_DRIFT)
                val e=field("editor").get(activity) as StoryEditorView
                check(e.captionInput.visibility==View.INVISIBLE) { "Empty caption visible on entry" }
            }
            SystemClock.sleep(500);i.waitForIdleSync()
            i.uiAutomation.takeScreenshot()?.let { write(it,"editor-empty.png") }
            onMain {
                val editor=field("editor").get(activity) as StoryEditorView
                editor.captionInput.setText("입력하는 문구가 잘 보여야 해요")
                editor.captionInput.setSelection(editor.captionInput.length());editor.focusCaption()
                editor.layoutParams=android.widget.FrameLayout.LayoutParams(-1,(200*context.resources.displayMetrics.density).toInt())
                editor.setEditingViewport(true)
            }
            i.waitForIdleSync();SystemClock.sleep(300)
            onMain {
                val editor=field("editor").get(activity) as StoryEditorView
                val p=checkNotNull(editor.captionInput.captionPlan)
                val target=android.util.TypedValue.applyDimension(android.util.TypedValue.COMPLEX_UNIT_SP,24f,context.resources.displayMetrics)
                check(p.paint.textSize*editor.captionInput.outputScale>=target-.5f) { "Typing preview still too small" }
                val viewType=StoryEditorView::class.java
                fun number(name: String)=viewType.getDeclaredField(name).apply { isAccessible=true }.getFloat(editor)
                val scale=number("scale");val originY=number("originY")
                val caretY=originY+(p.textBounds.top+(p.inkInset+p.layout.getLineBaseline(0))*p.contentScale)*scale
                check(caretY in 0f..editor.height.toFloat()) { "Editing caret left viewport" }
            }
            i.uiAutomation.takeScreenshot()?.let { write(it,"editor-typing.png") }
            if(focusOnly) {
                onMain {
                    @Suppress("UNCHECKED_CAST") val tabs=field("tabs").get(activity) as List<View>
                    tabs[StoryLayout.availableTemplates.indexOf(StoryTemplateType.SIDE_JOURNAL)].performClick()
                    field("textStyle").set(activity,StoryTextStyle.MEMO);call("updatePreview")
                    val editor=field("editor").get(activity) as StoryEditorView
                    editor.captionInput.setText("오늘의나를기억해요");editor.captionInput.setSelection(editor.captionInput.length())
                }
                SystemClock.sleep(600);i.waitForIdleSync()
                onMain {
                    val editor=field("editor").get(activity) as StoryEditorView
                    val p=checkNotNull(editor.captionInput.captionPlan)
                    check(p.vertical && p.layout.lineCount==StoryCharacters.split(editor.captionInput.text.toString()).size)
                    check(androidx.core.view.ViewCompat.getRootWindowInsets(editor)?.isVisible(androidx.core.view.WindowInsetsCompat.Type.ime())==true) { "Real IME not visible" }
                }
                i.uiAutomation.takeScreenshot()?.let { write(it,"editor-typing-vertical.png") }
            }
            onMain {
                val editor=field("editor").get(activity) as StoryEditorView
                val before=checkNotNull(editor.captionInput.captionPlan)
                editor.setEditingViewport(false);editor.endEditing()
                editor.layoutParams=android.widget.FrameLayout.LayoutParams(-1,-1)
                check(editor.captionInput.captionPlan?.bounds==before.bounds) { "Preview zoom changed export coordinates" }
                editor.captionInput.setText("")
            }
            i.waitForIdleSync()
            if(focusOnly) return "Focused horizontal/vertical typing, real IME visible, 24sp target, cursor visibility and unchanged export bounds passed.\n"
            onMain {
                val editor=field("editor").get(activity) as StoryEditorView
                val input=editor.captionInput
                check(input.text.isNullOrEmpty())
                editor.focusCaption()
                val info=EditorInfo()
                val connection=checkNotNull(input.onCreateInputConnection(info))
                check(info.imeOptions and EditorInfo.IME_MASK_ACTION==EditorInfo.IME_ACTION_DONE)
                connection.setComposingText("오",1)
                connection.setComposingText("오늘",1)
                connection.finishComposingText()
                connection.commitText("도 좋은 날",1)
                check(input.text.toString()=="오늘도 좋은 날") { "IME composition corrupted caption: ${input.text}" }
                @Suppress("UNCHECKED_CAST") val focusTabs=field("tabs").get(activity) as List<View>
                focusTabs[StoryLayout.availableTemplates.indexOf(StoryTemplateType.CLEAN_PURE)].performClick()
                check(input.hasFocus()) { "Template change removed text focus" }
                (field("today").get(activity) as View).performClick()
                check(input.text.toString()=="오늘도 좋은 날") { "Date replaced text" }
                (field("today").get(activity) as View).performClick()
                check(field("storyDate").get(activity)==null)
                (field("today").get(activity) as View).performClick()
                @Suppress("UNCHECKED_CAST") val tabs=field("tabs").get(activity) as List<View>
                tabs[StoryLayout.availableTemplates.indexOf(StoryTemplateType.CLEAN_PURE)].performClick()
                check(field("template").get(activity)==StoryTemplateType.CLEAN_PURE)
                input.setText(vertical);editor.focusCaption()
                check(input.text.toString()==vertical)
                val connection2=checkNotNull(input.onCreateInputConnection(EditorInfo()))
                input.setText("가".repeat(30));input.setSelection(input.length());connection2.commitText("더",1)
                check(input.text.toString()=="가".repeat(30)) { "31st character accepted" }
                input.setText(vertical)
                editor.endEditing()
            }
            i.waitForIdleSync()
            onMain {
                val editor=field("editor").get(activity) as StoryEditorView
                val input=editor.captionInput
                check(editor.childCount==2) { "Text handles still overlay caption" }
                val sx=input.left+input.width/2f;val sy=input.top+input.height/2f
                val time=SystemClock.uptimeMillis()
                for((action,x,y) in listOf(Triple(MotionEvent.ACTION_DOWN,sx,sy),
                    Triple(MotionEvent.ACTION_MOVE,sx-200f,sy-160f),Triple(MotionEvent.ACTION_UP,sx-200f,sy-160f))) {
                    val e=MotionEvent.obtain(time,SystemClock.uptimeMillis(),action,x,y,0)
                    editor.dispatchTouchEvent(e);e.recycle()
                }
                @Suppress("UNCHECKED_CAST") val positions=field("positions").get(activity) as Map<StoryTemplateType,StoryTextPosition>
                check(positions[StoryTemplateType.CLEAN_PURE]!=null) { "Direct caption drag failed" }
                @Suppress("UNCHECKED_CAST") val tabs=field("tabs").get(activity) as List<View>
                val shared=positions[StoryTemplateType.CLEAN_PURE]
                tabs[StoryLayout.availableTemplates.indexOf(StoryTemplateType.SIDE_JOURNAL)].performClick()
                check(editor.captionInput.captionPlan?.vertical==true)
                check(positions[StoryTemplateType.SIDE_JOURNAL]==shared) { "Template switch lost caption position" }
                tabs[StoryLayout.availableTemplates.indexOf(StoryTemplateType.CLEAN_PURE)].performClick()
                check(positions[StoryTemplateType.CLEAN_PURE]==shared)
                val before=editor.currentTextScale
                editor.changeTextScale(1.5f)
                check(editor.currentTextScale>before) { "Text size did not increase" }
                @Suppress("UNCHECKED_CAST") val scales=field("textScales").get(activity) as Map<StoryTemplateType,Float>
                check((scales[StoryTemplateType.CLEAN_PURE] ?: 0f)>1f)
                editor.endEditing()
            }
            SystemClock.sleep(400);i.waitForIdleSync()
            i.uiAutomation.takeScreenshot()?.let { write(it,"editor-vertical.png") }
            val recreated=i.addMonitor(SnsStoryActivity::class.java.name,null,false)
            onMain { activity.recreate() }
            activity=checkNotNull(i.waitForMonitorWithTimeout(recreated,15000)) as SnsStoryActivity
            i.removeMonitor(recreated);ready()
            onMain {
                check(field("template").get(activity)==StoryTemplateType.CLEAN_PURE)
                val editor=field("editor").get(activity) as StoryEditorView
                check(editor.captionInput.text.toString()==vertical)
                check(field("storyDate").get(activity)!=null)
                @Suppress("UNCHECKED_CAST") val pos=field("positions").get(activity) as Map<StoryTemplateType,StoryTextPosition>
                check(pos[StoryTemplateType.CLEAN_PURE]!=null)
                @Suppress("UNCHECKED_CAST") val scales=field("textScales").get(activity) as Map<StoryTemplateType,Float>
                check((scales[StoryTemplateType.CLEAN_PURE] ?: 0f)>1f) { "Text size lost on recreation" }
                val r=field("renderer").get(activity) as StoryRenderer
                val plan=editor.captionInput.captionPlan!!
                check(plan.layout.lineCount==1)
                val full=r.render(StoryTemplateType.CLEAN_PURE,vertical,date=field("storyDate").get(activity) as String,
                    position=pos[StoryTemplateType.CLEAN_PURE],textScale=scales[StoryTemplateType.CLEAN_PURE] ?: 1f)
                write(full.copy(Bitmap.Config.ARGB_8888,false),"vertical-moved-export.png")
                // Guides, keyboard and caret are not exported. Compare the editor content with its export.
                editor.endEditing();editor.interactionEnabled(false)
                val actual=Bitmap.createBitmap(editor.width,editor.height,Bitmap.Config.ARGB_8888)
                editor.draw(Canvas(actual))
                val expected=Bitmap.createBitmap(editor.width,editor.height,Bitmap.Config.ARGB_8888)
                val scale=minOf(editor.width/1080f,editor.height/1920f)
                val ox=(editor.width-1080*scale)/2;val oy=(editor.height-1920*scale)/2
                Canvas(expected).drawBitmap(full,null,RectF(ox,oy,ox+1080*scale,oy+1920*scale),Paint(Paint.FILTER_BITMAP_FLAG))
                var error=0L;var samples=0
                for(y in 0 until actual.height step 4) for(x in 0 until actual.width step 4) {
                    val a=actual.getPixel(x,y);val b=expected.getPixel(x,y)
                    error+=kotlin.math.abs(Color.red(a)-Color.red(b))+kotlin.math.abs(Color.green(a)-Color.green(b))+kotlin.math.abs(Color.blue(a)-Color.blue(b));samples+=3
                }
                val mean=error.toDouble()/samples
                check(mean<7.0) { "Editor/export pixel mismatch: $mean" }
                actual.recycle();expected.recycle();editor.interactionEnabled(true)
                val method=type.getDeclaredMethod("saveToGallery",Bitmap::class.java).apply { isAccessible=true }
                saved=method.invoke(activity,full) as Uri
                full.recycle()
            }
            context.contentResolver.openInputStream(saved!!).use { stream ->
                val bitmap=checkNotNull(BitmapFactory.decodeStream(stream));check(bitmap.width==1080 && bitmap.height==1920);bitmap.recycle()
            }
            onMain {
                @Suppress("UNCHECKED_CAST") val tabs=field("tabs").get(activity) as List<View>
                tabs[StoryLayout.availableTemplates.indexOf(StoryTemplateType.FULL_DRIFT)].performClick()
            }
            i.waitForIdleSync()
            onMain {
                val editor=field("editor").get(activity) as StoryEditorView
                val scale=minOf(editor.width/1080f,editor.height/1920f)
                val ox=(editor.width-1080*scale)/2;val oy=(editor.height-1920*scale)/2
                val frame=StoryLayout.frame(StoryTemplateType.CINEMATIC_16_9)
                val x=ox+540*scale;val y=oy+(frame.top+frame.bottom)/2*scale
                val time=SystemClock.uptimeMillis()
                for((action,yy) in listOf(MotionEvent.ACTION_DOWN to y,MotionEvent.ACTION_MOVE to y+100f,MotionEvent.ACTION_UP to y+100f)) {
                    val event=MotionEvent.obtain(time,SystemClock.uptimeMillis(),action,x,yy,0)
                    editor.dispatchTouchEvent(event);event.recycle()
                }
                @Suppress("UNCHECKED_CAST") val photoPositions=field("photoPositions").get(activity) as Map<StoryTemplateType,StoryPhotoPosition>
                val adjusted=checkNotNull(photoPositions[StoryTemplateType.FULL_DRIFT]) { "Photo drag did not update crop" }
                check(adjusted.y in 0f..1f)
                val r=field("renderer").get(activity) as StoryRenderer
                write(r.render(StoryTemplateType.CINEMATIC_16_9,"오늘도 좋은 날",photoPosition=adjusted),"cinematic-adjusted.png")
            }
            // Route real two-pointer events through the parent, including pointer lift transitions.
            onMain {
                @Suppress("UNCHECKED_CAST") val tabs=field("tabs").get(activity) as List<View>
                tabs[StoryLayout.availableTemplates.indexOf(StoryTemplateType.FULL_DRIFT)].performClick()
            }
            i.waitForIdleSync()
            onMain {
                val editor=field("editor").get(activity) as StoryEditorView
                val x=editor.width/2f;val y=editor.height*.3f
                val time=SystemClock.uptimeMillis();var tick=0L
                val trace=StringBuilder("size=${editor.width}x${editor.height} template=${field("template").get(activity)} enabled=${editor.isEnabled} ")
                fun event(action: Int,span: Float,two: Boolean) {
                    val count=if(two) 2 else 1
                    val props=Array(count) { index -> MotionEvent.PointerProperties().apply { id=index;toolType=MotionEvent.TOOL_TYPE_FINGER } }
                    val coords=Array(count) { index -> MotionEvent.PointerCoords().apply {
                        this.x=x+(if(index==0) -span/2 else span/2);this.y=y;pressure=1f;size=1f
                    } }
                    val e=MotionEvent.obtain(time,time+tick,action,count,props,coords,0,0,1f,1f,0,0,android.view.InputDevice.SOURCE_TOUCHSCREEN,0)
                    editor.dispatchTouchEvent(e)
                    val pg=StoryEditorView::class.java.getDeclaredField("photoGesture").apply { isAccessible=true }.get(editor)
                    val detector=StoryEditorView::class.java.getDeclaredField("photoScaleDetector").apply { isAccessible=true }.get(editor) as android.view.ScaleGestureDetector
                    trace.append("action=$action pg=$pg span=${detector.currentSpan} scaling=${detector.isInProgress} ")
                    e.recycle();tick+=20
                }
                event(MotionEvent.ACTION_DOWN,240f,false)
                event(MotionEvent.ACTION_POINTER_DOWN or (1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT),240f,true)
                for(span in listOf(360f,520f,680f,840f)) event(MotionEvent.ACTION_MOVE,span,true)
                event(MotionEvent.ACTION_POINTER_UP or (1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT),840f,true)
                event(MotionEvent.ACTION_UP,840f,false)
                @Suppress("UNCHECKED_CAST") val positions=field("photoPositions").get(activity) as Map<StoryTemplateType,StoryPhotoPosition>
                check((positions[StoryTemplateType.FULL_DRIFT]?.zoom ?: 1f)>1.1f) { "Pinch did not enlarge photo: $trace positions=$positions" }
                val r=field("renderer").get(activity) as StoryRenderer
                write(r.render(StoryTemplateType.FULL_DRIFT,"",photoPosition=positions[StoryTemplateType.FULL_DRIFT]),"full-zoomed.png")
            }
            val zoomMonitor=i.addMonitor(SnsStoryActivity::class.java.name,null,false)
            onMain { activity.recreate() }
            activity=checkNotNull(i.waitForMonitorWithTimeout(zoomMonitor,15000)) as SnsStoryActivity
            i.removeMonitor(zoomMonitor);ready()
            onMain {
                check(field("template").get(activity)==StoryTemplateType.FULL_DRIFT)
                @Suppress("UNCHECKED_CAST") val positions=field("photoPositions").get(activity) as Map<StoryTemplateType,StoryPhotoPosition>
                check((positions[StoryTemplateType.FULL_DRIFT]?.zoom ?: 1f)>1.1f) { "Photo zoom lost on recreation" }
            }
            onMain { (field("sizeButton").get(activity) as View).performClick() }
            i.waitForIdleSync()
            onMain {
                val panel=field("sizePanel").get(activity) as View
                val slider=field("sizeSlider").get(activity) as android.widget.SeekBar
                check(panel.visibility==View.VISIBLE && slider.width>0) { "Inline size panel missing" }
                val time=SystemClock.uptimeMillis()
                for(action in listOf(MotionEvent.ACTION_DOWN,MotionEvent.ACTION_UP)) {
                    val e=MotionEvent.obtain(time,SystemClock.uptimeMillis(),action,slider.width*.7f,slider.height/2f,0)
                    slider.dispatchTouchEvent(e);e.recycle()
                }
                check((field("editor").get(activity) as StoryEditorView).currentTextScale>1f)
            }
            SystemClock.sleep(500);i.waitForIdleSync()
            i.uiAutomation.takeScreenshot()?.let { write(it,"editor-size.png") }
            return "3 photo-filled templates including single-column vertical, 24sp minimum focused typing viewport, large fixed date, 3 fonts, fixed upper-left dates independent of caption size/drag, shared dragged position, neutral auto contrast and mixed-background backing, retained input focus, inline size touch control, single-line ink clipping matrix, automatic color match, 4 ratios, styles, Unicode vertical single column, direct Hangul IME input, 30-character input limit, date toggle, drag, resize, size restoration, cinematic fill, photo panning, two-finger photo zoom and zoom restoration, recreation, editor/export pixel comparison and JPEG save passed. No external sharing.\n"
        } finally {
            saved?.let { context.contentResolver.delete(it,null,null) }
            onMain { activity.finish() };photo.recycle()
        }
    }
}
