package com.mismira.quickbeauty;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.MotionEvent;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import java.util.concurrent.Executors;

public class MainActivity extends Activity {
    private static final int REQUEST_PICK_IMAGE = 1001;

    private static final int TAB_COOL_TONE = 0;
    private static final int TAB_FACE_SLIM = 1;
    private static final int TAB_CHIN_SLIM = 2;
    private static final int TAB_BODY_SLIM = 3;

    private int currentTab = TAB_COOL_TONE;

    private BeautyPreviewView previewView;
    private TextView badgeOriginal;
    private TextView hintCompare;
    private ProgressBar progressBar;

    private TextView txtParamName;
    private TextView txtParamValue;
    private SeekBar seekBarAdjust;

    private View tabCoolTone;
    private View tabFaceSlim;
    private View tabChinSlim;
    private View tabBodySlim;
    private TextView txtTabCoolTone;
    private TextView txtTabFaceSlim;
    private TextView txtTabChinSlim;
    private TextView txtTabBodySlim;

    private Uri targetImageUri;
    private Bitmap previewBitmap;
    private BeautyLandmarks detectedLandmarks;
    private final BeautyAdjustParams params = new BeautyAdjustParams();
    private FaceBodyDetector detector;

    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_quick_beauty);

        initViews();
        detector = new FaceBodyDetector();

        handleIntent(getIntent());
    }

    private void initViews() {
        previewView = findViewById(R.id.previewView);
        badgeOriginal = findViewById(R.id.badgeOriginal);
        hintCompare = findViewById(R.id.hintCompare);
        progressBar = findViewById(R.id.progressBar);

        txtParamName = findViewById(R.id.txtParamName);
        txtParamValue = findViewById(R.id.txtParamValue);
        seekBarAdjust = findViewById(R.id.seekBarAdjust);

        tabCoolTone = findViewById(R.id.tabCoolTone);
        tabFaceSlim = findViewById(R.id.tabFaceSlim);
        tabChinSlim = findViewById(R.id.tabChinSlim);
        tabBodySlim = findViewById(R.id.tabBodySlim);

        txtTabCoolTone = findViewById(R.id.txtTabCoolTone);
        txtTabFaceSlim = findViewById(R.id.txtTabFaceSlim);
        txtTabChinSlim = findViewById(R.id.txtTabChinSlim);
        txtTabBodySlim = findViewById(R.id.txtTabBodySlim);

        findViewById(R.id.btnClose).setOnClickListener(v -> finish());
        findViewById(R.id.btnReset).setOnClickListener(v -> resetAll());
        findViewById(R.id.btnSave).setOnClickListener(v -> saveProcessedImage());

        View.OnClickListener clickCoolTone = v -> selectTab(TAB_COOL_TONE);
        View.OnClickListener clickFaceSlim = v -> selectTab(TAB_FACE_SLIM);
        View.OnClickListener clickChinSlim = v -> selectTab(TAB_CHIN_SLIM);
        View.OnClickListener clickBodySlim = v -> selectTab(TAB_BODY_SLIM);

        tabCoolTone.setOnClickListener(clickCoolTone);
        txtTabCoolTone.setOnClickListener(clickCoolTone);

        tabFaceSlim.setOnClickListener(clickFaceSlim);
        txtTabFaceSlim.setOnClickListener(clickFaceSlim);

        tabChinSlim.setOnClickListener(clickChinSlim);
        txtTabChinSlim.setOnClickListener(clickChinSlim);

        tabBodySlim.setOnClickListener(clickBodySlim);
        txtTabBodySlim.setOnClickListener(clickBodySlim);

        seekBarAdjust.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (!fromUser) return;
                applyProgress(progress);
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        View.OnTouchListener touchCompareListener = (v, event) -> {
            int action = event.getAction();
            if (action == MotionEvent.ACTION_DOWN) {
                previewView.setShowOriginal(true);
                badgeOriginal.setVisibility(View.VISIBLE);
                hintCompare.setVisibility(View.GONE);
                return true;
            } else if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
                previewView.setShowOriginal(false);
                badgeOriginal.setVisibility(View.GONE);
                return true;
            }
            return false;
        };

        findViewById(R.id.previewContainer).setOnTouchListener(touchCompareListener);
        previewView.setOnTouchListener(touchCompareListener);

        selectTab(TAB_COOL_TONE);
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleIntent(intent);
    }

    private void handleIntent(Intent intent) {
        if (intent == null) {
            pickImageFromGallery();
            return;
        }

        String action = intent.getAction();

        if (Intent.ACTION_SEND.equals(action)) {
            Uri imageUri = intent.getParcelableExtra(Intent.EXTRA_STREAM);
            if (imageUri == null && intent.getClipData() != null && intent.getClipData().getItemCount() > 0) {
                imageUri = intent.getClipData().getItemAt(0).getUri();
            }
            if (imageUri == null) {
                imageUri = intent.getData();
            }
            if (imageUri != null) {
                loadImage(imageUri);
                return;
            }
        } else if (Intent.ACTION_SEND_MULTIPLE.equals(action)) {
            java.util.ArrayList<Uri> uris = intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM);
            if (uris != null && !uris.isEmpty() && uris.get(0) != null) {
                loadImage(uris.get(0));
                return;
            }
            if (intent.getClipData() != null && intent.getClipData().getItemCount() > 0) {
                loadImage(intent.getClipData().getItemAt(0).getUri());
                return;
            }
        } else if ((Intent.ACTION_VIEW.equals(action) || Intent.ACTION_EDIT.equals(action)) && intent.getData() != null) {
            loadImage(intent.getData());
            return;
        }

        pickImageFromGallery();
    }

    private void pickImageFromGallery() {
        Intent pickIntent = new Intent(Intent.ACTION_PICK, android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
        pickIntent.setType("image/*");
        try {
            startActivityForResult(pickIntent, REQUEST_PICK_IMAGE);
        } catch (Throwable t) {
            try {
                Intent getContentIntent = new Intent(Intent.ACTION_GET_CONTENT);
                getContentIntent.setType("image/*");
                startActivityForResult(Intent.createChooser(getContentIntent, "사진 선택"), REQUEST_PICK_IMAGE);
            } catch (Throwable t2) {
                Toast.makeText(this, "사진 선택기를 열 수 없습니다.", Toast.LENGTH_SHORT).show();
                finish();
            }
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_PICK_IMAGE && resultCode == RESULT_OK && data != null && data.getData() != null) {
            loadImage(data.getData());
        } else if (targetImageUri == null) {
            finish();
        }
    }

    private void loadImage(Uri uri) {
        this.targetImageUri = uri;
        progressBar.setVisibility(View.VISIBLE);

        Executors.newSingleThreadExecutor().execute(() -> {
            Bitmap bmp = BeautyBitmapUtils.decodeSampledBitmapFromUri(this, uri, 1920);
            mainHandler.post(() -> {
                if (bmp == null) {
                    progressBar.setVisibility(View.GONE);
                    Toast.makeText(this, "사진을 불러올 수 없습니다.", Toast.LENGTH_SHORT).show();
                    finish();
                    return;
                }
                this.previewBitmap = bmp;

                detector.detect(bmp, landmarks -> {
                    this.detectedLandmarks = landmarks;
                    progressBar.setVisibility(View.GONE);
                    previewView.setSource(bmp, landmarks);
                    previewView.setParams(params);
                });
            });
        });
    }

    private void selectTab(int tab) {
        this.currentTab = tab;

        tabCoolTone.setBackgroundColor(Color.TRANSPARENT);
        tabFaceSlim.setBackgroundColor(Color.TRANSPARENT);
        tabChinSlim.setBackgroundColor(Color.TRANSPARENT);
        tabBodySlim.setBackgroundColor(Color.TRANSPARENT);

        txtTabCoolTone.setTextColor(Color.parseColor("#AAAAAA"));
        txtTabFaceSlim.setTextColor(Color.parseColor("#AAAAAA"));
        txtTabChinSlim.setTextColor(Color.parseColor("#AAAAAA"));
        txtTabBodySlim.setTextColor(Color.parseColor("#AAAAAA"));

        int highlightBg = Color.parseColor("#2C2C2C");
        int highlightColor = Color.parseColor("#3B82F6");

        int currentVal = 0;
        String name = "";

        switch (tab) {
            case TAB_COOL_TONE:
                tabCoolTone.setBackgroundColor(highlightBg);
                txtTabCoolTone.setTextColor(highlightColor);
                name = "❄️ 쿨톤 피부";
                currentVal = params.getCoolTone();
                break;
            case TAB_FACE_SLIM:
                tabFaceSlim.setBackgroundColor(highlightBg);
                txtTabFaceSlim.setTextColor(highlightColor);
                name = "👤 얼굴 크기 축소";
                currentVal = params.getFaceSlim();
                break;
            case TAB_CHIN_SLIM:
                tabChinSlim.setBackgroundColor(highlightBg);
                txtTabChinSlim.setTextColor(highlightColor);
                name = "✨ 턱선 V라인";
                currentVal = params.getChinSlim();
                break;
            case TAB_BODY_SLIM:
                tabBodySlim.setBackgroundColor(highlightBg);
                txtTabBodySlim.setTextColor(highlightColor);
                name = "🧍 몸매 슬림";
                currentVal = params.getBodySlim();
                break;
        }

        txtParamName.setText(name);
        txtParamValue.setText(String.valueOf(currentVal));
        seekBarAdjust.setProgress(currentVal);
    }

    private void applyProgress(int progress) {
        txtParamValue.setText(String.valueOf(progress));
        switch (currentTab) {
            case TAB_COOL_TONE:
                params.setCoolTone(progress);
                break;
            case TAB_FACE_SLIM:
                params.setFaceSlim(progress);
                break;
            case TAB_CHIN_SLIM:
                params.setChinSlim(progress);
                break;
            case TAB_BODY_SLIM:
                params.setBodySlim(progress);
                break;
        }
        previewView.setParams(params);
    }

    private void resetAll() {
        params.reset();
        selectTab(currentTab);
        previewView.setParams(params);
        Toast.makeText(this, "보정 수치가 초기화되었습니다.", Toast.LENGTH_SHORT).show();
    }

    private void saveProcessedImage() {
        if (targetImageUri == null || detectedLandmarks == null) {
            Toast.makeText(this, "저장할 이미지가 준비되지 않았습니다.", Toast.LENGTH_SHORT).show();
            return;
        }

        progressBar.setVisibility(View.VISIBLE);
        Toast.makeText(this, "고화질로 보정하여 저장 중...", Toast.LENGTH_SHORT).show();

        Executors.newSingleThreadExecutor().execute(() -> {
            try {
                Bitmap fullBitmap = BeautyBitmapUtils.decodeFullBitmapFromUri(this, targetImageUri);
                if (fullBitmap == null) {
                    fullBitmap = previewBitmap;
                }

                Bitmap processed = BeautyFilterEngine.process(fullBitmap, detectedLandmarks, params);
                Uri savedUri = BeautyBitmapUtils.saveBitmapToGallery(this, processed);

                if (fullBitmap != previewBitmap && !fullBitmap.isRecycled()) {
                    fullBitmap.recycle();
                }
                if (processed != null && processed != previewBitmap && processed != fullBitmap && !processed.isRecycled()) {
                    processed.recycle();
                }

                mainHandler.post(() -> {
                    progressBar.setVisibility(View.GONE);
                    if (savedUri != null) {
                        Toast.makeText(this, "갤러리에 저장되었습니다! ✨", Toast.LENGTH_LONG).show();
                        finish();
                    } else {
                        Toast.makeText(this, "저장에 실패했습니다.", Toast.LENGTH_SHORT).show();
                    }
                });
            } catch (Throwable t) {
                mainHandler.post(() -> {
                    progressBar.setVisibility(View.GONE);
                    Toast.makeText(this, "저장 중 오류: " + t.getMessage(), Toast.LENGTH_SHORT).show();
                });
            }
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (detector != null) {
            detector.release();
        }
    }
}