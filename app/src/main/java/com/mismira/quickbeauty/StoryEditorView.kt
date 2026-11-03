package com.quickbeauty.app

import android.content.Context
import android.graphics.*
import android.text.Editable
import android.text.InputFilter
import android.text.TextWatcher
import android.util.TypedValue
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import android.view.ViewConfiguration
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.FrameLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.widget.AppCompatEditText
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.min

/** One live text block. It uses the export layout even while the Android IME composes Hangul. */
enum class StoryElement { CAPTION, DATE }

class StoryEditorView(context: Context) : FrameLayout(context) {
    val captionInput = CaptionInput(context)
    private var renderer: StoryRenderer? = null
    private var template = StoryTemplateType.CLEAN_PURE
    private var style = StoryTextStyle.TEMPLATE
    private var date: String? = null
    private var position: StoryTextPosition? = null
    private var textScale = 1f
    private var dateScale = 1f
    private var verticalText = false
    var selectedElement = StoryElement.CAPTION
        private set
    private var dateTap = false
    private var datePosition: StoryTextPosition?=null
    private var dateStart=StoryTextPosition(.2f,.15f)
    private var dateDragScale=1f
    private var captionBackground=StoryBackgroundMode.NONE
    private var dateBackground=StoryBackgroundMode.NONE
    private var dateStyle=StoryTextStyle.TEMPLATE
    private var photoPosition: StoryPhotoPosition? = null
    private var plan: StoryRenderer.CaptionPlan? = null
    private var scale = 1f
    private var originX = 0f
    private var originY = 0f
    private var ignoreChanges = false
    private var editingViewport = false
    private var downX = 0f
    private var downY = 0f
    private var startPosition = StoryTextPosition(.5f,.5f)
    private var moved = false
    private var gesture = false
    private var photoGesture = false
    private var photoLastX = 0f
    private var photoLastY = 0f
    var onCaptionChanged: ((String) -> Unit)? = null
    var onPositionChanged: ((StoryTextPosition) -> Unit)? = null
    var onTextScaleChanged: ((Float) -> Unit)? = null
    var onDatePositionChanged: ((StoryTextPosition) -> Unit)? = null
    var onDateScaleChanged: ((Float) -> Unit)? = null
    var onElementSelected: ((StoryElement) -> Unit)? = null
    var onPhotoPositionChanged: ((StoryPhotoPosition) -> Unit)? = null
    val currentCaptionPosition get()=plan?.let { StoryLayout.center(it.bounds) }
    val currentTextScale get()=plan?.contentScale ?: textScale
    val currentSelectedScale get()=if(selectedElement==StoryElement.DATE) dateScale else currentTextScale
    private val photoScaleDetector=ScaleGestureDetector(context,object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScaleBegin(detector: ScaleGestureDetector)=photoGesture && isEnabled
        override fun onScale(detector: ScaleGestureDetector): Boolean {
            val engine=renderer ?: return false
            val current=photoPosition ?: engine.defaultPhotoPosition(template)
            photoPosition=current.copy(zoom=(current.zoom*detector.scaleFactor).coerceIn(.5f,3f))
            moved=true;rebuild();photoPosition?.let { onPhotoPositionChanged?.invoke(it) }
            return true
        }
    })
    private val slop = ViewConfiguration.get(context).scaledTouchSlop
    private val backdrop = object : View(context) {
        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val engine = renderer ?: return
            canvas.save()
            canvas.translate(originX,originY);canvas.scale(scale,scale)
            canvas.clipRect(0f,0f,1080f,1920f)
            val p=plan
            engine.draw(canvas,template,p?.copy(caption=captionInput.text.toString(),
                labelColor=if(captionInput.text.isNullOrBlank()) null else p.labelColor),true,photoPosition)
            canvas.restore()
        }
    }

    init {
        clipChildren=true
        addView(backdrop,LayoutParams(-1,-1))
        captionInput.apply {
            id=View.generateViewId()
            contentDescription=context.getString(R.string.story_tap_edit)
            setBackgroundColor(Color.TRANSPARENT);setPadding(0,0,0,0)
            minHeight=0;minimumHeight=0;minWidth=0;minimumWidth=0
            includeFontPadding=false;isLongClickable=false
            setSingleLine(true);setHorizontallyScrolling(false)
            setOnFocusChangeListener { _,_ -> rebuild() }
            onCaretChanged={ if(editingViewport && !ignoreChanges) rebuild() }
            imeOptions=EditorInfo.IME_ACTION_DONE
            filters=arrayOf(InputFilter { source,start,end,dest,dstart,dend ->
                val original=source.subSequence(start,end).toString()
                val insertion=original.replace('\n',' ').replace('\r',' ')
                val next=dest.substring(0,dstart)+insertion+dest.substring(dend)
                val vertical=template==StoryTemplateType.SIDE_JOURNAL
                val count=StoryCharacters.split(next).size
                if(count>30) {
                    Toast.makeText(context,R.string.story_caption_limit,Toast.LENGTH_SHORT).show()
                    // Keep existing composition intact instead of chopping an emoji or a Hangul syllable.
                    dest.subSequence(dstart,dend)
                } else if(original!=insertion) insertion else null
            })
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?,start: Int,count: Int,after: Int) {}
                override fun onTextChanged(s: CharSequence?,start: Int,before: Int,count: Int) {}
                override fun afterTextChanged(s: Editable?) {
                    if(!ignoreChanges) {
                        rebuild();onCaptionChanged?.invoke(s.toString())
                        onTextScaleChanged?.invoke(currentTextScale)
                    }
                }
            })
            setOnEditorActionListener { _,action,_ ->
                if(action==EditorInfo.IME_ACTION_DONE) { endEditing();true } else false
            }
        }
        addView(captionInput,LayoutParams(1,1))

    }

    fun configure(engine: StoryRenderer,t: StoryTemplateType,s: StoryTextStyle,
        date: String?,position: StoryTextPosition?,caption: String,textScale: Float=1f,
        photoPosition: StoryPhotoPosition?=null,verticalText: Boolean=false,dateScale: Float=1f,
        datePosition: StoryTextPosition?=null,captionBackground: StoryBackgroundMode=StoryBackgroundMode.NONE,
        dateBackground: StoryBackgroundMode=StoryBackgroundMode.NONE,dateStyle: StoryTextStyle=StoryTextStyle.TEMPLATE) {
        renderer=engine;template=t;style=s;this.date=date;this.position=position
        this.textScale=textScale;this.photoPosition=photoPosition;this.verticalText=verticalText;this.dateScale=dateScale
        this.datePosition=datePosition;this.captionBackground=captionBackground;this.dateBackground=dateBackground;this.dateStyle=dateStyle
        if(captionInput.text.toString()!=caption) {
            ignoreChanges=true;captionInput.setText(caption);captionInput.setSelection(captionInput.length());ignoreChanges=false
        }
        rebuild()
    }

    fun selectElement(element: StoryElement) {
        val selected=if(element==StoryElement.DATE && date.isNullOrBlank()) StoryElement.CAPTION else element
        if(selected==StoryElement.DATE && position==null) {
            // Initial auto-stacking happens once. Editing the date must not
            // subsequently move an independently positioned caption.
            currentCaptionPosition?.let { position=it;onPositionChanged?.invoke(it) }
        }
        if(selectedElement==selected) return
        selectedElement=selected
        if(selected==StoryElement.DATE) endEditing()
        rebuild();onElementSelected?.invoke(selected)
    }
    fun changeSelectedScale(value: Float) {
        if(selectedElement==StoryElement.DATE) {
            dateScale=value.coerceIn(.5f,2.2f);rebuild();onDateScaleChanged?.invoke(dateScale)
        } else changeTextScale(value)
    }
    fun setEditingViewport(enabled: Boolean) {
        if(editingViewport!=enabled) { editingViewport=enabled;post { rebuild() } }
    }
    fun focusCaption() {
        selectElement(StoryElement.CAPTION)
        captionInput.visibility=VISIBLE
        captionInput.requestFocus()
        captionInput.post {
            if(captionInput.hasFocus()) androidx.core.view.ViewCompat.getWindowInsetsController(captionInput)
                ?.show(androidx.core.view.WindowInsetsCompat.Type.ime())
        }
    }
    fun endEditing() {
        captionInput.clearFocus()
        (context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager)
            .hideSoftInputFromWindow(windowToken,0)
    }
    fun changeTextScale(value: Float) {
        textScale=value.coerceIn(.5f,2.2f)
        rebuild();onTextScaleChanged?.invoke(currentTextScale)
    }
    fun interactionEnabled(enabled: Boolean) {
        isEnabled=enabled;captionInput.isEnabled=enabled
        rebuild()
    }
    override fun onSizeChanged(w: Int,h: Int,oldw: Int,oldh: Int) {
        super.onSizeChanged(w,h,oldw,oldh)
        post { rebuild() }
    }
    private fun rebuild() {
        val engine=renderer ?: return
        if(width==0 || height==0) return
        val raw=captionInput.text.toString()
        val p=engine.captionPlan(template,raw,style,date,position,textScale,photoPosition,verticalText,dateScale,datePosition,captionBackground,dateBackground,dateStyle)
        plan=p
        if(!(editingViewport && captionInput.hasFocus() && gesture)) {
            scale=min(width/1080f,height/1920f)
            originX=(width-1080f*scale)/2;originY=(height-1920f*scale)/2
            if(editingViewport && captionInput.hasFocus()) {
                val target=TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP,24f,resources.displayMetrics)
                scale=maxOf(scale,width/1080f,target/(p.paint.textSize*p.contentScale))
                val index=captionInput.selectionStart.coerceIn(0,raw.length)
                val offset=if(p.vertical) StoryCharacters.split(raw.substring(0,index)).size.coerceAtMost(p.layout.lineCount-1)
                    else p.layout.getLineForOffset(index.coerceAtMost(p.layout.text.length))
                val caretX=p.textBounds.left+(p.inkInset+(if(p.vertical) p.layout.width/2f
                    else p.layout.getPrimaryHorizontal(index.coerceAtMost(p.layout.text.length))))*p.contentScale
                val caretY=p.textBounds.top+(p.inkInset+p.layout.getLineBaseline(offset))*p.contentScale
                originX=(width*.55f-caretX*scale).coerceIn((width-1080f*scale).coerceAtMost(0f),0f)
                originY=(height*.45f-caretY*scale).coerceIn((height-1920f*scale).coerceAtMost(0f),0f)
            }
        }
        captionInput.visibility=if(raw.isNotBlank() || captionInput.hasFocus()) VISIBLE else INVISIBLE
        captionInput.captionPlan=p;captionInput.outputScale=scale*p.contentScale
        captionInput.setTextSize(TypedValue.COMPLEX_UNIT_PX,p.paint.textSize*scale*p.contentScale)
        captionInput.typeface=p.paint.typeface
        val text=p.textBounds
        captionInput.layoutParams=LayoutParams(ceil(text.width*scale).toInt().coerceAtLeast(1),
            ceil(text.height*scale).toInt().coerceAtLeast(dp(24))).apply {
            leftMargin=(originX+text.left*scale).toInt();topMargin=(originY+text.top*scale).toInt()
        }
        captionInput.invalidate();backdrop.invalidate()
    }
    private fun beginDrag(x: Float,y: Float) {
        selectElement(StoryElement.CAPTION)
        downX=x;downY=y;startPosition=plan?.let { StoryLayout.center(it.bounds) } ?: StoryTextPosition(.5f,.5f)
        moved=false;gesture=true;requestDisallowInterceptTouchEvent(true)
    }
    private fun dragTo(x: Float,y: Float) {
        val p=plan ?: return
        if(abs(x-downX)+abs(y-downY)>slop) moved=true
        val wanted=StoryTextPosition(startPosition.x+(x-downX)/scale/1080f,
            startPosition.y+(y-downY)/scale/1920f)
        position=StoryLayout.center(StoryLayout.block(p.bounds.width,p.bounds.height,wanted))
        rebuild();position?.let { onPositionChanged?.invoke(it) }
    }
    override fun onInterceptTouchEvent(event: MotionEvent): Boolean {
        if(!isEnabled) return false
        if(event.actionMasked==MotionEvent.ACTION_DOWN) {
            val x=(event.x-originX)/scale;val y=(event.y-originY)/scale
            plan?.fixedDate?.bounds?.let { b ->
                if(x in b.left..b.right && y in b.top..b.bottom) {
                    dateTap=true;downX=event.rawX;downY=event.rawY;dateStart=StoryLayout.center(b)
                    selectElement(StoryElement.DATE);dateDragScale=scale
                    requestDisallowInterceptTouchEvent(true);return true
                }
            }
            val box=Rect();captionInput.getHitRect(box)
            if(captionInput.visibility==VISIBLE && box.contains(event.x.toInt(),event.y.toInt())) { beginDrag(event.rawX,event.rawY);return true }
        }
        if(event.actionMasked==MotionEvent.ACTION_DOWN) {
            val hit=Rect();captionInput.getHitRect(hit)
            val frame=StoryLayout.frame(template)
            val x=(event.x-originX)/scale;val y=(event.y-originY)/scale
            if((captionInput.visibility!=VISIBLE || !hit.contains(event.x.toInt(),event.y.toInt())) && x in frame.left..frame.right && y in frame.top..frame.bottom) {
                photoGesture=true;moved=false;photoLastX=event.x;photoLastY=event.y
                requestDisallowInterceptTouchEvent(true);return true
            }
        }
        return gesture || photoGesture || dateTap
    }
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if(!isEnabled) return false
        if(photoGesture) photoScaleDetector.onTouchEvent(event)
        when(event.actionMasked) {
            MotionEvent.ACTION_POINTER_DOWN,MotionEvent.ACTION_POINTER_UP -> {
                val index=if(event.actionMasked==MotionEvent.ACTION_POINTER_UP && event.actionIndex==0) 1 else 0
                photoLastX=event.getX(index);photoLastY=event.getY(index)
            }
            MotionEvent.ACTION_MOVE -> if(dateTap) {
                val stamp=plan?.fixedDate
                if(stamp!=null) {
                    val wanted=StoryTextPosition(dateStart.x+(event.rawX-downX)/dateDragScale/1080f,
                        dateStart.y+(event.rawY-downY)/dateDragScale/1920f)
                    datePosition=StoryLayout.center(StoryLayout.block(stamp.bounds.width,stamp.bounds.height,wanted))
                    rebuild();datePosition?.let { onDatePositionChanged?.invoke(it) }
                }
            } else if(photoGesture && event.pointerCount==1 && !photoScaleDetector.isInProgress) {
                val dx=event.x-photoLastX;val dy=event.y-photoLastY
                if(abs(dx)+abs(dy)>slop) moved=true
                photoPosition=renderer?.panPhoto(template,photoPosition,dx/scale,dy/scale)
                photoLastX=event.x;photoLastY=event.y
                rebuild();photoPosition?.let { onPhotoPositionChanged?.invoke(it) }
            } else if(gesture) dragTo(event.rawX,event.rawY)
            MotionEvent.ACTION_UP -> {
                if(dateTap) { dateTap=false }
                else if(photoGesture) { if(!moved) endEditing() }
                else if(gesture && !moved) focusCaption() else if(!gesture) endEditing()
                gesture=false;photoGesture=false;dateTap=false;backdrop.invalidate();performClick()
            }
            MotionEvent.ACTION_CANCEL -> { gesture=false;photoGesture=false;dateTap=false;backdrop.invalidate() }
        }
        return true
    }
    override fun performClick(): Boolean { super.performClick();return true }
    private fun dp(value: Int)=(value*resources.displayMetrics.density).toInt()

    class CaptionInput(context: Context) : AppCompatEditText(context) {
        var captionPlan: StoryRenderer.CaptionPlan?=null
        var outputScale=1f
        var onCaretChanged: (() -> Unit)?=null
        override fun scrollTo(x: Int,y: Int) { super.scrollTo(0,0) }
        override fun onDraw(canvas: Canvas) {
            val p=captionPlan ?: return
            canvas.save();canvas.scale(outputScale,outputScale);canvas.translate(p.inkInset,p.inkInset)
            if(text.isNullOrEmpty()) {
                val hintPaint=Paint(p.paint).apply {
                    textSize=maxOf(if(p.vertical) 30f else 36f,
                        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP,18f,resources.displayMetrics)/outputScale)
                    alpha=150;textAlign=Paint.Align.CENTER
                }
                val hint=context.getString(R.string.story_vertical_hint)
                canvas.drawText(hint,p.layout.width/2f,p.layout.height/2f-(hintPaint.ascent()+hintPaint.descent())/2,hintPaint)
            } // The shared renderer draws visible lettering; this input view only draws IME hints/caret.
            if(hasFocus()) {
                val raw=text.toString()
                val index=selectionStart.coerceIn(0,raw.length)
                val cursor=Paint(Paint.ANTI_ALIAS_FLAG).apply { color=0xFF8C72EC.toInt();strokeWidth=3f }
                if(p.vertical) {
                    val line=StoryCharacters.split(raw.substring(0,index)).size
                    val y=if(line>=p.layout.lineCount) p.layout.height.toFloat() else p.layout.getLineTop(line).toFloat()
                    canvas.drawLine(12f,y,p.layout.width-12f,y,cursor)
                } else {
                    val offset=if(raw.isEmpty()) 0 else index
                    val line=p.layout.getLineForOffset(offset.coerceAtMost(p.layout.text.length))
                    val x=p.layout.getPrimaryHorizontal(offset.coerceAtMost(p.layout.text.length))
                    canvas.drawLine(x,p.layout.getLineTop(line).toFloat(),x,p.layout.getLineBottom(line).toFloat(),cursor)
                }
            }
            canvas.restore()
        }
        override fun onSelectionChanged(selStart: Int,selEnd: Int) { super.onSelectionChanged(selStart,selEnd);invalidate();onCaretChanged?.invoke() }
        override fun onTouchEvent(event: MotionEvent): Boolean {
            if(event.actionMasked==MotionEvent.ACTION_UP) {
                performClick();requestFocus()
                val p=captionPlan
                if(p!=null) {
                    val line=p.layout.getLineForVertical((event.y/outputScale-p.inkInset).toInt())
                    val offset=if(p.vertical) StoryCharacters.split(text.toString()).take(line+1).sumOf { it.length }
                        else p.layout.getOffsetForHorizontal(line,event.x/outputScale-p.inkInset)
                    setSelection(offset.coerceIn(0,length()))
                }
                (context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager)
                    .showSoftInput(this,InputMethodManager.SHOW_IMPLICIT)
            }
            return true
        }
        override fun performClick(): Boolean { super.performClick();return true }
    }
}
