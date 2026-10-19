# QuickBeauty 1.1.4 (17)

## Google Play 출시 노트

### ko-KR
생기 보정으로 은은한 볼터치와 립 컬러를 더할 수 있습니다.
생기를 얼굴·다리길이 보정과 함께 사용할 때 위치가 자연스럽게 맞도록 개선했습니다.
여러 얼굴의 선택과 개별 보정값 저장·초기화 동작을 개선했습니다.
쿨톤 보정의 밝기를 조정했습니다.

### en-US
Add a subtle blush and lip tint with Vitality.
Makeup now follows face and leg-length adjustments.
Improved face selection and saving or resetting individual face edits.
Adjusted brightness in the Tone filter.

## 검증

- 사용자 실기기 기본 테스트 통과 (2026-10-19).
- JVM 테스트 44건 통과.
- API36 에뮬레이터에서 생기+형태 보정 0/50/100, 미리보기/저장 일치, 원본 비교, 치아/입술 아래 피부/옆 얼굴 보호, 윤곽 누락, 고해상도 좌표 스케일링 검증 통과.
- 실제 Activity의 얼굴별 저장/초기화 버튼 검증 통과.
- 이번 출시는 16번과 구분되는 새 17번 번들이다. 최종 심사 제출은 사용자 확인 후 수동 진행한다.

## 기능 한계

- 실제 입술 윤곽을 얻지 못한 얼굴은 립 효과를 생략한다.
- 볼 보호 영역은 기하학적 마스크이며 피부 분할 모델이 아니다.
- 몸매/어깨는 기존 다중 인물 제한을 유지한다. 다리길이는 사진 전체에 적용된다.
