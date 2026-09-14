package com.mismira.quickbeauty

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.PorterDuff
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.MotionEvent
import android.view.View
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
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
        private const val TAB_FACE_SIZE = 1
        private const val TAB_CHIN_SLIM = 2
        private const val TAB_FACE_LENGTH = 3
        private const val TAB_SHOULDER = 4
        private const val TAB_BODY_SLIM = 5
    }

    private var currentTab = TAB_COOL_TONE

    private lateinit var previewView: BeautyPreviewView
    private lateinit var badgeOriginal: TextView
    private lateinit var hintCoachMark: TextView
    private lateinit var progressBar: ProgressBar

    private lateinit var topBar: View
    private lateinit var bottomControlPanel: View
    private lateinit var btnReset: View
    private lateinit var txtParamValue: TextView
    private lateinit var seekBarAdjust: SeekBar

    private lateinit var tabContainers: List<View>
    private lateinit var tabIconBgs: List<View>
    private lateinit var tabIcons: List<ImageView>
    private lateinit var tabLabels: List<TextView>

    private var targetImageUri: Uri? = null
    private var previewBitmap: Bitmap? = null
    private var detectedLandmarks: BeautyLandmarks? = null
    private val params = BeautyAdjustParams()
    private var detector: FaceBodyDetector? = null

    private val prefs by lazy { getSharedPreferences("quick_beauty_prefs", Context.MODE_PRIVATE) }

    private val pickImageLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            loadImage(uri)
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

        loadSavedPreferences()
        handleIntent(intent)
    }

    private fun initViews() {
        topBar = findViewById(R.id.topBar)
        bottomControlPanel = findViewById(R.id.bottomControlPanel)
        previewView = findViewById(R.id.previewView)
        badgeOriginal = findViewById(R.id.badgeOriginal)
        hintCoachMark = findViewById(R.id.hintCoachMark)
        progressBar = findViewById(R.id.progressBar)

        btnReset = findViewById(R.id.btnReset)
        txtParamValue = findViewById(R.id.txtParamValue)
        seekBarAdjust = findViewById(R.id.seekBarAdjust)

        tabContainers = listOf(
            findViewById(R.id.tabCoolTone),
            findViewById(R.id.tabFaceSize),
            findViewById(R.id.tabChinSlim),
            findViewById(R.id.tabFaceLength),
            findViewById(R.id.tabShoulder),
            findViewById(R.id.tabBodySlim)
        )
        tabIconBgs = listOf(
            findViewById(R.id.iconBgCoolTone),
            findViewById(R.id.iconBgFaceSize),
            findViewById(R.id.iconBgChinSlim),
            findViewById(R.id.iconBgFaceLength),
            findViewById(R.id.iconBgShoulder),
            findViewById(R.id.iconBgBodySlim)
        )
        tabIcons = listOf(
            findViewById(R.id.iconCoolTone),
            findViewById(R.id.iconFaceSize),
            findViewById(R.id.iconChinSlim),
            findViewById(R.id.iconFaceLength),
            findViewById(R.id.iconShoulder),
            findViewById(R.id.iconBodySlim)
        )
        tabLabels = listOf(
            findViewById(R.id.txtTabCoolTone),
            findViewById(R.id.txtTabFaceSize),
            findViewById(R.id.txtTabChinSlim),
            findViewById(R.id.txtTabFaceLength),
            findViewById(R.id.txtTabShoulder),
            findViewById(R.id.txtTabBodySlim)
        )

        findViewById<View>(R.id.btnClose).setOnClickListener { finish() }
        findViewById<View>(R.id.btnPrivacyPolicy).setOnClickListener { showPrivacyInformation() }
        btnReset.setOnClickListener { resetAll() }
        findViewById<View>(R.id.btnSave).setOnClickListener { saveProcessedImage() }

        tabContainers.forEachIndexed { index, container ->
            container.setOnClickListener { selectTab(index) }
        }

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
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    previewView.setShowOriginal(false)
                    badgeOriginal.visibility = View.GONE
                    true
                }
                else -> false
            }
        }

        findViewById<View>(R.id.previewContainer).setOnTouchListener(touchCompareListener)
        previewView.setOnTouchListener(touchCompareListener)

        selectTab(TAB_COOL_TONE)
        updateResetButton()
    }

    private fun applyWindowInsets() {
        val root = findViewById<View>(R.id.rootLayout) ?: return
        ViewCompat.setOnApplyWindowInsetsListener(root) { _, windowInsets ->
            val insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())

            topBar.updatePadding(left = dpToPx(16), top = insets.top, right = dpToPx(16), bottom = 0)
            topBar.updateLayoutParams {
                height = insets.top + dpToPx(56)
            }

            // 하단 패널 2단 초슬림화: 태스크바 및 제스처 바 여백 확보
            val bottomPadding = max(insets.bottom, dpToPx(48)) + dpToPx(8)
            bottomControlPanel.updatePadding(
                left = dpToPx(14),
                top = dpToPx(10),
                right = dpToPx(14),
                bottom = bottomPadding
            )

            windowInsets
        }
    }

    private fun loadSavedPreferences() {
        val c = prefs.getInt("pref_cool_tone", 0)
        val fs = prefs.getInt("pref_face_size", prefs.getInt("pref_face_slim", 0))
        val cs = prefs.getInt("pref_chin_slim", 0)
        val fl = prefs.getInt("pref_face_length", 0)
        val sh = prefs.getInt("pref_shoulder", 0)
        val b = prefs.getInt("pref_body_slim", 0)
        params.set(c, fs, cs, fl, sh, b)
        updateResetButton()
    }

    private fun savePreferences() {
        prefs.edit()
            .putInt("pref_cool_tone", params.coolTone)
            .putInt("pref_face_size", params.faceSize)
            .putInt("pref_face_slim", params.faceSize)
            .putInt("pref_chin_slim", params.chinSlim)
            .putInt("pref_face_length", params.faceLength)
            .putInt("pref_shoulder", params.shoulder)
            .putInt("pref_body_slim", params.bodySlim)
            .apply()
    }

    private fun showCoachMarkIfNeeded() {
        val shownCount = prefs.getInt("coach_mark_shown_count", 0)
        if (shownCount < 2) {
            hintCoachMark.alpha = 1f
            hintCoachMark.visibility = View.VISIBLE
            hintCoachMark.postDelayed({
                hintCoachMark.animate()
                    .alpha(0f)
                    .setDuration(400)
                    .withEndAction { hintCoachMark.visibility = View.GONE }
                    .start()
            }, 2500)
            prefs.edit().putInt("coach_mark_shown_count", shownCount + 1).apply()
        } else {
            hintCoachMark.visibility = View.GONE
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
        try {
            pickImageLauncher.launch("image/*")
        } catch (_: Throwable) {
            Toast.makeText(this, "사진 선택기를 열 수 없습니다.", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    private fun openPrivacyPolicy() {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(getString(R.string.privacy_policy_url)))
        try {
            startActivity(intent)
        } catch (_: android.content.ActivityNotFoundException) {
            Toast.makeText(this, "개인정보처리방침을 열 수 없습니다.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showPrivacyInformation() {
        AlertDialog.Builder(this)
            .setTitle(R.string.privacy_policy_title)
            .setMessage(R.string.privacy_policy_disclosure)
            .setPositiveButton(R.string.privacy_policy_open) { _, _ -> openPrivacyPolicy() }
            .setNegativeButton(R.string.close, null)
            .show()
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
                showCoachMarkIfNeeded()
            }
        }
    }

    private fun selectTab(tab: Int) {
        this.currentTab = tab

        for (i in tabContainers.indices) {
            val isSelected = (i == tab)
            tabIconBgs[i].setBackgroundResource(
                if (isSelected) R.drawable.bg_tab_circle_selected
                else R.drawable.bg_tab_circle_unselected
            )
            tabIcons[i].setColorFilter(
                if (isSelected) Color.WHITE
                else Color.parseColor("#71717A"),
                PorterDuff.Mode.SRC_IN
            )
            tabLabels[i].setTextColor(
                if (isSelected) Color.WHITE
                else Color.parseColor("#71717A")
            )
            tabLabels[i].typeface = if (isSelected) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
        }

        val currentVal = when (tab) {
            TAB_COOL_TONE -> params.coolTone
            TAB_FACE_SIZE -> params.faceSize
            TAB_CHIN_SLIM -> params.chinSlim
            TAB_FACE_LENGTH -> params.faceLength
            TAB_SHOULDER -> params.shoulder
            TAB_BODY_SLIM -> params.bodySlim
            else -> params.coolTone
        }

        txtParamValue.text = currentVal.toString()
        seekBarAdjust.progress = currentVal
    }

    private fun applyProgress(progress: Int) {
        txtParamValue.text = progress.toString()
        when (currentTab) {
            TAB_COOL_TONE -> params.coolTone = progress
            TAB_FACE_SIZE -> params.faceSize = progress
            TAB_CHIN_SLIM -> params.chinSlim = progress
            TAB_FACE_LENGTH -> params.faceLength = progress
            TAB_SHOULDER -> params.shoulder = progress
            TAB_BODY_SLIM -> params.bodySlim = progress
        }
        savePreferences()
        updateResetButton()
        previewView.setParams(params)
    }

    private fun updateResetButton() {
        btnReset.visibility = if (params.hasAnyEffect()) View.VISIBLE else View.GONE
    }

    private fun resetAll() {
        params.reset()
        prefs.edit().clear().apply()
        updateResetButton()
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
        Toast.makeText(this, "원본 초고화질로 보정하여 저장 중...", Toast.LENGTH_SHORT).show()

        lifecycleScope.launch {
            val savedUri = withContext(Dispatchers.IO) {
                try {
                    val fullBitmap = BeautyBitmapUtils.decodeFullBitmapFromUri(this@MainActivity, uri) ?: previewBitmap
                    val processed = BeautyFilterEngine.process(fullBitmap, landmarks, params)
                    val resultUri = BeautyBitmapUtils.saveBitmapToGallery(this@MainActivity, uri, processed)

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
                Toast.makeText(this@MainActivity, "새 사진으로 저장 완료! ✨ (갤러리 최신 사진)", Toast.LENGTH_LONG).show()
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
