# 퀵뷰티(QuickBeauty) 개인정보처리방침

**시행일: 2026년 9월 14일**

퀵뷰티는 선택한 사진을 기기에서 편집하는 Android 앱입니다. 앱 개발자는 계정이나 광고 목적으로 사용자의 개인정보를 수집하지 않습니다. 다만 앱에 포함된 Google ML Kit SDK가 진단 및 사용 분석 정보를 Google에 전송할 수 있습니다. 이 방침은 앱과 포함된 SDK의 데이터 처리를 함께 설명합니다.

## 1. 사진과 편집 데이터

- 사용자는 Android 시스템 사진 선택기 또는 다른 앱의 공유 기능으로 편집할 사진을 직접 선택합니다. 앱은 전체 사진 보관함을 읽는 권한을 요청하지 않습니다.
- 선택한 사진과 얼굴·신체 분석 결과는 기기에서 처리됩니다. 사진 내용, 얼굴/신체 랜드마크 및 편집 결과를 앱이나 ML Kit가 Google 서버로 보내지 않습니다.
- 사용자가 저장을 누르면 결과 사진이 기기의 `DCIM/QuickBeauty` 폴더에 저장됩니다.
- JPEG 결과에는 원본에서 읽을 수 있는 카메라 정보 등 일부 EXIF 메타데이터를 복사합니다. GPS 위치 정보는 저장된 결과에 복사하지 않습니다.

## 2. Google ML Kit 진단 정보

앱은 얼굴 감지 및 포즈 감지에 Google ML Kit를 사용합니다. 분석은 기기에서 수행되고 사진이나 분석 결과는 Google에 전송되지 않습니다. Google의 ML Kit SDK는 진단과 사용 분석을 위해 기기 제조사·모델·Android 버전 및 사용 가능한 하드웨어 가속기, 앱 패키지명과 버전, 설치 단위 식별자, 기능 실행 이벤트, 입력·출력 크기와 설정, 처리 시간 및 오류 코드를 Google 서버에 전송할 수 있습니다. 포즈 감지 SDK는 원격 구성 및 설치 진단도 사용할 수 있습니다. Google은 해당 진단 데이터가 전송 중 암호화되며 제3자와 공유되지 않는다고 안내합니다.

자세한 사항은 [ML Kit 데이터 공개 안내](https://developers.google.com/ml-kit/android-data-disclosure)와 [ML Kit 개인정보 안내](https://developers.google.com/ml-kit/terms)를 확인해 주세요.

## 3. 권한과 기기 저장

- 사진 선택은 Android 시스템 사진 선택기의 선택 항목에만 접근합니다. 앱은 `READ_MEDIA_IMAGES` 또는 `READ_EXTERNAL_STORAGE` 권한을 사용하지 않습니다.
- 앱은 사진의 GPS 위치 정보를 읽거나 저장하지 않으며 `ACCESS_MEDIA_LOCATION` 권한을 요청하지 않습니다.
- 저장된 결과는 기기의 갤러리에서 볼 수 있으며, 앱 개발자는 기기의 사진에 접근할 수 없습니다.

## 4. 보관, 제공 및 문의

사진과 편집 결과는 기기에서 사용자가 관리합니다. 앱 개발자는 이를 서버에 보관하거나 제3자에게 제공하지 않습니다. Google ML Kit 진단 정보 처리 방식은 위 Google 안내를 따릅니다.

개인정보 처리 관련 문의: **mismira96@gmail.com**

---

# QuickBeauty Privacy Policy

**Effective Date: September 14, 2026**

QuickBeauty is an on-device photo editing Android application. The developer does not collect, store, or transmit your personal data, photos, or facial information for user accounts, advertising, or tracking. However, the integrated Google ML Kit SDK may transmit diagnostic and telemetry data to Google. This policy explains how data is handled by the app and its third-party SDK.

## 1. Photos and Editing Data

- **Permissionless Photo Selection**: Photos are chosen explicitly by the user using the Android system Photo Picker or shared directly from other apps (e.g., Gallery). The app never requests broad access to your media storage (`READ_EXTERNAL_STORAGE` or `READ_MEDIA_IMAGES`).
- **100% On-Device Processing**: All image processing, facial landmark detection, and body pose analysis occur strictly locally on your device. Neither the original image, facial landmarks, nor edited photos are ever uploaded to any external server.
- **Saving Outputs**: When you tap Save, the resulting image is saved locally to your device's `DCIM/QuickBeauty` directory.
- **EXIF Metadata & Privacy**: When saving JPEG photos, safe optical metadata (such as camera model, focal length, orientation) is preserved to maintain image quality. However, GPS location data is intentionally stripped and never copied to edited photos.

## 2. Google ML Kit Telemetry & Diagnostics

QuickBeauty utilizes Google ML Kit (Face Detection and Pose Detection) purely for on-device feature analysis.
Google's ML Kit SDK may transmit non-personally identifiable diagnostic and telemetry data to Google servers to monitor SDK reliability and performance. This data may include device hardware details (make, model, OS version, hardware accelerators), app package name and version, installation identifiers, processing duration, and error codes. Google encrypts telemetry data in transit and handles it under Google's privacy terms.

For more details, please review:
- [Google ML Kit Data Disclosure](https://developers.google.com/ml-kit/android-data-disclosure)
- [Google Privacy Policy](https://policies.google.com/privacy)

## 3. Permissions & Device Storage

- The app does not request or require dangerous storage permissions.
- The app does not read or request access to device location (`ACCESS_MEDIA_LOCATION` / `ACCESS_FINE_LOCATION`).
- The developer has no backend servers and no access to your edited photos or camera roll.

## 4. Contact Us

If you have any questions or concerns regarding this Privacy Policy, please contact:  
**Email**: **mismira96@gmail.com**

