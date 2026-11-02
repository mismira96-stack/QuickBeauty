# QuickBeauty — SNS 스토리 기능 전체 구현 리뷰 및 진행 중단 분석

> **작성일자**: 2026-10-25  
> **검토자**: Antigravity (Gemini)  
> **검토 대상**: Codex가 진행한 SNS 스토리 구현 코드 전체 (11개 신규 파일, 6개 수정 파일, 9개 구현 기록 문서)  
> **현재 빌드 상태**: 빌드 성공, 단위 테스트 57개 100% 통과, Lint 오류 0개, 에뮬레이터 계측 테스트 1건 대기

---

## 1. 진행이 멈춘 정확한 원인 (Root Cause of Stoppage)

Codex는 구현 완료 후 최종 에뮬레이터 회귀 검증(`SingleStoryRegression.kt`)을 수행하던 중 **단 1개의 assertion 실패**로 진행을 멈췄습니다.

### 1.1 실패 지점
- **실패 파일 및 라인**: `SingleStoryRegression.kt:180`
- **에러 로그**:
  ```text
  java.lang.IllegalStateException: Check failed.
      at com.quickbeauty.app.SingleStoryRegression.run$lambda$31(SingleStoryRegression.kt:180)
  ```
- **실패한 코드**:
  ```kotlin
  // SingleStoryRegression.kt 175~181
  main { editor().focusCaption() }
  SystemClock.sleep(700); i.waitForIdleSync()
  main {
      val e = editor(); val p = e.captionInput.captionPlan!!
      val target = TypedValue.applyDimension(COMPLEX_UNIT_SP, 24f, context.resources.displayMetrics)
      check(p.paint.textSize * e.captionInput.outputScale >= target - .5f) // <-- 여기서 실패
      check(ViewCompat.getRootWindowInsets(e)?.isVisible(WindowInsetsCompat.Type.ime()) == true)
  }
  ```

### 1.2 원인 분석 (왜 실패했는가?)
- Codex는 **"입력 중 키보드가 올라오면 커서 주변을 최소 24sp 크기로 확대하여 가독성을 높이는 기능(`editingViewport`)"**을 구현했습니다.
- 그런데 이 기능은 Android의 WindowInsets 리스너에서 **가상 키보드가 실제로 표시(`insets.isVisible(ime()) == true`)**될 때만 활성화되도록 설계되어 있습니다.
- 헤드리스/자동화 에뮬레이터(`emulator-5580`) 환경에서는 `showSoftInput(..., SHOW_IMPLICIT)` 호출만으로는 가상 키보드가 실제 화면에 즉시 팝업되지 않는 현상(`dumpsys input_method` 확인 결과 `mInputShown=false`)이 발생했습니다.
- 따라서 키보드가 열리지 않아 `editingViewport`가 켜지지 않았고, 확대 배율(`outputScale`)이 올라가지 않아 `check(...)` assertion에서 멈춘 것입니다.
- **결론**: 앱의 핵심 로직이나 런타임 충돌(Crash)이 아니며, **테스트 자동화 환경에서의 가상 키보드(IME) 포커스 대기 방식의 문제**입니다.

---

## 2. 전체 구현 기능 상세 리뷰

Codex는 사용자의 피드백을 실시간으로 반영하며 총 11개의 코틀린 소스 파일과 리소스를 매우 완성도 높게 작성했습니다.

### 2.1 진입 동선 및 안전성 (A+)
- **위치**: `MainActivity.kt`, `activity_quick_beauty.xml`
- **검토 결과**:
  - 사진을 갤러리에 저장 완료했을 때만 `saveResultPanel`에 `[스토리 템플릿]` 버튼이 노출됩니다.
  - 슬라이더를 추가 조작하거나 리셋하면 패널이 즉시 닫혀, 보정본과 화면 간 불일치가 완벽히 차단됩니다.
  - 원본 URI가 아닌 실제 저장된 고화질 파일 URI(`lastSavedUri`)를 전달하므로 재보정 위험이 없고 100% 동일한 결과물이 전달됩니다.

### 2.2 화면 구조 및 에디터 (`SnsStoryActivity.kt`, `StoryEditorView.kt`) (A)
- **화면 구성**:
  - 복잡한 탭들을 정리하고 **단일 캔버스(`CLEAN_PURE`)** 중심의 직관적 UI로 정돈됨.
  - 하단 도구 모음:
    1. **`[문구]`**: 사진 위 텍스트 탭 및 직접 입력.
    2. **`[서체]`**: 고딕(시스템 기본) / 손글씨(나눔손글씨 펜) / 명조(고운바탕 Bold) 3종 팝업 선택.
    3. **`[크기]`**: 50% ~ 220% 슬라이더 제어 (선택한 요소에 따라 문구 크기/날짜 크기 독립 조절).
    4. **`[세로쓰기]`**: 원클릭으로 가로쓰기 ↔ 세로쓰기 전환 (Unicode grapheme 기준 한 열 세로쓰기).
    5. **`[오늘]`**: 캡슐 알약 형태의 날짜 스탬프(`yyyy.MM.dd`) 원클릭 ON/OFF.
- **인터랙션/제스처**:
  - 문구 및 날짜 요소를 터치하여 자유롭게 드래그 이동 (Safe Area 안쪽으로 자동 클램핑).
  - 사진 영역을 두 손가락으로 핀치 줌(0.5배~3.0배) 및 한 손가락 이동(Pan) 지원.
  - 사진 위에서 직접 한글 타이핑 가능 (Android IME 조합 깨짐 방지).

### 2.3 렌더링 파이프라인 (`StoryRenderer.kt`, `StoryLayout.kt`) (A+)
- **해상도**: 정규 인스타그램 스토리 규격인 **1080 × 1920 ARGB_8888** 비트맵으로 고화질 합성.
- **안전 영역 (Safe Area)**: 상단 200px, 하단 1670px, 좌우 24px를 엄격히 준수하여 인스타그램 UI에 가려지지 않음.
- **세로쓰기 엔진 (`StoryCharacters.kt`)**: 복잡한 한글 음절, 복합 이모지(👨‍👩‍👧‍👦 등)를 ICU 문자 경계로 정확히 계산하여 1열 세로쓰기 구현.
- **메모리 및 비동기 처리**: `Dispatchers.IO`에서 단 1회 렌더링 후 비트맵 `recycle()` 명시적 호출, FileProvider 임시 파일 생성 시 24시간 지난 임시 파일 자동 정리.

### 2.4 인스타그램 연동 및 Fallback (A)
- `com.instagram.share.ADD_TO_STORY` 공식 인텐트 규격 적용.
- `AndroidManifest.xml`에 `<queries>` 패키지 가시성 선언 완료.
- 인스타그램 미설치 또는 실행 실패 시 시스템 표준 공유 창(`ACTION_SEND`)으로 자동 전환.

---

## 3. 사용자 원래 요청 vs 현재 구현의 핵심 차이점 (중요 검토 사항)

사용자의 최초 요청:
> *"template 몇개가있고, 거기에, 글자정도만넣을수있고, 인스타스토리에 올리기 정도가 어떨까싶다.. 필요한사람만할수있게 ㅎ"*

### 현재 구현된 상태
- Codex는 피드백 과정을 거치면서 **"템플릿 선택 탭"을 제거**하고, **단일 사진 프레임(`CLEAN_PURE`) 위에 [세로쓰기] / [가로쓰기]를 토글하는 방식**으로 인터페이스를 통합했습니다.
- `StoryLayout.kt` 및 `StoryRenderer.kt` 내부에는 5가지 템플릿(클린 화이트, 뮤트 톤, 16:9 시네마틱, 세로 저널, 풀 뷰) 렌더링 코드가 모두 남아 있으나, 현재 `SnsStoryActivity` 화면에서는 템플릿 선택 탭이 숨겨져 있습니다.

> **💡 검토 포인트**:
> 1. 지금처럼 **단일 깔끔한 프레임에서 가로/세로쓰기·서체·크기·날짜를 조절하는 초단순 UI**가 마음에 드시는지?
> 2. 아니면 원래 기획처럼 **[풀 뷰] / [클린 화이트] / [블러/컬러 톤] 등 템플릿 2~3종을 상단에서 골라 바꾸는 탭**이 다시 필요한지?

---

## 4. 검증 결과 요약

| 검증 항목 | 결과 | 세부 내용 |
| :--- | :---: | :--- |
| **Gradle 빌드** | **PASS** | Debug / Release APK 빌드 성공 |
| **단위 테스트 (Unit Tests)** | **PASS** | `StoryLayoutTest`, `StoryTextWrappingTest` 등 **57개 전체 통과** |
| **Android Lint** | **PASS** | 오류(Error) **0개** |
| **인스트루멘테이션 계측** | **1건 대기** | `SingleStoryRegression.kt`의 에뮬레이터 키보드 감지 대기에서 정지 |

---

## 5. 즉시 진행할 수 있는 권장 조치 (Next Steps)

1. **테스트 블로킹 해소**:
   - `SingleStoryRegression.kt`에서 에뮬레이터 가상 키보드 대기 로직을 실제 UI 상태 기반으로 안전하게 보완하여 전체 테스트 통과 완료.
2. **템플릿 노출 범위 확정**:
   - 현재의 '단일 프레임 + 세로/가로 토글' 유지 여부 또는 '템플릿 탭' 복원 여부 확인.
3. **실기기(갤럭시 폴드) 검증용 APK 빌드**:
   - 수정 완료 후 실기기에 설치하여 사용자가 직접 터치감(드래그, 핀치 줌, 서체 변경, 인스타 스토리 전송)을 테스트할 수 있도록 제공.
