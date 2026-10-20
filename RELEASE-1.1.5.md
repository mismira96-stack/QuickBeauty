# QuickBeauty 1.1.5 (18)

- Refined blush strength (85) and rose-pink lips (75, D94A78); face/leg geometry unchanged.
- Saving keeps the original-based editor open. A compact saved bar offers View photo without duplicate large buttons.
- Back and the header photo-change icon open the picker, with save/discard/cancel for unsaved edits. Cancelling the picker preserves edits.
- Copied global/per-face snapshots track unsaved changes independently of face selection. Gallery viewing excludes QuickBeauty itself.

Validation: 47 JVM tests passed; release APK/AAB, debug and instrumentation builds passed. API36 synthetic Android regressions passed for save/back/picker cancellation, compact bar height and 48dp action target, makeup/warp rendering, preview/export consistency and neighboring face/lip masks. APK signature and 16KB ZIP alignment passed.

AAB SHA256: 3426e5f3dbf8fa4f8f577e458d5467ed165f5ae9c752972e2386193702ae0b62
APK SHA256: ab5174de79a4323cc498a662dd69be9cb068ca9747bedbbde724e0002f687c53

Production preparation only; final review submission remains with the user.
