# 스토리 템플릿 v3 및 휴대폰 갤러리 선택

2026-10-24 / Codex 구현. 이전 블러·폴라로이드 구현 안내 대신 이 문서를 최신 변경 기준으로 사용한다.

## 변경

- Clean Margin / Soft Tone / Quiet Caption 3종으로 교체. 사진 전체를 비율 유지해 배치하고 블러·카드 그림자·둥근 액자를 제거했다.
- Clean Margin은 상단 한 줄, Soft Tone은 사진 아래 한 줄, Quiet Caption은 사진 하단 한 줄이다. 자동 얼굴 회피는 없다.
- Nanum Pen Script를 기본 문구 서체로 추가하고 `assets/licenses/NanumPenScript-OFL.txt`에 라이선스를 포함했다. 문구 기본값은 비어 있으며 날짜는 버튼을 눌렀을 때만 들어간다.
- 글자 읽기를 돕는 상단 흰색/하단 어두운 그라데이션은 문구가 있을 때만 적용된다.
- Soft Tone은 작은 간격으로 사진 픽셀을 샘플링해 대표색을 추출하고 저채도의 밝은 단색으로 만든다. 사진 자체 색은 변경하지 않는다.
- 현재 구조는 1080×1920 JPEG, 갤러리 저장, Instagram Stories 인텐트 및 일반 공유 대체 흐름을 유지한다. 실제 Instagram 수신 및 앱 ID 요구사항 검증은 여전히 남아 있다.

## 휴대폰 갤러리

기존 `ActivityResultContracts.GetContent()`의 범용 GET_CONTENT 요청을 교체했다.

1. Samsung Gallery (`com.sec.android.gallery3d`)가 사진 선택 요청을 처리할 수 있으면 해당 앱을 직접 연다.
2. 없으면 `ACTION_PICK` + MediaStore 이미지 URI를 처리하는 앱을 사용자가 고르는 창을 연다. 특정 앱의 기본값 때문에 Google Photos로 자동 이동하는 일을 피한다.
3. 처리할 갤러리가 없거나 실행 실패 시 로컬 파일 선택 요청으로 대체한다.
4. `EXTRA_LOCAL_ONLY`는 로컬 사진 요청 힌트이며 외부 앱의 실제 표시 내용을 강제하거나 기기의 기본 앱 설정을 변경하지 않는다.
5. 필요한 패키지·인텐트만 Manifest queries에 선언했다. 전체 사진 접근 권한은 추가하지 않았다.
6. 취소 시 원래 사진·보정값을 유지하는 callback 정책 및 갤러리의 공유로 들어오는 기존 흐름은 유지한다.

## 검증 범위

- PC에서 디버그 APK, Android 테스트 APK 컴파일, 전체 단위 테스트 및 Lint를 실행한다. 결과는 `app/build/story-v3-build.log` 및 최종 빌드 로그를 참조한다.
- 배치 테스트는 가로·세로·정사각·극단적인 비율에서 사진 비율 유지, 캔버스 범위, 캡션 안전 영역 및 Soft Tone의 사진/문구 분리를 확인한다.
- 기기 테스트 코드는 3종 템플릿 기준으로 갱신했으나 이번 변경에서는 사용자 휴대폰에서 실행하거나 APK를 설치하지 않았다.
- 어머니 기기의 삼성 갤러리 실행, 다른 제조사/Google Photos만 설치된 기기의 대체 선택, 선택·취소 및 원본 공유 흐름은 실제 기기 확인이 남아 있다.
- 기존 버전에서 확인한 MediaStore/화면 재생성 테스트 결과를 새 3종의 실기기 검증 결과로 간주하지 않는다.

## 수정 파일

- `StoryLayout.kt`, `StoryRenderer.kt`, `SnsStoryActivity.kt`
- `PhotoPickerIntents.kt`, `MainActivity.kt`, `AndroidManifest.xml`
- `res/values*/story_strings.xml`, `res/font/nanum_pen_script.ttf`, `assets/licenses/`
- `StoryLayoutTest.kt`, `StoryRegression.kt`

## Antigravity 리뷰 포인트

원본 보정 결과의 전달, 사진 비율과 문구 가독성, 샘플링된 대표색, 갤러리 선택 및 취소 흐름, OS 패키지 가시성, FileProvider 권한과 외부 연동 실패 처리를 검토한다. 앱 구현 코드를 동시에 수정하기보다는 별도 리뷰 파일로 전달한다.
