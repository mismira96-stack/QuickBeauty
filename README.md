# QuickBeauty (간편 뷰티 ✨)

> **초경량 100% 온디바이스 뷰티 필터 독립 Android 앱**  
> 외부 서버 전송 없이 내 스마트폰 안에서 0ms 지연시간으로 실시간 뷰티 보정을 수행합니다.

---

## ✨ 핵심 기능

1. **❄️ 쿨톤 피부 톤업**
   - 황색기(Yellow) 억제 및 핑크/블루 톤업
   - 5x4 ColorMatrix 기반의 화사한 명도 부스트 연산
2. **👤 얼굴 크기 축소**
   - Google ML Kit 얼굴 랜드마크 중심 자연스러운 방사형 수축
3. **✨ 턱선 V라인 & 리프팅**
   - 턱 끝 랜드마크 중심 2차 에르미트 감쇄(Smooth Hermite Falloff)를 통한 턱선 갸름화 및 턱 끝 리프팅
4. **🧍 몸매 슬림**
   - 어깨~골반~다리 라인 슬림 압축 및 배경 왜곡 방지 페이드아웃
5. **👆 꾹 누르면 원본 보기**
   - 프리뷰 터치 다운 시 원본 무보정 화면 즉시 노출, 손을 떼면 0ms 딜레이로 보정본 복귀
6. **⚡ 60fps 온디바이스 실시간 연산**
   - GPU 드라이버 호환성 이슈 없는 Skia 소프트웨어 비트맵 메시(`drawBitmapMesh`) + `ColorMatrix` 연산

---

## 📲 진입 및 사용 방식

- **삼성 갤러리 연동**:
  - 사진 감상 중 **[공유]** 또는 **[다른 앱으로 편집]** 터치 시 목록에 `간편 뷰티 ✨`로 즉시 노출
  - `ACTION_SEND`, `ACTION_SEND_MULTIPLE`, `ACTION_EDIT`, `ACTION_VIEW`, `ClipData` 모두 지원
- **홈 화면 단독 실행**:
  - 앱 아이콘 터치 시 삼성 갤러리 사진 선택기가 즉시 실행되어 사진 선택 후 바로 보정 시작
- **초고화질 저장**:
  - 보정 완료 후 [저장] 터치 시 최대 4096px 4K 고화질로 갤러리(`Pictures/QuickBeauty`)에 즉시 저장

---

## 🛠️ 기술 스택 & 개발 환경

- **Platform**: Android (Target SDK 36, Min SDK 29)
- **Language**: Java 17
- **AI/ML Engine**: Google ML Kit (Face Detection 16.1.7, Pose Detection 18.0.0-beta3)
- **Rendering**: Android Canvas Bitmap Mesh + ColorMatrix
- **Package**: `com.mismira.quickbeauty`

---

## 📄 라이선스
MIT License
