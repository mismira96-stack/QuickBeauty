package com.mismira.quickbeauty

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.view.MotionEvent
import android.view.View
import android.widget.ProgressBar
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updateLayoutParams
import androidx.core.view.updatePadding
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.max

class MainActivity : AppCompatActivity() {

    companion object {
        private const val TAB_COOL_TONE = 0
        private const val TAB_FACE_SLIM = 1
        private const val TAB_CHIN_SLIM = 2
        private const val TAB_BODY_SLIM = 3
    }

    private var currentTab = TAB_COOL_TONE

    private lateinit var previewView: BeautyPreviewView
    private lateinit var badgeOriginal: TextView
    private lateinit var hintCompare: TextView
    private lateinit var btnCompareTop: TextView
    private lateinit var progressBar: ProgressBar

    private lateinit var topBar: View
    private lateinit var bottomControlPanel: View
    private lateinit var txtParamName: TextView
    private lateinit var txtParamValue: TextView
    private lateinit var seekBarAdjust: SeekBar

    private lateinit var tabCoolTone: View
    private lateinit var tabFaceSlim: View
    private lateinit var tabChinSlim: View
    private lateinit var tabBodySlim: View
    private lateinit var txtTabCoolTone: TextView
    private lateinit var txtTabFaceSlim: TextView
    private lateinit var txtTabChinSlim: TextView
    private lateinit var txtTabBodySlim: TextView

    private var targetImageUri: Uri? = null
    private var previewBitmap: Bitmap? = null
    private var detectedLandmarks: BeautyLandmarks? = null
    private val params = BeautyAdjustParams()
    private var detector: FaceBodyDetector? = null

    private val pickImageLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data?.data != null) {
            loadImage(result.data!!.data!!)
        } else if (targetImageUri == null) {
            finish()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_quick_beauty)

        initViews()
        applyWindowInsets()
        detector = FaceBodyDetector()

        handleIntent(intent)
    }

    private fun initViews() {
        topBar = findViewById(R.id.topBar)
        bottomControlPanel = findViewById(R.id.bottomControlPanel)
        previewView = findViewById(R.id.previewView)
        badgeOriginal = findViewById(R.id.badgeOriginal)
        hintCompare = findViewById(R.id.hintCompare)
        btnCompareTop = findViewById(R.id.btnCompareTop)
        progressBar = findViewById(R.id.progressBar)

        txtParamName = findViewById(R.id.txtParamName)
        txtParamValue = findViewById(R.id.txtParamValue)
        seekBarAdjust = findViewById(R.id.seekBarAdjust)

        tabCoolTone = findViewById(R.id.tabCoolTone)
        tabFaceSlim = findViewById(R.id.tabFaceSlim)
        tabChinSlim = findViewById(R.id.tabChinSlim)
        tabBodySlim = findViewById(R.id.tabBodySlim)

        txtTabCoolTone = findViewById(R.id.txtTabCoolTone)
        txtTabFaceSlim = findViewById(R.id.txtTabFaceSlim)
        txtTabChinSlim = findViewById(R.id.txtTabChinSlim)
        txtTabBodySlim = findViewById(R.id.txtTabBodySlim)

        findViewById<View>(R.id.btnClose).setOnClickListener { finish() }
        findViewById<View>(R.id.btnReset).setOnClickListener { resetAll() }
        findViewById<View>(R.id.btnSave).setOnClickListener { saveProcessedImage() }

        val clickCoolTone = View.OnClickListener { selectTab(TAB_COOL_TONE) }
        val clickFaceSlim = View.OnClickListener { selectTab(TAB_FACE_SLIM) }
        val clickChinSlim = View.OnClickListener { selectTab(TAB_CHIN_SLIM) }
        val clickBodySlim = View.OnClickListener { selectTab(TAB_BODY_SLIM) }

        tabCoolTone.setOnClickListener(clickCoolTone)
        txtTabCoolTone.setOnClickListener(clickCoolTone)

        tabFaceSlim.setOnClickListener(clickFaceSlim)
        txtTabFaceSlim.setOnClickListener(clickFaceSlim)

        tabChinSlim.setOnClickListener(clickChinSlim)
        txtTabChinSlim.setOnClickListener(clickChinSlim)

        tabBodySlim.setOnClickListener(clickBodySlim)
        txtTabBodySlim.setOnClickListener(clickBodySlim)

        seekBarAdjust.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (!fromUser) return
                applyProgress(progress)
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        val touchCompareListener = View.OnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    previewView.setShowOriginal(true)
                    badgeOriginal.visibility = View.VISIBLE
                    hintCompare.visibility = View.GONE
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    previewView.setShowOriginal(false)
                    badgeOriginal.visibility = View.GONE
                    hintCompare.visibility = View.VISIBLE
                    true
                }
                else -> false
            }
        }

        findViewById<View>(R.id.previewContainer).setOnTouchListener(touchCompareListener)
        previewView.setOnTouchListener(touchCompareListener)
        btnCompareTop.setOnTouchListener(touchCompareListener)

        selectTab(TAB_COOL_TONE)
    }

    private fun applyWindowInsets() {
        val root = findViewById<View>(R.id.rootLayout) ?: return
        ViewCompat.setOnApplyWindowInsetsListener(root) { _, windowInsets ->
            val insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())

            topBar.updatePadding(left = dpToPx(16), top = insets.top, right = dpToPx(16), bottom = 0)
            topBar.updateLayoutParams {
                height = insets.top + dpToPx(60)
            }

            val bottomPadding = max(insets.bottom, dpToPx(52)) + dpToPx(16)
            bottomControlPanel.updatePadding(
                left = dpToPx(16),
                top = dpToPx(10),
                right = dpToPx(16),
                bottom = bottomPadding
            )

            windowInsets
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        if (intent == null) {
            pickImageFromGallery()
            return
        }

        val action = intent.action
        if (Intent.ACTION_SEND == action) {
            var imageUri: Uri? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
            } else {
                @Suppress("DEPRECATION")
                intent.getParcelableExtra(Intent.EXTRA_STREAM)
            }
            if (imageUri == null && intent.clipData != null && intent.clipData!!.itemCount > 0) {
                imageUri = intent.clipData!!.getItemAt(0).uri
            }
            if (imageUri == null) {
                imageUri = intent.data
            }
            if (imageUri != null) {
                loadImage(imageUri)
                return
            }
        } else if (Intent.ACTION_SEND_MULTIPLE == action) {
            val uris = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM, Uri::class.java)
            } else {
                @Suppress("DEPRECATION")
                intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM)
            }
            if (!uris.isNullOrEmpty() && uris[0] != null) {
                loadImage(uris[0])
                return
            }
        } else if ((Intent.ACTION_VIEW == action || Intent.ACTION_EDIT == action) && intent.data != null) {
            loadImage(intent.data!!)
            return
        }

        pickImageFromGallery()
    }

    private fun pickImageFromGallery() {
        val pickIntent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI).apply {
            type = "image/*"
        }
        try {
            pickImageLauncher.launch(pickIntent)
        } catch (_: Throwable) {
            try {
                val getContentIntent = Intent(Intent.ACTION_GET_CONTENT).apply {
                    type = "image/*"
                }
                pickImageLauncher.launch(Intent.createChooser(getContentIntent, "사진 선택"))
            } catch (_: Throwable) {
                Toast.makeText(this, "사진 선택기를 열 수 없습니다.", Toast.LENGTH_SHORT).show()
                finish()
            }
        }
    }

    private fun loadImage(uri: Uri) {
        this.targetImageUri = uri
        progressBar.visibility = View.VISIBLE

        lifecycleScope.launch {
            val bmp = withContext(Dispatchers.IO) {
                BeautyBitmapUtils.decodeSampledBitmapFromUri(this@MainActivity, uri, 1920)
            }

            if (bmp == null) {
                progressBar.visibility = View.GONE
                Toast.makeText(this@MainActivity, "사진을 불러올 수 없습니다.", Toast.LENGTH_SHORT).show()
                finish()
                return@launch
            }

            previewBitmap = bmp

            detector?.detect(bmp) { landmarks ->
                detectedLandmarks = landmarks
                progressBar.visibility = View.GONE
                previewView.setSource(bmp, landmarks)
                previewView.setParams(params)
            }
        }
    }

    private fun selectTab(tab: Int) {
        this.currentTab = tab

        val activeBg = R.drawable.bg_tab_selected
        val inactiveBg = R.drawable.bg_tab_unselected

        tabCoolTone.setBackgroundResource(if (tab == TAB_COOL_TONE) activeBg else inactiveBg)
        tabFaceSlim.setBackgroundResource(if (tab == TAB_FACE_SLIM) activeBg else inactiveBg)
        tabChinSlim.setBackgroundResource(if (tab == TAB_CHIN_SLIM) activeBg else inactiveBg)
        tabBodySlim.setBackgroundResource(if (tab == TAB_BODY_SLIM) activeBg else inactiveBg)

        val activeColor = Color.WHITE
        val inactiveColor = Color.parseColor("#94A3B8")

        txtTabCoolTone.setTextColor(if (tab == TAB_COOL_TONE) activeColor else inactiveColor)
        txtTabFaceSlim.setTextColor(if (tab == TAB_FACE_SLIM) activeColor else inactiveColor)
        txtTabChinSlim.setTextColor(if (tab == TAB_CHIN_SLIM) activeColor else inactiveColor)
        txtTabBodySlim.setTextColor(if (tab == TAB_BODY_SLIM) activeColor else inactiveColor)

        txtTabCoolTone.typeface = if (tab == TAB_COOL_TONE) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
        txtTabFaceSlim.typeface = if (tab == TAB_FACE_SLIM) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
        txtTabChinSlim.typeface = if (tab == TAB_CHIN_SLIM) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
        txtTabBodySlim.typeface = if (tab == TAB_BODY_SLIM) Typeface.DEFAULT_BOLD else Typeface.DEFAULT

        val (name, currentVal) = when (tab) {
            TAB_COOL_TONE -> "❄️ 쿨톤 피부" to params.coolTone
            TAB_FACE_SLIM -> "👤 얼굴 크기 축소" to params.faceSlim
            TAB_CHIN_SLIM -> "✨ 턱선 V라인" to params.chinSlim
            TAB_BODY_SLIM -> "🧍 몸매 슬림" to params.bodySlim
            else -> "❄️ 쿨톤 피부" to params.coolTone
        }

        txtParamName.text = name
        txtParamValue.text = currentVal.toString()
        seekBarAdjust.progress = currentVal
    }

    private fun applyProgress(progress: Int) {
        txtParamValue.text = progress.toString()
        when (currentTab) {
            TAB_COOL_TONE -> params.coolTone = progress
            TAB_FACE_SLIM -> params.faceSlim = progress
            TAB_CHIN_SLIM -> params.chinSlim = progress
            TAB_BODY_SLIM -> params.bodySlim = progress
        }
        previewView.setParams(params)
    }

    private fun resetAll() {
        params.reset()
        selectTab(currentTab)
        previewView.setParams(params)
        Toast.makeText(this, "보정 수치가 초기화되었습니다.", Toast.LENGTH_SHORT).show()
    }

    private fun saveProcessedImage() {
        val uri = targetImageUri
        val landmarks = detectedLandmarks
        if (uri == null || landmarks == null) {
            Toast.makeText(this, "저장할 이미지가 준비되지 않았습니다.", Toast.LENGTH_SHORT).show()
            return
        }

        progressBar.visibility = View.VISIBLE
        Toast.makeText(this, "고화질로 보정하여 저장 중...", Toast.LENGTH_SHORT).show()

        lifecycleScope.launch {
            val savedUri = withContext(Dispatchers.IO) {
                try {
                    val fullBitmap = BeautyBitmapUtils.decodeFullBitmapFromUri(this@MainActivity, uri) ?: previewBitmap
                    val processed = BeautyFilterEngine.process(fullBitmap, landmarks, params)
                    val resultUri = BeautyBitmapUtils.saveBitmapToGallery(this@MainActivity, processed)

                    if (fullBitmap != previewBitmap && fullBitmap?.isRecycled == false) {
                        fullBitmap.recycle()
                    }
                    if (processed != null && processed != previewBitmap && processed != fullBitmap && !processed.isRecycled) {
                        processed.recycle()
                    }

                    resultUri
                } catch (t: Throwable) {
                    null
                }
            }

            progressBar.visibility = View.GONE
            if (savedUri != null) {
                Toast.makeText(this@MainActivity, "갤러리에 저장되었습니다! ✨", Toast.LENGTH_LONG).show()
                finish()
            } else {
                Toast.makeText(this@MainActivity, "저장에 실패했습니다.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun dpToPx(dp: Int): Int {
        return (dp * resources.displayMetrics.density + 0.5f).toInt()
    }

    override fun onDestroy() {
        super.onDestroy()
        detector?.release()
    }
}
