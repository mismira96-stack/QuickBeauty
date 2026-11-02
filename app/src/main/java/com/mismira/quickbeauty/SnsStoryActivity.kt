package com.quickbeauty.app

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ContentValues
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.text.InputFilter
import android.text.TextWatcher
import android.text.Editable
import android.view.Gravity
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.*
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.core.content.res.ResourcesCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.*
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class SnsStoryActivity : AppCompatActivity() {
    private lateinit var sourceUri: Uri
    private lateinit var editor: StoryEditorView
    private lateinit var save: TextView
    private lateinit var share: TextView
    private lateinit var today: TextView
    private lateinit var captionButton: TextView
    private lateinit var fontButton: TextView
    private lateinit var sizeButton: TextView
    private lateinit var sizePanel: View
    private lateinit var sizeSlider: SeekBar
    private lateinit var sizeValue: TextView
    private lateinit var progress: ProgressBar
    private lateinit var captionInput: EditText
    private lateinit var controlsPanel: View
    private lateinit var helpLabel: View
    private var captionPosition: StoryTextPosition?=null
    private var textScale=1f
    private var dateScale=1f
    private var datePosition: StoryTextPosition?=null
    private var captionBackground=StoryBackgroundMode.NONE
    private var dateBackground=StoryBackgroundMode.NONE
    private var photoPosition: StoryPhotoPosition?=null
    private var verticalText=false
    private var selectedElement=StoryElement.CAPTION
    private lateinit var verticalButton: TextView
    private val template=StoryTemplateType.CLEAN_PURE
    private var textStyle=StoryTextStyle.TEMPLATE
    private var dateStyle=StoryTextStyle.TEMPLATE
    private lateinit var formatRow: LinearLayout
    private val formatButtons=mutableListOf<TextView>()
    private val formatPresets=StoryTextStyle.availableFonts.flatMap { font -> listOf(font to StoryBackgroundMode.NONE,font to StoryBackgroundMode.WHITE) }
    private var storyDate: String?=null
    private var captionValue=""
    private var renderer: StoryRenderer?=null
    private var busy=false

    override fun onCreate(state: Bundle?) {
        enableEdgeToEdge(statusBarStyle=SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle=SystemBarStyle.dark(Color.TRANSPARENT))
        super.onCreate(state)
        sourceUri=intent.data ?: run { finish();return }
        textStyle=StoryTextStyle.availableFonts.find { it.name==state?.getString("styleName") } ?: textStyle
        dateStyle=StoryTextStyle.availableFonts.find { it.name==state?.getString("dateStyle") } ?: StoryTextStyle.TEMPLATE
        captionValue=state?.getString("caption") ?: ""
        storyDate=state?.getString("storyDate")
        state?.getFloatArray("captionPosition")?.takeIf { it.size==2 }?.let { captionPosition=StoryTextPosition(it[0],it[1]) }
        textScale=state?.getFloat("captionScale",1f) ?: 1f
        dateScale=state?.getFloat("dateScale",1f) ?: 1f
        verticalText=state?.getBoolean("verticalText",false) ?: false
        state?.getFloatArray("datePosition")?.takeIf { it.size==2 }?.let { datePosition=StoryTextPosition(it[0],it[1]) }
        // Discard legacy implicit AUTO state after updating; retain explicit choices in new sessions.
        val currentBackgroundState=state?.getInt("backgroundDefaultsVersion",0)==1
        captionBackground=if(currentBackgroundState) StoryBackgroundMode.entries.find { it.name==state?.getString("captionBackground") } ?: StoryBackgroundMode.NONE else StoryBackgroundMode.NONE
        dateBackground=if(currentBackgroundState) StoryBackgroundMode.entries.find { it.name==state?.getString("dateBackground") } ?: StoryBackgroundMode.NONE else StoryBackgroundMode.NONE
        selectedElement=StoryElement.entries.find { it.name==state?.getString("selectedElement") } ?: StoryElement.CAPTION
        state?.getFloatArray("photoPosition")?.takeIf { it.size==3 || it.size==5 }?.let { photoPosition=StoryPhotoPosition(it[0],it[1],it[2],it.getOrElse(3){0f},it.getOrElse(4){0f}) }
        buildUi()
        lifecycleScope.launch {
            val engine=withContext(Dispatchers.IO) {
                val bitmap=BeautyBitmapUtils.decodeSampledBitmapFromUri(this@SnsStoryActivity,sourceUri,1920)
                bitmap?.let { StoryRenderer(it,
                    ResourcesCompat.getFont(this@SnsStoryActivity,R.font.nanum_pen_script) ?: android.graphics.Typeface.DEFAULT,
                    ResourcesCompat.getFont(this@SnsStoryActivity,R.font.gowun_batang_bold) ?: android.graphics.Typeface.SERIF,
                    StoryPhotoPosition(.5f,.5f)) }
            }
            if(engine==null) {
                Toast.makeText(this@SnsStoryActivity,R.string.story_load_failed,Toast.LENGTH_LONG).show();finish()
            } else { renderer=engine;setBusy(false);updatePreview() }
        }
    }

    private fun buildUi() {
        val root=LinearLayout(this).apply {
            orientation=LinearLayout.VERTICAL;setBackgroundColor(0xFF0F1014.toInt())
            isFocusableInTouchMode=true
        }
        setContentView(root)
        ViewCompat.setOnApplyWindowInsetsListener(root) { view,insets ->
            val bars=insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.ime())
            view.setPadding(0,bars.top,0,bars.bottom)
            val keyboard=insets.isVisible(WindowInsetsCompat.Type.ime())
            if(::editor.isInitialized) editor.setEditingViewport(keyboard)
            if(::controlsPanel.isInitialized) controlsPanel.visibility=if(keyboard) View.GONE else View.VISIBLE
            if(::helpLabel.isInitialized) helpLabel.visibility=if(keyboard) View.GONE else View.VISIBLE
            insets
        }
        val header=LinearLayout(this).apply { gravity=Gravity.CENTER_VERTICAL;setPadding(dp(12),0,dp(12),0) }
        header.addView(action(getString(R.string.close)).apply { setOnClickListener { finish() } })
        header.addView(TextView(this).apply {
            text=getString(R.string.story_title);textSize=17f;setTextColor(Color.WHITE);gravity=Gravity.CENTER
        },LinearLayout.LayoutParams(0,dp(54),1f))
        save=action(getString(R.string.story_save)).apply { setTextColor(0xFFB5A8FF.toInt());setOnClickListener { export(false) } }
        header.addView(save);root.addView(header)
        val stage=FrameLayout(this).apply { setPadding(dp(12),dp(4),dp(12),dp(6)) }
        editor=StoryEditorView(this).apply {
            onCaptionChanged={ captionValue=it }
            onPositionChanged={ captionPosition=it }
            onTextScaleChanged={ this@SnsStoryActivity.textScale=it;syncSizeControls() }
            onDatePositionChanged={ this@SnsStoryActivity.datePosition=it }
            onDateScaleChanged={ this@SnsStoryActivity.dateScale=it;syncSizeControls() }
            onElementSelected={
                this@SnsStoryActivity.selectedElement=it
                if(::sizePanel.isInitialized) sizePanel.visibility=View.VISIBLE
                syncSizeControls()
            }
            onPhotoPositionChanged={ this@SnsStoryActivity.photoPosition=it }
        }
        captionInput=editor.captionInput
        stage.addView(editor,FrameLayout.LayoutParams(-1,-1))
        progress=ProgressBar(this)
        stage.addView(progress,FrameLayout.LayoutParams(dp(32),dp(32),Gravity.CENTER))
        root.addView(stage,LinearLayout.LayoutParams(-1,0,1f))
        helpLabel=TextView(this).apply {
            text=getString(R.string.story_inline_help);textSize=11f;setTextColor(0xFFACABB7.toInt());gravity=Gravity.CENTER
        }
        root.addView(helpLabel,LinearLayout.LayoutParams(-1,dp(24)))
        val controls=LinearLayout(this).apply {
            orientation=LinearLayout.VERTICAL;setPadding(dp(16),dp(8),dp(16),dp(10));setBackgroundColor(0xFF191A21.toInt())
        }
        controlsPanel=controls
        val utility=LinearLayout(this)
        captionButton=action(getString(R.string.story_text)).apply { textSize=13f;setOnClickListener { editor.focusCaption() } }
        fontButton=action("Aa").apply { textSize=13f;setOnClickListener { showTextSize() } }
        today=action(getString(R.string.story_today)).apply {
            textSize=12f
            setOnClickListener {
                storyDate=if(storyDate==null) SimpleDateFormat("yyyy.MM.dd",Locale.getDefault()).format(Date()) else null
                if(storyDate==null) selectedElement=StoryElement.CAPTION else selectedElement=StoryElement.DATE
                updatePreview()
            }
        }
        sizeButton=action(getString(R.string.story_size)).apply { textSize=13f;setOnClickListener { showTextSize() } }
        verticalButton=action(getString(R.string.story_vertical)).apply {
            textSize=13f
            setOnClickListener {
                if(captionPosition==null) captionPosition=editor.currentCaptionPosition
                verticalText=!verticalText;selectedElement=StoryElement.CAPTION;updatePreview()
            }
        }
        listOf(captionButton,fontButton,sizeButton,verticalButton,today).forEach {
            it.setPadding(dp(3),0,dp(3),0);it.setSingleLine(true)
            it.setAutoSizeTextTypeUniformWithConfiguration(10,13,1,android.util.TypedValue.COMPLEX_UNIT_SP)
            utility.addView(it,LinearLayout.LayoutParams(0,dp(46),1f))
        }
        controls.addView(utility)
        val sliderRow=LinearLayout(this).apply { gravity=Gravity.CENTER_VERTICAL;visibility=View.GONE }
        sizePanel=sliderRow
        sizeValue=TextView(this).apply { textSize=12f;setTextColor(Color.WHITE);gravity=Gravity.CENTER }
        sliderRow.addView(sizeValue,LinearLayout.LayoutParams(dp(86),dp(40)))
        sizeSlider=SeekBar(this).apply {
            min=50;max=220
            contentDescription=getString(R.string.story_text_size)
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(bar: SeekBar?,progress: Int,fromUser: Boolean) {
                    if(fromUser) { editor.changeSelectedScale(progress/100f);syncSizeControls() }
                }
                override fun onStartTrackingTouch(bar: SeekBar?) {}
                override fun onStopTrackingTouch(bar: SeekBar?) {}
            })
        }
        sliderRow.addView(sizeSlider,LinearLayout.LayoutParams(0,dp(40),1f))
        controls.addView(sliderRow)
        val formatScroll=HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled=false }
        formatRow=LinearLayout(this).apply { gravity=Gravity.CENTER_VERTICAL }
        val fonts=listOf(android.graphics.Typeface.DEFAULT,ResourcesCompat.getFont(this,R.font.nanum_pen_script),ResourcesCompat.getFont(this,R.font.gowun_batang_bold))
        val fontNames=listOf(R.string.story_font_sans,R.string.story_font_hand,R.string.story_font_serif)
        formatPresets.forEachIndexed { index,preset ->
            val (font,mode)=preset
            val fontIndex=StoryTextStyle.availableFonts.indexOf(font)
            val chip=action("Aa").apply {
                typeface=fonts[fontIndex];textSize=21f;setPadding(0,0,0,0)
                contentDescription=getString(fontNames[fontIndex])+", "+getString(if(mode==StoryBackgroundMode.NONE) R.string.story_background_none else R.string.story_background_white)
                setOnClickListener { applyFormatPreset(index) }
            }
            formatButtons.add(chip)
            formatRow.addView(chip,LinearLayout.LayoutParams(dp(48),dp(44)).apply { setMargins(dp(4),dp(2),dp(4),dp(8)) })
        }
        formatScroll.addView(formatRow);controls.addView(formatScroll)
        share=action(getString(R.string.story_share)).apply {
            background=rounded(0xFF7563E7.toInt());setOnClickListener { export(true) }
        }
        controls.addView(share,LinearLayout.LayoutParams(-1,dp(48)))
        root.addView(controls);setBusy(false);root.requestFocus()
    }
    private fun applyFormatPreset(index: Int) {
        val (font,mode)=formatPresets[index]
        if(editor.selectedElement==StoryElement.DATE) { dateStyle=font;dateBackground=mode }
        else { textStyle=font;captionBackground=mode }
        updatePreview()
    }
    private fun showTextSize() {
        sizePanel.visibility=if(sizePanel.visibility==View.VISIBLE) View.GONE else View.VISIBLE
        syncSizeControls()
    }
    private fun syncSizeControls() {
        if(!::sizeSlider.isInitialized) return
        selectedElement=editor.selectedElement
        val label=getString(if(selectedElement==StoryElement.DATE) R.string.story_today else R.string.story_text)
        sizeValue.text="$label ${(editor.currentSelectedScale*100).toInt()}%"
        if(::formatRow.isInitialized) {
            val font=if(selectedElement==StoryElement.DATE) dateStyle else textStyle
            val mode=if(selectedElement==StoryElement.DATE) dateBackground else captionBackground
            formatButtons.forEachIndexed { index,chip ->
                val preset=formatPresets[index];val white=preset.second==StoryBackgroundMode.WHITE
                chip.setTextColor(if(white) Color.BLACK else Color.WHITE)
                chip.background=GradientDrawable().apply {
                    setColor(if(white) Color.WHITE else 0xFF30313B.toInt());cornerRadius=dp(22).toFloat()
                    if(preset.first==font && preset.second==mode) setStroke(dp(2),0xFFA891FF.toInt())
                }
                chip.isSelected=preset.first==font && preset.second==mode
                chip.isEnabled=!busy
            }
        }
        sizeSlider.progress=(editor.currentSelectedScale*100).toInt().coerceIn(50,220)
        captionButton.setTextColor(if(selectedElement==StoryElement.CAPTION) 0xFFD1C7FF.toInt() else Color.WHITE)
        today.setTextColor(if(storyDate!=null) 0xFFD1C7FF.toInt() else Color.WHITE)
        verticalButton.setTextColor(if(verticalText) 0xFFD1C7FF.toInt() else Color.WHITE)
        verticalButton.isSelected=verticalText
        (helpLabel as TextView).setText(R.string.story_single_help)
    }
    private fun updatePreview() {
        val engine=renderer ?: return
        editor.configure(engine,template,textStyle,storyDate,captionPosition,captionValue,
            textScale,photoPosition,verticalText,dateScale,datePosition,captionBackground,dateBackground,dateStyle)
        editor.selectElement(selectedElement)
        textScale=editor.currentTextScale
        syncSizeControls()
        progress.visibility=View.GONE
    }
    private fun setBusy(value: Boolean) {
        busy=value
        val enabled=!value && renderer!=null
        save.isEnabled=enabled;share.isEnabled=enabled
        sizeSlider.isEnabled=enabled
        formatButtons.forEach { it.isEnabled=enabled }
        save.alpha=if(enabled) 1f else .4f;share.alpha=if(enabled) 1f else .4f
        editor.interactionEnabled(enabled)
        listOf(today,captionButton,fontButton,sizeButton,verticalButton).forEach { it.isEnabled=enabled }
        progress.visibility=if(value || renderer==null) View.VISIBLE else View.GONE
    }
    private fun export(forShare: Boolean) {
        val engine=renderer ?: return
        if(busy) return
        editor.endEditing()
        val selected=template;val caption=captionValue;val style=textStyle;val date=storyDate;val position=captionPosition
        val savedTextScale=textScale;val savedPhotoPosition=photoPosition;val savedVertical=verticalText;val savedDateScale=dateScale
        val savedDateStyle=dateStyle
        val savedDatePosition=datePosition;val savedCaptionBackground=captionBackground;val savedDateBackground=dateBackground
        setBusy(true)
        lifecycleScope.launch {
            try {
                val uri=withContext(Dispatchers.IO) {
                    val bitmap=engine.render(selected,caption,textStyle=style,date=date,position=position,
                        textScale=savedTextScale,photoPosition=savedPhotoPosition,verticalText=savedVertical,dateScale=savedDateScale,datePosition=savedDatePosition,
                        captionBackground=savedCaptionBackground,dateBackground=savedDateBackground,dateStyle=savedDateStyle)
                    try {
                        if(forShare) {
                            val directory=File(cacheDir,"stories").apply { mkdirs() }
                            directory.listFiles()?.filter { it.lastModified()<System.currentTimeMillis()-86400000L }?.forEach { it.delete() }
                            val file=File.createTempFile("story-",".png",directory)
                            file.outputStream().use { check(bitmap.compress(Bitmap.CompressFormat.PNG,100,it)) }
                            FileProvider.getUriForFile(this@SnsStoryActivity,"$packageName.storyfiles",file)
                        } else saveToGallery(bitmap)
                    } finally { bitmap.recycle() }
                }
                if(forShare) openStory(uri) else Toast.makeText(this@SnsStoryActivity,R.string.story_saved,Toast.LENGTH_SHORT).show()
            } catch(cancelled: CancellationException) { throw cancelled }
            catch(_: Exception) { Toast.makeText(this@SnsStoryActivity,R.string.story_export_failed,Toast.LENGTH_LONG).show() }
            finally { setBusy(false) }
        }
    }
    private fun saveToGallery(bitmap: Bitmap): Uri {
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "Story_${System.currentTimeMillis()}.png")
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            put(MediaStore.Images.Media.RELATIVE_PATH, "DCIM/QuickBeauty")
            put(MediaStore.Images.Media.IS_PENDING, 1)
            put(MediaStore.Images.Media.WIDTH, bitmap.width)
            put(MediaStore.Images.Media.HEIGHT, bitmap.height)
        }
        val uri = checkNotNull(contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values))
        try {
            checkNotNull(contentResolver.openOutputStream(uri)).use {
                check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it))
            }
            contentResolver.update(uri, ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) }, null, null)
            return uri
        } catch (e: Exception) { contentResolver.delete(uri, null, null); throw e }
    }

    private fun openStory(uri: Uri) {
        val story = Intent("com.instagram.share.ADD_TO_STORY").apply {
            setPackage("com.instagram.android")
            setDataAndType(uri, "image/png")
            clipData = ClipData.newRawUri("Story", uri)
            // Re-enter the share handler with fresh media instead of resurfacing its old stack.
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        try {
            grantUriPermission("com.instagram.android",uri,Intent.FLAG_GRANT_READ_URI_PERMISSION)
            startActivity(story)
        }
        catch (_: ActivityNotFoundException) { shareNormally(uri) }
        catch (_: SecurityException) { shareNormally(uri) }
    }

    private fun shareNormally(uri: Uri) {
        Toast.makeText(this, R.string.story_share_fallback, Toast.LENGTH_LONG).show()
        startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
            type = "image/png"; putExtra(Intent.EXTRA_STREAM, uri)
            clipData = ClipData.newRawUri("Story", uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }, getString(R.string.story_share)))
    }


    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString("styleName",textStyle.name);outState.putString("dateStyle",dateStyle.name)
        outState.putString("caption",captionValue);outState.putString("storyDate",storyDate)
        captionPosition?.let { outState.putFloatArray("captionPosition",floatArrayOf(it.x,it.y)) }
        photoPosition?.let { outState.putFloatArray("photoPosition",floatArrayOf(it.x,it.y,it.zoom,it.offsetX,it.offsetY)) }
        outState.putFloat("captionScale",textScale);outState.putFloat("dateScale",dateScale)
        datePosition?.let { outState.putFloatArray("datePosition",floatArrayOf(it.x,it.y)) }
        outState.putInt("backgroundDefaultsVersion",1)
        outState.putString("captionBackground",captionBackground.name);outState.putString("dateBackground",dateBackground.name)
        outState.putBoolean("verticalText",verticalText);outState.putString("selectedElement",selectedElement.name)
        super.onSaveInstanceState(outState)
    }
    private fun dp(value: Int)=(value*resources.displayMetrics.density).toInt()
    private fun rounded(color: Int)=GradientDrawable().apply { setColor(color);cornerRadius=dp(12).toFloat() }
    private fun action(label: String)=TextView(this).apply {
        text=label;textSize=14f;gravity=Gravity.CENTER;setTextColor(Color.WHITE)
        minHeight=dp(40);minWidth=dp(48);setPadding(dp(12),0,dp(12),0);isClickable=true;isFocusable=true
    }
}
