# QuickBeauty 1.1.6 (19)

2026-10-25. 사용자 요청: 프로덕션 출시 및 검토 요청까지 진행.

- 보정 화면에서 저장 없이 선택적으로 Story 편집을 열 수 있다.
- 단일 #FAF9F7 웜화이트 캔버스, 사진 pinch 및 자유 이동, 가로/세로 문구.
- 날짜/문구 독립 크기·이동·서체·배경. Aa 프리셋 6개로 글꼴/배경 동시 선택.
- 기본 문구 배경 없음 + 흰 글씨 고정. 흰 알약 프리셋은 검은 글씨. 날짜 선택 외곽선 제거.
- 1080x1920 PNG 갤러리 저장/Instagram Story 공유.

검증: 단위 테스트 60개, release Lint 오류 0, release APK/AAB 및 debug 빌드 성공. APK v2 서명 검증 및 16KB ZIP 정렬 검사 통과. 직전 story-formats-device 계측에서 선택 날짜 테두리 없음/프리셋 독립 적용/IME/재생성/프리뷰 및 저장 PNG 픽셀 동일 통과. 마지막 문구 NONE 흰색 고정 수정은 renderer 분기 변경 후 빌드/단위/Lint 검증.

아티팩트: release/1.1.6/QuickBeauty-1.1.6-19.apk 및 .aab.
APK SHA256: 9A210EA17376D3B439BD4BC065058769DB5E67D58E92EAB39D644A1A7D30C0D0
AAB SHA256: 083D40DC7FE06A163C952A5E335094057FD503E6D88FFE86200A154F1F99C4B3

출시 증거: release/1.1.6/validation.json, commit.json, track-after.json, play-publish.log. 증거 파일이 생성된 경우에만 성공으로 판단. 심사 승인/일반 공개 완료는 API commit 성공과 구분한다.

실제 Instagram의 기존 화면별 새 미디어 수신은 별도 실기기 확인이 남아 있다. 마지막 debug 설치 시 사용자 폰 USB가 분리되어 설치하지 못했다. 이전 설치 APK는 유지된다.

Play 결과: validation 및 commit 성공. changesInReviewBehavior=ERROR_IF_IN_REVIEW로 기존 심사 취소 없이 제출했다. 새 edit으로 재조회한 production track에서 versionCode19/status completed 확인. 이는 프로덕션 전체 배포 설정과 검토 요청 제출 성공이며 Google 심사 승인/스토어 전파 완료를 별도 확인한 것은 아니다. 증거는 release/1.1.6/*.json. Drive APK: https://drive.google.com/file/d/1llUaoFRDjd9PnJj9tDxJJg_U7c9YYujN/view
