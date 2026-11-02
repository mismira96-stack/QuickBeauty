---
name: feature-design-review
description: >-
  Use this skill when drafting new feature design specifications (DESIGN-*.md),
  standardizing design document formats, or performing cross-AI reviews between Antigravity
  and Codex. Defines the standard design doc template, review checklist, and personas.
---

# Feature Design & Cross-AI Review Skill (설계 및 AI 협업 리뷰 표준)

이 스킬은 **Antigravity(Gemini)** 와 **Codex(또는 기타 AI 어시스턴트)** 가 프로젝트 기능 설계 문서를 작성하고 상호 검토(Peer Review)할 때 준수하는 표준 절차 및 문서 양식을 정의합니다.

---

## 1. 파일 위치 및 협업 공유 원칙

1. **설계 문서 저장 위치**:
   - 프로젝트 루트 디렉터리에 `DESIGN-<FEATURE-NAME>.md` 형식으로 저장합니다.
   - 예: `C:\Users\mismi\Documents\Codex\QuickBeauty\DESIGN-SNS-STORY-TEMPLATE.md`
   - 상위 작업 디렉터리(`C:\Users\mismi\Documents\Codex\`)에도 사본을 제공하여 타 AI/작업 환경에서 손쉽게 접근할 수 있도록 합니다.

2. **AI 상호 참조 파일 목록 (Review Targets)**:
   - **UI/UX 레이아웃**: `app/src/main/res/layout/*.xml` (특히 진입점 패널, 툴바 등)
   - **문구 및 다국어 리소스**: `app/src/main/res/values/strings.xml`, `values-ko/`, `values-en/`
   - **핵심 액티비티 및 상태 제어**: `MainActivity.kt` (상태 초기화, 제스처 충돌, 화면 전환)
   - **이미지 처리 및 렌더링 파이프라인**: `BeautyPreviewView.kt`, 비트맵 합성 렌더러
   - **외부 연동/인텐트 명세**: `AndroidManifest.xml` (FileProvider, 외부 인텐트 쿼리)

---

## 2. 크로스 AI 리뷰 체크리스트 (Codex & Antigravity)

설계 문서를 검토하거나 구현 코드를 리뷰할 때 다음 4대 관점을 필수로 점검합니다:

### ① 톤앤매너 및 페르소나 (Tone & Persona)
- [ ] **‘사진으로 이것저것 해보는 사람’**의 자연스러운 말투와 실사용자 관점을 유지하고 있는가?
- [ ] 기능/버튼 명칭이 개발자 중심 용어나 과장된 마케팅 문구가 아닌, 사진 찍고 꾸미기를 즐기는 사용자의 직관적인 언어로 되어 있는가?

### ② 사용자 경험 중심성 (Experience over Promotion)
- [ ] 앱 홍보나 기능 나열보다 **실제 사용자가 사진을 다듬고 공유하는 실제 경험 흐름**이 매끄러운가?
- [ ] 기존의 핵심 가치(예: 1초 초고속 보정 및 저장)를 해치지 않는 **선택적(Optional) 진입 동선**인가?

### ③ 다양성과 비획일성 (Non-Monotonous Variety)
- [ ] 템플릿, 프리셋, 프리뷰 결과물이 단조롭거나 천편일률적이지 않고, 각기 다른 무드와 활용 목적을 제공하는가?
- [ ] 기본값(폰트, 컬러, 레이아웃)이 유저의 다양한 사진 스타일에 자연스럽게 녹아드는가?

### ④ 기술적 구체성 및 예외 처리 (Flow & Edge Cases)
- [ ] **화면 전환 및 뒤로가기/닫기**: 작업 중 이탈 시 데이터 보존/확인 다이얼로그 처리.
- [ ] **메모리(OOM) 방어**: 편집 화면은 다운샘플링 미리보기를 사용하고, 최종 저장/전송 시에만 고해상도(1080×1920)로 합성하는가?
- [ ] **외부 앱 미설치 예외**: 인스타그램 등 연동 앱이 없을 때 `ACTION_SEND` (시스템 공유) 대체 흐름이 마련되어 있는가?
- [ ] **권한 및 FileProvider**: Android 10+ Scoped Storage 및 임시 공유 URI 권한 부여가 정확한가?

---

## 3. 표준 설계 문서 양식 (Design Document Template)

새로운 기능을 기획/설계할 때 아래 템플릿을 기본 구조로 사용합니다:

```markdown
# [Project Name] — [기능명] 설계안 (Design Specification)

> **문서 상태**: 검토용 초안 (Draft for Codex Review) / 확정 (Approved)
> **작성일자**: YYYY-MM-DD
> **핵심 원칙**: [예: 극도로 가볍고 단순하게, 1초 보정 UX 보존 등]

---

## 1. 배경 및 목표 (Background & Goals)
- **배경**: 사용자의 실제 니즈 및 기존 사용 시의 불편점
- **목표**: 달성하고자 하는 구체적 가치 2~3가지
- **비목표(Non-goals)**: 복잡도를 낮추기 위해 의도적으로 제외할 영역

---

## 2. 사용자 경험 및 진입 동선 (UX Flow)
- 메인 화면에서의 진입 경로 (주 경로 / 보조 경로)
- 화면 간 전이 및 취소/완료 라이프사이클 흐름도 (텍스트 다이어그램 또는 mermaid)

---

## 3. 화면 UI 구조 (Wireframe)
- ASCII 아트로 상단 바, 캔버스, 제어부, 하단 액션 버튼 배치 표현
- 터치 인터랙션 및 제스처 동작 정의

---

## 4. 상세 기능 명세 (Feature Specifications)
- 지원 템플릿 / 프리셋 / 옵션 목록 및 디자인 세부 수치 (패딩, 폰트, 색상값)
- 입력 제어 (텍스트 길이 제한, 날짜 칩 원클릭 적용 등)

---

## 5. 기술 아키텍처 및 구현 방안 (Technical Architecture)
- 컴포넌트 구조 (Activity vs BottomSheetDialog)
- 이미지 렌더러 / 합성 파이프라인 (미리보기 vs 고해상도 저장)
- 외부 연동 인텐트 규격 (Instagram Stories Intent / System Share Fallback)
- 메모리 안전성 (OOM 방지 다운샘플링)

---

## 6. 다국어 리소스 명세 (Localization Table)
- UI에 노출되는 모든 텍스트 키값 및 기본 영어(en) / 한국어(ko) 번역문 테이블

---

## 7. 크로스 AI 검토 포인트 (Review Points for Codex & Antigravity)
- 상대 AI에게 피드백을 요청할 핵심 의사결정 사항 (3~4가지)
```
