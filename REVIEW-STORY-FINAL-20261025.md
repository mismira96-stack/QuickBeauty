# Antigravity 리뷰 요청 — Story 최종 정리 (2026-10-25)

실제 소스: C:\work\projects\QuickBeauty
현재 UI는 단일 CLEAN_PURE 캔버스. 템플릿 추가/스티커 추가는 범위 밖이다.

## 이번 최종 변경
- 문구와 날짜 초기 배경 및 저장 상태가 없을 때 복원 기본값을 NONE으로 통일.
- Activity / Editor / Renderer 기본 인자까지 NONE. 사용자가 선택한 AUTO/WHITE는 회전·재생성 때 유지.
- 하단 배경 버튼에서 선택 대상별 자동/흰 바탕/없음 변경 가능.
- 선택 시 보라색 외곽선은 편집 표시이며 내보내기에 포함하지 않는다.

## 사진과 글자의 아래쪽 영역
- 문구/날짜는 1080×1920 좌표에서 y=200..1670 안전 영역 내부로 제한한다.
- 사진은 인스타 UI 뒤쪽까지 이어진다. CLEAN_PURE 프레임은 (16,24)..(1064,1896): 가장자리의 얇은 흰 테두리 의도 유지.
- 인스타 UI가 사진 아래를 가릴 수 있으므로 핵심 문구만 위 영역에 둔다. 안전 영역이 저장 결과를 잘라내는 것은 아니다.

## 우선 검토 파일
- app/src/main/java/com/mismira/quickbeauty/SnsStoryActivity.kt
- app/src/main/java/com/mismira/quickbeauty/StoryEditorView.kt
- app/src/main/java/com/mismira/quickbeauty/StoryRenderer.kt
- app/src/main/java/com/mismira/quickbeauty/StoryLayout.kt
- app/src/main/java/com/mismira/quickbeauty/QuickBeautyDialog.kt
- app/src/main/java/com/mismira/quickbeauty/MainActivity.kt
- app/src/androidTest/java/com/quickbeauty/app/SingleStoryRegression.kt

## 검증 이력과 리뷰 포인트
- 직전 APK 계측 통과: 긴 한글/영문/이모지(최대 30 grapheme), 세로 왕복 위치 보존, 축소 여백 대칭, 실제 1:1 프리뷰와 렌더러 픽셀 동일 및 저장 PNG 디코딩 동일.
- 직전 계측 로그: app/build/story-final-png-device.log, story-final-direct-device.log, story-text-stress-result.log.
- 이번 기본값 변경에 맞춰 계측 assertion도 NONE으로 갱신. 이번에는 전체 계측을 재실행하지 않고 빌드/단위/Lint 검증한다: app/build/story-none-final-checks.log.
- 저장 없이 진입은 현재 보정 결과 PNG 캐시를 사용하며 갤러리에 쓰지 않는다. 원래 보정 화면으로 돌아오면 편집 보존.
- 실제 Instagram 수신/열기는 사용자 기기 확인이 아직 필요하다. 자동화가 전송 완료까지 검증했다는 의미가 아니다.
- 요청: 기본값/복원 일관성, 날짜와 문구 독립성, 안전 영역과 사진 프레임 구분, 캐시/FileProvider 수명, IME 편집 확대 및 PNG 메모리를 중심으로 리뷰. 문제 지적 시 파일/라인/재현 단계 제시.

전달 APK: QuickBeauty-story-final-none-debug-20261025.apk (폰 설치 및 Downloads, Drive QuickBeauty-tests)

## 최종 후속 수정 — 배경 기본값/전체 캔버스 이동/인스타 재진입

이 절이 위의 안전 영역 및 기본값 검증 설명보다 우선한다.
- 문구·날짜 모두 NONE 기본값. 이전 버전의 저장된 화면 상태에 있던 암묵적 AUTO가 되살아나지 않도록 backgroundDefaultsVersion=1로 복원 상태를 구분한다. 새 버전에서 명시적으로 고른 배경은 화면 재생성에도 보존한다.
- 배경 메뉴 순서는 없음/흰 바탕/자동. enum ordinal에 의존하지 않고 명시적 modes 목록으로 상태와 선택을 연결한다. 하단 버튼은 현재 모드도 표시한다.
- 날짜 선택 표시를 둥근 테두리에서 사각 편집 외곽선으로 변경해 실제 알약 배경과 구분한다. 이 외곽선은 저장되지 않는다.
- 사용자 요청으로 상단/하단 Instagram 안전 영역에 대한 위치 제한과 보라색 영역 안내를 제거했다. y=0..1920 전체를 사용한다. 캔버스 밖 잘림 방지만 유지하며 사진 프레임은 기존과 같다. 문구 30 grapheme 제한은 변경하지 않는다.
- Instagram 공유 파일은 매번 별도 PNG URI. 명시적 읽기 권한 부여와 NEW_TASK|CLEAR_TOP으로 공유 핸들러에 재진입한다. 인스타 메인 화면을 먼저 강제로 여는 동작이나 CLEAR_TASK/강제 종료는 사용하지 않는다.
- 폰에서 ADD_TO_STORY 핸들러가 CustomStoryShareHandlerActivity로 정상 해석됨을 확인했다. 실제 Instagram의 기존 화면 상태별 새 사진 수신은 아직 별도 실기기 검증이 필요하며 완치로 단정하지 않는다.
- Android Intent 플래그 근거: https://developer.android.com/reference/android/content/Intent#FLAG_ACTIVITY_CLEAR_TOP
- 이번 빌드/단위/Lint: app/build/story-background-fix-checks.log
- 이번 계측: app/build/story-background-fix-device.log (최종 결과 참고). 문구와 날짜의 초기 렌더링 plan이 각각 배경 null/투명인지 직접 assertion 추가.
- 이동 회귀: StoryLayoutTest.textCanReachFormerInstagramFooter에서 문구가 y=1920 하단 및 y=0 상단까지 갈 수 있음을 검증.
- 최종 APK: QuickBeauty-story-background-fix-debug-20261025.apk

최종 결과: story-background-fix-device.log 계측 통과. 문구/날짜 초기 배경 NONE과 투명 렌더링, 각 배경 선택 독립성, 회전 복원, 가로/세로 왕복, 실제 IME 편집 확대, 드래그/사진 핀치, 1:1 프리뷰 및 저장 PNG 픽셀 동일 검증 통과. 단위 테스트 59개 및 Lint 통과. 실제 폰 재설치/Downloads push/Drive 업로드 SHA256 일치: F18ACAF3C886D3BF053AD7DA1A2FEF785443484BEA90C4004549E13E6827DBB9. Drive 파일 ID: 140PQ54hT0JGOfkZX9TsrbM7rnlffI9rg.

## 최종 UI 후속 — 인스타식 조합 프리셋 / 사진 자유 이동 / 웜화이트

이 절이 이전 배경 선택창/날짜 테두리 설명보다 우선한다.
- 날짜 및 문구 선택 외곽선 모두 제거. NONE에서는 렌더링 배경도 없고 선택 테두리도 없다. 선택 대상은 하단 크기 표시와 스타일 샘플 선택 상태로 확인한다.
- 긴 '배경 · 없음' 버튼과 별도 서체/배경 팝업 제거. 크기 아래 Aa 샘플 6개: 고딕/손글씨/명조 × 배경없음/흰 알약. 실제 서체/바탕 미리보기로 한 번에 적용한다.
- 날짜 서체를 dateStyle로 독립 상태화. 문구에 적용하면 날짜는 바뀌지 않고, 날짜에 적용하면 문구는 바뀌지 않는다. 화면 복원 및 프리뷰/PNG 저장 모두 동일 인자를 사용한다.
- 기본 캔버스 배경은 사용자 지정 #FAF9F7. 흰 알약 선택 배경은 계속 흰색이다.
- 사진 offsetX/Y를 추가해 사진이 프레임에 꽉 맞아도 왼쪽으로 더 이동 가능. 오른쪽에 세로 문구용 여백을 만들 수 있다. 최소 64px은 보이게 제한해 사진이 완전히 사라지지 않게 한다. 기존 3개 좌표 복원도 호환하고 새 상태는 5개 좌표를 저장한다.
- 사진 pinch copy는 offset을 유지. 프리뷰와 렌더러의 StoryLayout.photo를 공통 사용.
- 빌드/단위/Lint: app/build/story-formats-checks.log. 사진이 정확히 프레임 비율일 때 왼쪽으로 이동해 오른쪽 여백이 생기는 단위 테스트 추가.
- 계측: app/build/story-formats-device.log. 선택된 날짜 enabled/disabled 실제 View 비트맵을 비교해 선택 테두리 없음 검증. 프리셋 실제 버튼 클릭으로 문구/날짜 독립 적용 및 NONE 복귀 확인.
- APK: QuickBeauty-story-formats-debug-20261025.apk

최종 검증 결과: story-formats-device.log 계측 통과. 문구/날짜 NONE 초기 렌더링 및 선택 날짜의 테두리 없음(실제 View 픽셀 비교), 6개 조합 프리셋 버튼 적용과 독립 상태, 날짜/문구 크기와 이동, 사진 pinch, 실제 IME 확대, 화면 재생성, 가로/세로 왕복, 1:1 프리뷰 및 저장 PNG 픽셀 동일 통과. 로그의 최종 요약 문구 일부는 이전 팝업 이름이 남아 있으나 테스트 코드는 inline formatButtons를 검사한다. 빌드/단위60개/Lint 통과. 폰 설치/Downloads/Drive APK SHA256 모두 27FCBF5AE24CD68B852FBC08EB2EDA7F2D63D110E75839A0F2C4D27369C9C9C4로 일치. Drive 파일 ID 1HZMaG_7DsMYsf-8v1x2Nbr3dntA_z5y4.
