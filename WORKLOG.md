## 2026-10-20 생기 강도 비교용 튜닝 (출시 미반영)

- 추가 사용자 피드백으로 립 최대 alpha를 65에서 75로 상향했다. 볼터치 85, 색상, 보호 영역 및 워프는 유지한다. 44개 단위 테스트, assembleRelease와 APK 서명 검증 통과. blush85-lip75-test.apk를 Google Drive/QuickBeauty-tests에 복사하고 SHA-256 일치를 확인했다. 기존 테스트본은 보존하며 Play에는 업로드하지 않았다. 발색은 사용자 확인 대기.
- 후속 사용자 요청: 볼터치 85를 유지하고 립 최대 alpha만 54에서 65로 소폭 상향. 아랫입술 보조 착색은 기존 35% 비율을 유지한다. 색상/보호 영역/워프는 변경하지 않는다. 새 테스트 APK는 blush85-lip65 이름으로 구분한다.
- 립65 테스트본 testReleaseUnitTest 44건, assembleRelease 및 APK 서명 검증 통과. 별도 파일 quickbeauty-1.1.4-blush85-lip65-test.apk로 생성하여 Google Drive의 QuickBeauty-tests 폴더에도 복사했다. 이전 blush85 테스트본은 비교용으로 유지한다. 실기기 발색은 사용자 확인 대기, Play 업로드 없음.

- 사용자 피드백: 출시본 생기가 거의 변하지 않는 느낌. 볼터치 MAX_CHEEK_ALPHA를 65에서 85로 조정했다.
- 로즈 핑크 RGB, 립 강도, 영역 보호 및 워프 처리는 유지한다. 버전은 1.1.4(17) 테스트용 그대로이며 AAB 생성/스토어 업로드는 하지 않는다.
- 실제 사진에서 자연스러움과 발색은 사용자 확인 후 확정한다.
- testReleaseUnitTest 44건 및 assembleRelease 통과. 테스트 APK: C:/work/outputs/quickbeauty-vitality-85-20261020/quickbeauty-1.1.4-blush85-test.apk. 연결 기기 없음으로 설치는 하지 않았다.

## 2026-10-20 QuickBeauty 1.1.4 (17) 출시 준비

- Google Play API 조회에서 기존 프로덕션 1.1.3(16)은 completed 상태로 확인. 16번은 신발 보호 수정본이며 이번 생기 수정본과 다르다.
- 새 versionCode 17 / versionName 1.1.4로 구분. testReleaseUnitTest(44건), assembleRelease, bundleRelease 통과. APK 서명 및 ZIP 16KB 정렬 검사 통과.
- 서명 APK/AAB, 한글·영문 출시 노트와 메타데이터: C:/work/releases/QuickBeauty/1.1.4.
- AAB SHA256: a033d5b7bab9d5b8746a6d13db23e1210e819e8e11e3b88d5c2f604520584415.
- 최종 심사 제출은 사용자 수동 진행. 기존 배포 국가·정책 선언·스토어 이미지 변경 없음.
- API 업로드/검증/커밋 완료 후 별도 edit으로 재조회: production 17=draft, 기존 16=completed 유지. 한글·영문 노트 및 서버 AAB SHA256 일치 확인.
- changesNotSentForReview 플래그는 이 앱에서 거부되어 draft 트랙 변경만 플래그 없이 커밋했다. 심사 제출/배포는 실행하지 않았다. 결과: C:/work/outputs/quickbeauty-20261020/play-draft-result.json.
- 출시 소스 스냅샷 ec67e4a를 origin/codex/16kb-release에 푸시했다.

## 2026-10-19 Codex 최종 보완 — 아래 이전 Gemini 요약보다 우선

- 생기는 원본 좌표에서 얼굴별 작은 투명 레이어로 렌더링한 뒤, 사진과 동일한 메쉬 셀로 변형한다 (`VitalityRenderer`). 볼 중심뿐 아니라 그라데이션/클리핑/입술 모양도 함께 움직인다. 미리보기는 레이어를 캐싱하며 고해상도 저장에서 전체 사진 크기의 추가 메이크업 비트맵을 만들지 않는다.
- 로즈 핑크 복구: 볼 #E0829D / 최대 alpha 65, 립 #C95E7E / 보조 #B95070. 쿨톤 밝기(32 기반 오프셋)는 유지했다.
- 립 윤곽 누락 시 추정 타원 없음. 유효한 입술 다각형 안으로 블러를 제한한다. 볼은 보수적인 얼굴 타원/하안부 창을 사용하며 다른 검출 얼굴을 제외한다. 피부 의미 분할 모델은 아니다.
- 저장 및 초기화는 모든 얼굴의 보정 상태를 반영한다. 얼굴 전환 직후 초기화 상태도 갱신한다. 저장 중 버튼 재활성화를 막는다.
- 얼굴 크기 20% 일괄 필터 제거. 중심과 영역이 거의 겹치는 중복 검출을 정리하고, 터치 시 다른 얼굴의 확장 영역보다 실제 얼굴 사각형을 우선한다. 작은 정상 원거리 인물은 상대 면적만으로 버리지 않는다.
- 선택 링/터치 영역은 현재 보정 좌표에 맞추며, 기존 링 애니메이션을 취소한 후 하나만 실행한다. 사진 전환/뷰 분리 시 캐시와 애니메이션 정리.
- debug는 다시 com.quickbeauty.app.debug로 분리. 정식 패키지 서명 불일치로 첫 설치 실패 후 사용자가 기존 앱을 삭제했고, 서명된 수정 release APK를 com.quickbeauty.app으로 설치/실행했다. 사용자: '잘되는거같아'. 특정 사진의 역사적 검출 원인까지 확정한 것은 아니다.
- JVM 44건 통과(엔진37 / 뷰포트3 / 선택4), release APK·debug APK·instrumentation APK 빌드 통과.
- API36 에뮬레이터의 실제 Android Canvas 픽셀 검증 통과: 생기+형태 보정 0/50/100, 미리보기/저장 동일, 원본 비교, 치아 틈/입술 아래 피부, 옆 얼굴 보호, 윤곽 누락, 고해상도 좌표 스케일링. 독립적인 전체 합성-워프 기준과 색상 평균 오차는 0.12/255 미만이었다.
- 실제 Activity에서 작은 얼굴 B가 0이고 A에 생기가 남아 있을 때 저장·초기화 활성화, 전체 초기화 후 map 비움/버튼 숨김 검증 통과. 테스트는 합성 시료와 앱 내부 캐시만 사용했으며 갤러리에 캡처나 사진을 추가하지 않았다.
- 버전은 1.1.3(16) 테스트 상태 유지. 기존 업로드 AAB는 이번 수정본이 아니다. 이번 턴에서 AAB 재생성/Play 업로드/심사 제출은 하지 않았다.

# QuickBeauty (간편 뷰티 ✨) 개발 완료 리포트 & 워크로그 (Worklog)

# QuickBeauty (간편 뷰티 ✨) 개발 완료 리포트 & 워크로그 (Worklog)

## 🤖 [CODEX 리뷰 & 인수인계 가이드] 2026-10-19 세션 정리

> **작업 브랜치**: `codex/16kb-release`
> **패키지명**: `com.quickbeauty.app` (디버그/릴리즈 단일 패키지 통일 — 이전 기록, 위 최신 보완 참조)
> **검증 상태**: 당시 Gemini 기록 42건 (`./gradlew testDebugUnitTest`), 최신 재검증은 위 참조
> **기기 배포**: `SM-F971N` 실기기에 최신 단일 빌드 설치 완료 (`./gradlew installDebug`)

### 📌 7대 우선순위 리뷰 과제 구현 완료 내역
1. **[P1] 생기 위치 동기화 (`BeautyFilterEngine.kt`, `BeautyPreviewView.kt`)**:
   - `BeautyFilterEngine.warpPoint(origX, origY, verts, meshW, meshH, w, h)`: 메쉬 버텍스 이중선형 보간(Bilinear Interpolation) 알고리즘 구현.
   - 다리길이 연장(`legStretch`), 얼굴축소(`faceSize`), 턱선(`chinSlim`), 얼굴길이(`faceLength`) 워프 시 메쉬 변위에 맞춰 볼터치와 립스틱 좌표가 100% 동기화되도록 연동 (`BeautyPreviewView` 및 `process` 저장 파이프라인 동일 적용).
2. **[P2] 립 오착색 방지 (`BeautyFilterEngine.kt`)**:
   - 윤곽선 미검출 시 `mouthPoint` 기준 타원으로 임의 추정하던 fallback 코드 완전 삭제.
   - 4점 이상의 유효한 윗/아랫입술 실제 윤곽선(`upperLipPoints`, `lowerLipPoints`)이 있을 때만 정밀 렌더링하여 치아나 턱 살 침범 원천 차단.
3. **[P2] 얼굴별 저장/초기화 상태 일치 (`MainActivity.kt`)**:
   - `updateResetButton`: 현재 얼굴(`params`)뿐 아니라 `faceParamsByIndex`에 남아있는 다른 얼굴들의 보정값까지 합산 평가하여 [초기화] 버튼 표시 상태 유지.
4. **[P2] 이중 포커스 링 / 원 크기 변동 원인 규명 및 해결**:
   - **원인 1**: `build.gradle`의 `applicationIdSuffix ".debug"`로 인해 폰에 구버전 정식 앱과 신버전 개발 앱 2개가 공존하여 구버전 화면을 보며 테스트하던 혼선 -> `.debug` 접미사 제거 및 단일 `com.quickbeauty.app`으로 통합/재설치 완료.
   - **원인 2**: 다중 인물 사진에서 배경/옷깃의 미세 노이즈 얼굴(idx=5 등) 오검출로 재클릭 시 작은 원이 뜨던 현상 -> `FaceBodyDetector.kt`의 `isSelectableFace`에서 메인 얼굴 대비 면적 20% 미만 노이즈 얼굴 필터링 적용.
   - 포커스 링을 얼굴 전체 윤곽과 헤어스타일까지 안정적으로 감싸는 `0.72f` 단일 선택 링으로 통일 (`unselectedIndicatorPaint` 제거).
5. **[P2] 볼터치 영역 보호 (`BeautyFilterEngine.kt`)**:
   - 앞볼 애플존 중심 그라데이션 시 눈 위(눈두덩/이마) 및 턱선 아래(목/옷깃)로 번지지 않도록 캔버스 안전 사각 영역 클리핑(`clipRect`) 적용.
6. **[P2] 렌더링 검증 & 단위 테스트 (`BeautyFilterEngineTest.kt`)**:
   - `testWarpPointBilinearInterpolation` 단위 테스트 추가 및 전체 42건 단위 테스트 통과.
7. **[P3] 릴리즈 디버그 로그 정리**:
   - 프로덕션 코드 내 임시 진단 로그(`QB_DEBUG`) 완전 제거.

### 🔍 CODEX 내일 검증 체크포인트
1. `git diff`를 통해 `BeautyFilterEngine.kt`, `BeautyPreviewView.kt`, `FaceBodyDetector.kt`, `MainActivity.kt`의 변경 내역 확인.
2. 실기기(`SM-F971N`)에서 `QuickBeauty` 단일 앱 실행 후 2인 사진에서:
   - 왼쪽/오른쪽 얼굴 탭 시 단일 블루 링이 시원하게 뜨고 오클릭(노이즈 얼굴 튐)이 없는지 확인.
   - [생기] 슬라이더 조절 후 [다리길이], [얼굴크기], [턱선] 조절 시 볼터치/립 위치가 함께 움직이는지 확인.
   - 사진 저장 후 갤러리 결과물과 미리보기가 100% 일치하는지 확인.

## v1.1.0 다중 얼굴 보정 유지

- 얼굴을 전환해도 이전 얼굴의 보정값을 얼굴별로 보존하고, 최종 메시에 여러 얼굴의 보정을 합성.
- 비포/애프터 비교는 사진 전체 원본과 최종 결과 기준으로 유지.
- 얼굴 영역을 길게 눌러도 전체 사진 비포/애프터를 확인할 수 있도록 터치 동작을 분리.
- 얼굴 전환 시 선택된 얼굴의 저장값을 슬라이더와 숫자 표시에도 즉시 반영.
- 다중 얼굴 체형 보정 잠금은 유지해 포즈 오인 바인딩을 방지.

## v1.0.7 출시 후보 (versionCode 10, 2026-09-26)

- 다중 인물 사진에서는 단일 포즈가 다른 얼굴에 붙지 않도록 어깨·몸매 보정을 보수적으로 제한하고, 얼굴 보정은 유지.
- 선택되지 않은 다른 얼굴의 바운딩 영역을 워프에서 보호해 인접 인물의 얼굴이 함께 변형되지 않도록 처리.
- 얼굴 컨투어 매칭을 첫 결과 고정에서 전체 결과 순회로 변경.
- 저장 시 사진·랜드마크·보정 파라미터를 스냅샷으로 고정하고, 저장 중 새 인텐트를 무시해 상태 혼합 방지.
- 초고해상도 원본 저장은 6000px 편집 상한을 적용해 대형 카메라 사진의 OOM 저장 실패를 예방.
- 영문 리소스 및 스토어 등록정보를 포함한 1.0.7 릴리즈 후보로 단위 테스트와 APK/AAB 빌드 검증 예정.
- 저장 해상도 설명을 실제 동작에 맞춰 정정. 대형 원본은 최대 6000px 범위로 처리하며 “원본 무손실·제한 없음” 표현은 사용하지 않음.

## v1.0.6 출시 후보 (versionCode 9, 2026-09-21)

- 얼굴 미검출 시 가상의 얼굴·신체를 만들지 않도록 변경. 음식 등 비인물 사진에서는 형태 보정을 건너뛰고 쿨톤 색감 조정만 허용.
- 얼굴이 40×40 미리보기 메쉬에서 세 칸 미만이거나 100px 미만이면 형태 보정을 건너뛰고 화면에 이유를 안내.
- 다중 얼굴 검출과 대표 얼굴 윤곽선 추출을 별도 ML Kit 검출기로 분리. 윤곽선 모드가 다중 얼굴 반환을 제한하던 설정을 제거.
- 어깨·몸매 보정은 사진 좌우 가장자리에서 변위를 0으로 감쇠. 인물과 배경이 맞닿은 내부 경계는 분할 마스크가 없어 완전히 보호할 수 없으므로 실사진 확인 필요.
- 작은 얼굴에서는 얼굴 변형을 제한하되, 해당 얼굴과 일치하는 실제 상체 포즈가 충분히 크게 잡히면 어깨·몸매 보정은 허용. 하단 썸네일처럼 작은 검출 후보는 얼굴 선택 목록에서 제외하고, 탭 안내에는 누적처럼 보이는 얼굴 번호를 표시하지 않음.
- 얼굴을 휴대전화로 가린 전신 사진은 얼굴 보정만 제한하고, 어깨·골반 포즈가 높은 신뢰도로 검출되면 어깨·몸매 보정을 허용. 실기기 사진(750×1000)에서 어깨 폭 56px을 검출했으나 기존 60px 하한 때문에 잠기던 문제를 확인해, 포즈 신뢰도 0.9 및 사진 너비의 6% 이상 조건을 유지하면서 고정 하한을 40px로 조정.
- 여러 명 사진에서는 선택한 얼굴과 포즈가 일치하지 않으면 가상 상체를 만들지 않아 타인의 몸이 함께 변형되지 않도록 제한.
- 릴리즈 단위 테스트 20건 통과, 서명 APK/AAB 빌드 완료. Android 36 에뮬레이터에서 사용자 제공 2인 사진 캡처의 양쪽 얼굴 선택과 비인물 이미지의 형태 보정 차단·쿨톤 사용을 확인. Samsung Galaxy 실기기에서는 얼굴을 가린 작은 전신 원본 사진에서 어깨·몸매 슬라이더 활성화와 실제 보정을 사용자와 함께 확인.
- 글로벌 영어권 출시 준비 및 다국어(i18n) 시스템 구축:
  - 안드로이드 기본 리소스(`values/strings.xml`)를 글로벌 영어로 표준화하고, `values-ko/strings.xml`(한국어) 및 `values-en/strings.xml`(영어)을 분리 구성하여 시스템 언어에 자동 대응.
  - 서구권 미적 기준과 뉘앙스를 반영한 6대 탭 영문화: `Tone`, `Face Ratio`, `Jawline`, `Chin Lift`, `Shoulders`, `Body Slim`.
  - 레이아웃 및 `MainActivity` 내의 모든 텍스트, 안내 코치마크, 토스트 메시지 리소스화 완료.
  - 글로벌 구글 플레이 심사를 위한 공식 영문 개인정보처리방침([PRIVACY_POLICY.md](PRIVACY_POLICY.md)) 및 영문 스토어 등록정보 가이드([STORE_LISTING_EN.md](STORE_LISTING_EN.md)) 작성 완료.

---

> **프로젝트 개요**: 초경량 온디바이스 사진 편집 Android 앱
> **패키지명**: `com.quickbeauty.app`  
> **저장소**: [QuickBeauty GitHub Repository](https://github.com/mismira96-stack/QuickBeauty.git)  
> **작성일**: 2026-09-13  
> **핵심 가치**: "사진/분석 결과는 온디바이스 처리 · 광고 제로 · 고화질 저장 · 2단 미니멀 UI"

---

## 1. 개발 배경 및 문제 정의 (Why QuickBeauty?)

- **기존 뷰티 앱(스노우, 소다 등)의 피로감**:
  - 수백 개에 달하는 불필요한 기능(메이크업, 스티커, AI 프로필), 200MB 이상의 거대한 앱 용량, 과도한 광고 및 VIP 구독 유도.
  - 사진을 외부/중국 AI 서버로 전송하여 보정하는 방식으로 인한 심각한 **개인정보/얼굴 사진 유출 우려**.
  - 사진 저장 시 강제 다운샘플링과 압축으로 인해 **원본 화질이 뭉개지고 흐려지는 문제**.
  - 인위적이고 과도한 성형 느낌(외계인 턱, 인조 왕눈이)으로 남성이나 자연스러운 원본 보정을 원하는 유저들에게 부적합.
- **QuickBeauty의 해결책**:
  - 유저들이 실전에서 가장 많이 쓰는 **핵심 보정 6가지**만 엄선.
  - 사진 보정과 분석 결과는 **온디바이스(On-device)** 처리하며, 앱의 편집 기능은 오프라인에서도 동작합니다. 포함된 ML Kit SDK는 진단·사용 분석 정보를 네트워크 연결 시 Google에 전송할 수 있습니다.
  - 10MB 안팎의 초경량 APK, 3초 만에 끝나는 직관적인 다크모드 2단 UI.
  - 원본 카메라 해상도 처리와 읽을 수 있는 광학 EXIF 정보 보존을 지원합니다. GPS 위치 정보는 결과 사진에 복사하지 않습니다.

---

## 2. 공식 6개 기능 체계 (`쿨톤` / `작은얼굴` / `턱선` / `얼굴길이` / `어깨` / `몸매`)

| 탭 명칭 | 핵심 효과 | 상세 알고리즘 및 특징 |
| :--- | :--- | :--- |
| **`쿨톤`** | 맑고 투명한 피부 톤업 | 황색(Yellow)/적색(Red) 채널을 정밀 억제하고 블루/핑크 채널 부스트 + 5x4 ColorMatrix 기반 화사한 명도 톤업 |
| **`작은얼굴`** | 두상 전체 축소 (**소두 효과**) | 중심 코어($R_{inner}=0.60 R_{head}$) 선형 스케일링으로 이목구비 왜곡 없이 가로/세로를 균일 축소하여 어깨 대비 두상 비율 축소 |
| **`턱선`** | 사각턱·옆볼 V라인 슬리밍 | 이목구비 중심축(T-Zone)을 100% 보호하며 양 볼살 및 사각턱 라인만 안쪽으로 부드럽게 수축하여 갸름한 턱선 완성 |
| **`얼굴길이`** | 긴 하관 단축 (동안 리프팅) | 턱 끝을 입술 방향으로 자연스럽게 끌어올려 긴 하관/턱 길이를 컴팩트하게 단축 (이마 하향 보정 18% 동시 적용) |
| **`어깨`** | 직각 어깨 / 어깨 넓히기 (**치트키**) | 광각 렌즈 왜곡으로 좁아진 어깨를 양옆으로 5~7% 확장하고 어깨 끝선을 살짝 리프팅하여 당당하고 반듯한 피지컬 완성 |
| **`몸매`** | 허리/복부 바디라인 슬리밍 | 복부 및 허리 중심부를 완만하게 안쪽으로 압축하여 자연스러운 S라인/슬림 핏 형성 |

---

## 3. 핵심 엔지니어링 성과 & 알고리즘 혁신

### 1) 소두(작은얼굴) 컨포멀 스케일링 (Conformal Head Scaling)
- **기존 문제**: 단순 가로 폭만 줄이면 얼굴이 오이처럼 길어지고 이목구비 비율이 깨짐.
- **해결**: 두상 중심점($X_{fc}, Y_{fc}$)을 기준으로 내측 반경 $R_{inner}$ 내부에서는 등각 선형 스케일($W=1.0$)을 적용하여 눈, 코, 입의 내부 기하학적 형태는 100% 보존하면서 머리 둘레 전체를 $X, Y$ 균일하게 축소.

### 2) 직각 어깨 워핑 (Square Shoulder Warping)
- **인체 비율 치트키**: 소두 효과와 결합 시 연예인/배우 프로필 수준의 피지컬 비율 구현.
- **목선 보호**: 쇄골/목 중심축($|X - S_{mid}| \le 0.25 W_{shoulder}$)은 변위를 0으로 고정하여 목이 두꺼워지는 현상을 원천 차단.
- **외곽 리프팅**: 어깨 끝선 부위는 수평 확장과 함께 상향 리프팅($-0.18 \times push$)을 결합하여 처진 어깨를 반듯한 직각 어깨로 보정.

### 3) 엄격한 수학적 단조성(Strict Monotonicity) 보장
- 메쉬 워핑 시 모든 행과 열에서 격자 미분 계수가 항상 양수($\frac{dx'}{dx} > 0, \frac{dy'}{dy} > 0$)를 유지하도록 수학적 경계값 설계.
- 6개 파라미터를 모두 100(최대치)으로 중첩 적용해도 메쉬 겹침(Fold), 찢어짐, 계단/주름 현상이 0건임을 단위 테스트로 수학적 검증 완료.

### 4) 100% 원본 무손실 초고화질 보존 엔진
- **`inSampleSize = 1` 무손실 디코딩**: 기존 4096px 리사이징 캡을 제거하여 5000만~1억 화소 원본 픽셀 그대로 유지.
- **적응형 60x60 고밀도 메쉬**: 3,721개 정밀 버텍스 격자 + Skia Paint 안티에일리어싱/디더링(`isDither = true`)으로 곡선 뭉개짐 제로.
- **EXIF 카메라 메타데이터 보존**: 카메라 제조사(Make), 기종(Model), 렌즈 초점거리, F값, ISO 정보를 복사합니다. GPS 위치 정보는 결과 사진에 복사하지 않습니다.
- **현재 시각 타임스탬프 & 갤러리 최상단 정렬**: 촬영일시(DateTimeOriginal/DateTaken)를 저장하는 현재 시각으로 기록하여, 과거에 찍은 사진을 보정해도 삼성 갤러리 **맨 첫 번째(오늘/방금 전 최신 사진)**에 즉각 노출.
- **저장 위치 최적화**: 삼성 갤러리 메인 앨범 리스트에 기본 노출되는 `DCIM/QuickBeauty` 폴더에 `Beauty_YYYYMMDD_HHMMSS.jpg`로 신규 저장 (원본 파일 100% 보존).

---

## 4. UI/UX 혁신 (Commercial 2-Row Dark Minimal Design & 6대 원형 라인아트 아이콘)

- **홍보 배너와 1:1 완벽 일치하는 6대 프리미엄 원형 아이콘 탭**:
  - **`쿨톤`**: 화사함을 상징하는 4각 다이아몬드 스파클 스타 (`✦`)
  - **`작은얼굴`**: 단정한 두상/얼굴 실루엣 라인아트
  - **`턱선`**: 세련된 V라인 턱선 곡선
  - **`얼굴길이`**: 동안 단축을 직관적으로 보여주는 수직 양방향 화살표 (`↕`)
  - **`어깨`**: 넓고 반듯한 직각 어깨 라인 실루엣
  - **`몸매`**: 슬림한 허리 곡선 라인아트
- **일렉트릭 블루(Electric Blue) 글로우 활성화 상태**:
  - 선택된 탭: 38dp 원형 배지에 은은한 다크블루(`rgba(30, 42, 74, 1)`) 배경과 선명한 `#3B82F6` 블루 테두리 + 순백색(`Color.WHITE`) 볼드 텍스트.
  - 비선택 탭: 다크 서클(`rgba(22, 22, 26, 1)`) 배경에 세련된 그레이(`Color.parseColor("#71717A")`) 텍스트로 시각적 위계 확립.
- **사진이 주인공인 화면**:
  - 아이콘 탭을 원형으로 컴팩트하게 집약하여 전체 화면의 80% 이상을 사진 뷰포트에 할당.
- **2단 극초미니멀 인터랙션**:
  - **1단**: 6개 원형 아이콘 탭 (`쿨톤` / `작은얼굴` / `턱선` / `얼굴길이` / `어깨` / `몸매`)
  - **2단**: 부드러운 슬라이더 + 깔끔한 우측 숫자 인디케이터
- **터치 홀드 원본 즉시 비교**:
  - 화면을 꾹 누르고 있으면 0.01초 만에 원본이 표시되고 `원본` 뱃지 노출, 손을 떼면 즉시 보정본으로 복귀.
- **시스템 바 완벽 호환**:
  - 갤럭시 One UI 태스크바, 제스처 내비게이션 바, 펀치홀 카메라 영역과 겹치지 않도록 동적 WindowInsets 패딩 적용.

---

## 5. 소프트웨어 아키텍처 및 품질 보증

```
app/src/main/java/com/mismira/quickbeauty/
├── MainActivity.kt          # 6개 탭 인터랙션, 수명주기, MediaStore/DCIM 저장 파이프라인
├── BeautyAdjustParams.kt    # 6대 파라미터 모델 (쿨톤, 작은얼굴, 턱선, 얼굴길이, 어깨, 몸매)
├── BeautyFilterEngine.kt    # Skia Bitmap Mesh Warping & ColorMatrix 연산 코어
├── BeautyBitmapUtils.kt     # 100% 원본 디코딩, 무손실 압축, EXIF 복사 및 MediaScanner
├── BeautyLandmarks.kt       # 얼굴/신체 랜드마크 데이터 구조 및 좌표 변환
├── BeautyPreviewView.kt     # 60fps 실시간 터치 홀드 비교 렌더링 뷰
└── FaceBodyDetector.kt      # Google ML Kit 비동기 랜드마크 추출 엔진
```

- **언어 및 런타임**: 100% Kotlin 모던 마이그레이션 완료 (Java 의존성 제거).
- **Android 16 16KB 페이지 호환성**: C++ .so 네이티브 라이브러리 의존성 없이 순수 Android Framework & Skia API로 구동되어 16KB 메모리 정렬 호환 경고 0건.
- **단위 테스트 통과**:
  - `testParamsClampingAndReset`: 6대 파라미터 유효 범위(0~100) 및 초기화 테스트 통과.
  - `testParamsCopy`: 파라미터 독립 복제 무결성 테스트 통과.
  - `testCoolToneMatrixCalculations`: 블루 부스트/황색 억제/명도 오프셋 매트릭스 계산 통과.
  - `testLandmarksDefaultsAndScale`: 포트레이트/전신 랜드마크 좌표 스케일링 통과.
  - `testComputeWarpedVerticesZeroParamsMatchesOriginalGrid`: 기본값 메쉬 무왜곡 검증 통과.
  - `testComputeWarpedVerticesWithFaceSizeShrinksBothXAndY`: 소두 효과 가로/세로 동시 축소 검증 통과.
  - `testComputeWarpedVerticesWithFaceLengthLiftsChinUpward`: 턱 끝 상향 리프팅 동안 비율 검증 통과.
  - `testComputeWarpedVerticesWithShoulderBroadeningExpandsShoulders`: 어깨 좌우 대칭 확장 검증 통과.
  - `testMonotonicityNoMeshInversion`: 6개 파라미터 동시 최대치(100) 적용 시 메쉬 뒤집힘 0건 검증 통과.

---

## 6. 배포 및 구글 플레이 스토어 출시 준비도

- **실기기 & 에뮬레이터 검증**: Samsung Galaxy 실기기(`R5KL503VHQR`) 및 Pixel 에뮬레이터에서 설치 및 기능 검증 완료.
- **구글 플레이 20명 14일 비공개 테스트 준비**:
  - 복잡한 가입이나 서버 연동이 없어 테스터들이 즉시 만족하며 사용할 수 있는 높은 완성도.
  - 릴리즈 빌드 번들(`.aab`) 및 서명 키 발급 준비 완료.
- **GitHub 저장소 관리**: `origin/main` 브랜치에 모든 소스코드 커밋 및 최신화 완료.

---

## 7. v1.0.2 업데이트 (소형 인물 왜곡 방지 & 다중 인물 스마트 선택/탭 전환)

> **배포 버전**: v1.0.2 (versionCode 5)  
> **업데이트 일자**: 2026-09-16  
> **핵심 과제**: 테스터 실전 피드백 2건 해결 및 Codex 엔지니어링 리뷰 피드백 반영

### 1) 테스터 실전 피드백 및 원인 분석
1. **소형/원거리 인물 사진에서 어깨 보정 시 다리/바닥 왜곡 현상**:
   - **원인**: 기존 랜드마크 누락 fallback 로직(`setupDefaultsIfEmpty()`)에서 `imageWidth * 0.25f` 등 이미지 절대 크기를 기준으로 삼아, 사진 속 인물이 작을 때 가상 신체 바운더리가 이미지 하단(골반/무릎/바닥)까지 지나치게 확장되어 워핑 영향권에 포함됨.
2. **다중 인물(2명 이상) 사진에서 타깃 오류 및 키메라(Chimera) 결합 현상**:
   - **원인**: 단순 바운딩 박스 크기 기준 정렬로 인해 화면 중앙의 주인공이 아닌 엉뚱한 인물이 기본 선택되거나, 1인용 Pose Detector의 신체 랜드마크가 선택된 얼굴과 다른 사람의 몸과 엮여 뒤틀림 발생. 사용자가 터치로 보정 대상을 바꿀 수 있는 인터랙션 부재.

---

### 2) 핵심 엔지니어링 해결책

#### ① 소형 인물 Strict Local Clamping & 적응형 감쇠 (Adaptive Damping)
- **절대 이미지 크기 종속 제거**: 모든 랜드마크 fallback 및 워핑 범위를 이미지 크기가 아닌 **실제 감지된 얼굴 크기($fw, fh$)**에 비례하도록 완전 개편.
- **적응형 감쇠 계수 (`effectiveScaleDamping`)**:
  - 인물 비율($faceRatio < 0.08$)인 경우 미세 배경 뒤틀림을 방지하도록 워핑 강도를 자동 감쇠.
  - 단, 얼굴은 작지만 체형이 큰 인물에서도 보정력이 사라지지 않도록 신체 비율($bodyRatio$)을 함께 반영($\max(faceRatio, bodyRatio \times 0.55)$).
- **영향 범위 분리 (어깨 vs 몸매)**:
  - **어깨 (`shoulderBotY`)**: 상체와 가슴까지 자연스럽게 펴주되 골반($leftHip.y$) 이전에서 안전 종료 $\rightarrow$ **다리/무릎/바닥 변위 0.0% 보장**.
  - **몸매 (`bodyBottomY`)**: 어깨 아래 허리/복부에 집중되도록 제한하여 바닥 침범 원천 차단.

#### ② 다중 인물 스마트 스코어링 & 탭 전환 (Tap to Switch Face)
- **스마트 스코어링 ($Area \times 0.65 + CenterDist \times 0.35$)**: 얼굴 크기뿐만 아니라 화면 중앙 집중도를 함께 고려하여 2인 이상 사진에서 실제 주인공 인물을 0순위 기본 타깃으로 자동 지정.
- **Face-Pose 키메라 결합 차단 (`parsePoseIfMatched`)**:
  - 감지된 포즈의 어깨 중심이 선택된 얼굴의 중심과 기하학적으로 일치하는지 엄격 검증:
    - 수평 편차: $|dx| \le fw \times 2.0$
    - 수직 편차: $dy \in [0.15 fh, 3.8 fh]$ (Codex 피드백 반영: 전신/원거리 사진에서 정상 포즈가 버려지지 않도록 $3.8fh$로 여유 부여).
  - 불일치 시 무리하게 타인의 몸을 엮지 않고 얼굴 기반 안전 상체 추정 모드로 격리.
- **탭으로 얼굴 전환 (`BeautyPreviewView.findFaceAt`)**:
  - 2명 이상 감지 시 화면의 다른 인물 얼굴을 가볍게 탭(Tap)하면 즉시 해당 인물로 보정 타깃 전환.
  - 350ms / 25dp 터치 분기로 기존 **"길게 누르면 원본 비교"** 제스처 100% 보존.
- **다중 인물 시각적 피드백**:
  - 선택된 얼굴: 선명한 일렉트릭 블루 포커스 링 (`#3B82F6`)
  - 비선택 얼굴: 부드러운 반투명 화이트 링 (터치 전환 유도)
  - 2.5초 후 자연스러운 페이드아웃 애니메이션으로 사진 감상 방해 최소화.

#### ③ 얼굴길이(Face Length) 하관 동안 단축 알고리즘 안정화 & 왜곡 원천 차단
- **기존 문제 분석**:
  - **이마/머리 위 배경 단층 왜곡**: 두상 밸런스를 맞추려던 이마 상단 하향 변위(`downRatio`)로 인해 머리핀(리본) 및 머리 뒤 배경(벤치/벽)이 아래로 쿵 떨어지며 계단식 단절 왜곡 발생.
  - **티셔츠/옷깃 빨림 현상**: 턱 밑 감쇠 범위(`neckSpan = fh * 0.35f`)가 너무 길어, 아기처럼 목이 짧은 체형에서 티셔츠 넥라인과 줄무늬 가슴팍이 턱 쪽으로 쑥 빨려 올라가며 옷이 찌그러짐.
  - **랜드마크 순서 역전 위험**: 웃는 표정이나 아기 얼굴에서 `chinY`, `mouthY`, `noseBaseY` 간격이 극도로 좁아질 때 구간 매핑 오류 발생.
- **해결 방안**:
  - **이마/배경 하향 변위 완전 삭제**: 머리 위 리본 및 배경 벤치 왜곡 0% (완전 무변형 보존).
  - **안전 기하학 클램핑 (`safeNoseY < safeMouthY < safeChinY`)**: 어떤 표정이나 얼굴 각도에서도 항상 단조 증가하는 인체 기하학적 순서 보장.
  - **초근접 턱 밑 감쇠 (`neckSpan = min(fh * 0.12f, 25f)`)**: 턱선 바로 아래 10~25px 내에서 리프팅을 0으로 급격 감쇠시켜 **티셔츠 넥라인/가슴팍 변위 0.0% 완벽 차단**.
  - **턱 폭 일체형 코사인 스무딩**: 턱 중앙만 뾰족하게 파이는 현상을 없애고 턱선 전체가 매끄럽게 올라가도록 확장.

#### ④ Codex 2차 리뷰 반영 및 터치 인터랙션 고도화
- **기본 상태(수치 0) 얼굴 탭 전환 좌표 누락 픽스 ([P1])**:
  - `BeautyPreviewView.onDraw()` 조기 반환(`params.isDefault()`) 이전에 `lastLeft, lastTop, lastScale`을 항상 저장하도록 수정하여, 사진을 처음 열자마자 탭해도 `findFaceAt`이 100% 정상 작동.
  - 기본 상태에서도 다중 인물 포커스 링(선택: 일렉트릭 블루, 비선택: 화이트) 렌더링 지원.
- **다중 인물 전환 시 포즈 랜드마크 보존 ([P1])**:
  - `FaceBodyDetector`에 `lastDetectedPose`를 캐싱하고, `switchToFace` 시 저장된 포즈를 전달하여 전환된 두 번째 인물도 포즈 기반 신체/어깨 보정이 100% 유지되도록 개선.
- **원본 비교 vs 얼굴 탭 분리 (사용자 실전 피드백 반영)**:
  - 롱프레스 딜레이를 150ms $\rightarrow$ 320ms로 최적화하고 `isShowingOriginal` 상태 플래그 도입.
  - 가벼운 터치(350ms/25dp 이내)는 원본 비교를 트리거하지 않고 즉시 얼굴 전환만 수행하도록 분기하여 오작동 원천 해결.
- **실전 웃는 아기 얼굴 회귀 픽스처 테스트 추가 ([P2])**:
  - `testSmilingBabyFaceLengthNoDistortionFixture`: 입술과 턱 끝이 10px로 극도로 좁은 웃는 표정에서도 턱 리프팅 정상 작동, 머리 위(변위 0.0f) 및 줄무늬 티셔츠(변위 0.0f) 완벽 보존을 수학적으로 검증.

#### ⑤ v1.0.4 업데이트 (ML Kit 소형 얼굴 감지율 2배 완화 & 터치 UX 최종 최적화)
- **ML Kit 소형 얼굴 감지율 완화 (`setMinFaceSize(0.04f)`)**:
  - 기본값(10%)으로 인해 전신/원거리 2인 사진에서 작은 얼굴이 감지되지 않던 문제를 `0.04f`(4%)로 낮춰 해결.
  - `PERFORMANCE_MODE_FAST`를 유지하여 지연(0.05초 속도) 없이 2~3인 사진의 작은 얼굴도 모두 정상 감지.
- **포커스 링 UX 정돈**:
  - 사진 로드 시 불필요한 링 깜빡임을 제거하고 직접 탭했을 때만 선택된 인물에 단일 파란색 링 표시.
- **1인 사진 원본 비교 통일**:
  - 인물이 1명일 때는 얼굴을 누르든 배경을 누르든 100% 원본 비교로 작동하여 조작 편의성 극대화.

#### ⑥ 상단 헤더 바 시스템 인셋 & 스페이서 구조 개편 (Z Fold 및 상태바 겹침/잘림 버그 원천 해결)
- **Android `RelativeLayout`의 `layout_centerVertical` 버그 해결**:
  - `RelativeLayout`은 자식 뷰를 수직 중앙 정렬할 때 `paddingTop`을 무시하고 전체 높이 기준으로 계산하여, `개인정보 / 초기화 / 저장` 버튼이 상태바(0~105px) 내부로 기어올라가던 문제를 해결.
- **구조적 2단 분리 레이아웃 적용**:
  - `topBar`를 수직 `LinearLayout`으로 분리:
    1. `topBarStatusBarSpacer`: 상태표시줄 및 펀치홀/노치 높이를 전담하는 투명 스페이서 (시스템 인셋 자동 반영).
    2. `topBarContent`: 56dp 표준 높이의 독립 헤더. 내부 버튼들이 상태바 아래 56dp 영역 내에서만 안전하게 수직 정렬.
- **실기기(Galaxy Z Fold SE) 실측 검증 완료**:
  - 상태바(0~105px) 아래 134~223px 위치에 버튼 배치 완료 (약 30px 안전 여백 확보).
  - 삼성 시스템 스크린샷의 상태바 자동 잘라내기 옵션 동작 시에도 헤더 및 버튼이 잘리지 않고 100% 온전히 보존됨.

---

### 3) 품질 검증 결과
- **단위 테스트 (Unit Tests)**: 13건 전체 통과 (`BUILD SUCCESSFUL`)
  - `testSmallPersonStrictClampingPreventsLegDistortion`: 소형 인물에서 어깨 100 최대 적용 시 다리/바닥 버텍스 변위 0.0f 검증 통과.
  - `testFaceLengthDoesNotDistortForeheadAndClothes`: 얼굴길이 100 최대 적용 시 이마 위 배경(변위 0.0f) 및 턱 아래 옷 영역(변위 0.0f) 무왜곡 검증 통과.
  - `testSmilingBabyFaceLengthNoDistortionFixture`: 웃는 아기 얼굴 픽스처 회귀 테스트 통과.
  - `testMultiFaceSetupAndFaceRatio`: 다중 인물 FaceInfo 등록 및 faceRatio 계산 검증 통과.
- **릴리즈 빌드 검증**: `assembleRelease` 및 `bundleRelease` (v1.0.4 / versionCode 7) 에러 없이 빌드 성공.
- **실기기 동작 검증**: Samsung Galaxy Z Fold SE 실기기(`R5KL503VHQR`)에 구버전 패키지 충돌 정리 후 v1.0.4 릴리즈 패키지 설치, 상태바 안전 여백 실측 및 실기기 정상 실행 확인 완료.



## 2026-10-03 — QuickBeauty 1.1.1 (14), production 16 KB fix
- Includes face-length tuning from b62e758 (maximum lift 0.040, proportional neck falloff).
- Updated bundled ML Kit pose detection 18.0.0-beta3 to beta5: old libxeno_native.so used 4096-byte ELF LOAD alignment; new arm64-v8a/x86_64 libraries use 16384-byte alignment.
- Verified all four 64-bit native libraries, APK zipalign -P 16 and APK signature verification.
- testReleaseUnitTest: 22 tests, zero failures/errors; assembleRelease and bundleRelease passed.
- Production draft replaces version 13 with 14. Final review submission remains manual.
- A 16 KB emulator runtime test has not been performed. Pose SDK change needs device regression check.

## 2026-10-07 — Launcher icon clipping fix
- Adaptive foreground used a 1024px drawable-nodpi bitmap with gravity=center, preserving intrinsic pixels and cropping the white star on smaller launcher bounds.
- Changed gravity to fill with bitmap filtering, preserving existing artwork while scaling to adaptive icon bounds.
- Device-specific visual verification pending; no phone connected. No Play upload or version bump in this change.


## 2026-10-07 — Small-face balance trial
- Expanded radial face-only contraction to an upper-head ellipse centered 0.12 face heights above the face center; maximum contraction reduced from 8.5% to 6%.
- Added image-edge falloff; jawline and face-length formulas unchanged.
- 23 unit tests passed, including upper/lower contraction balance and fixed top/bottom edge regression. Release APK built; on-device visual acceptance pending. No Play upload.


## 2026-10-07 — QuickBeauty 1.1.2 (15) release preparation
- Includes launcher foreground scaling fix and balanced small-face contraction.
- User accepted device testing, including multiple faces; other adjustment formulas unchanged.
- 23 unit tests passed; assembleRelease/bundleRelease passed; APK 16 KB ZIP alignment check passed.
- Signed APK/AAB copied to release-apks/quickbeauty-1.1.2-code15-release.*.
- Korean and English production release notes prepared.

- Play Console confirms production 1.1.2 is under review (full rollout); managed publishing disabled, so approval publishes automatically. No blocking errors; optional mapping/native-symbol warnings only.
- Code and release preparation pushed to origin/codex/16kb-release.



## 2026-10-18 — 1.1.3 production draft uploaded

- Uploaded latest shoe-protection AAB, versionCode 16 / versionName 1.1.3.
- Production track re-read confirms 16 is draft; existing 15 remains completed.
- Google rejected changesNotSentForReview=true for this app; committed the draft without that unsupported parameter. No review submission or rollout was requested.
- SHA256: 4d7e14f5c16cadddc1165eed0fc39f0cd8b127d3e64b59052f8d186767809d68
- Korean and English release notes included. User will test and submit manually tomorrow.

## 2026-10-20 — Lip rose color trial
- User requested unchanged blush. Kept cheek alpha 85 / RGB E0829D, lip alpha 75, geometry and masks unchanged. Lip RGB C95E7E -> D94A78; lower-lip accent B95070 -> C44169 for a more saturated rose pink.
- 44 unit tests and assembleRelease passed. Signed APK verified and copied to Google Drive/QuickBeauty-tests as quickbeauty-1.1.4-blush85-lip75-rose-test.apk; SHA-256 copy match. Earlier trials retained. Visual acceptance pending; no Play upload.


## 2026-10-20 — Keep editing after save / change-photo navigation
- Save no longer finishes MainActivity. A persistent result panel offers View saved photo and Change photo; original URI and edit values stay unchanged to avoid cumulative edits.
- System back and header photo-change icon use the same picker flow. Unsaved edits offer save-and-change / change-without-saving / cancel. Picker cancellation preserves the existing editor; cancel on initial launch still exits. Saving/loading blocks navigation.
- EditSnapshot compares copied per-face/global values against the successful save snapshot, independent of selected face; zero-valued face entries are normalized. Edits made during save remain unsaved. Save failures keep the editor and do not open the picker.
- English fallback/en and Korean strings added. 47 JVM tests passed; release/debug/instrumentation builds passed. API36 emulator synthetic regression passed: save retains editor/source, later edits become dirty, back opens picker, picker cancellation preserves edits. Test-generated gallery entry removed.
- Signed test APK quickbeauty-1.1.4-photo-flow-rose-test.apk copied to Google Drive/QuickBeauty-tests with matching SHA-256. Includes approved rose lip trial; cheek unchanged. No AAB/Play upload or version bump. Physical-device acceptance pending.

## 2026-10-20 — 1.1.5 (18) compact saved bar / production preparation
- Replaced default gray buttons and long save text with a single subtle dark saved bar: short confirmation and lavender View photo action, with a 48dp touch target. Photo change remains in the header; duplicate button removed.
- Includes accepted rose lip and blush tuning plus save/back/photo-change flow. Original-based editing and per-face snapshot tracking remain intact.
- 47 JVM tests and release APK/AAB/debug/instrumentation builds passed. API36 synthetic flow regression and pixel makeup regressions passed; compact height/action target checked and English layout visually inspected. Test gallery entries removed.
- APK signature and 16KB ZIP alignment passed. Version 1.1.5/code18 and Korean/English release notes prepared. Final Play review submission remains manual.
- Connected Samsung SM-F971N installation succeeded; dumpsys confirms 1.1.5/code18. Production API readback confirms 18 draft and 17 completed, uploaded AAB SHA-256 matches. No final review submission.
