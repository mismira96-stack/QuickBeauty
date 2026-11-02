# QuickBeauty 1.1.7 (20)

## Included
- Violet/blue A1 launcher icon and matching 512px Play icon. Adaptive and legacy resources updated; circle/rounded/square masks inspected and the user accepted the installed icon.
- Verified per-person shoulder/body adjustments in group photos. Face-owned cropped poses are checked by nose association, confidence and torso geometry. Unmatched bodies remain unavailable.
- Identified-body slimming extends across torso through pelvis with smooth transitions, fixed image edges and sibling face/verified torso protection. Per-person values survive selection and saving.
- Existing face adjustments, shared/global leg adjustment, Tone brightness and color formulas remain unchanged. Tone/brightness improvements explicitly deferred by the user.

## Validation
- 62 JVM tests, release APK/AAB build and release vital lint passed.
- APK signature and 16KB ZIP alignment passed.
- Connected Samsung fixture: 5 faces, 2 matched bodies including the foreground user. Body85 export and horizontal mesh monotonicity passed.
- User tested the installed trial and additional photos, reporting no issues and explicitly requested review submission.
- Limitations: pose association is conservative, not person segmentation. Overlapping arms/clothes/background can still deform; bodies are not guaranteed detectable in every group photo.

## Artifacts
- APK SHA256: e05b12c10324ccc98cf42803cd38fca56b854c1e22ef2687672a2ce8c14ec608
- AAB SHA256: b20978813ca9ab55700aa82faafe6791c5d04e69b1c5a90bf2c2a2313ee4f7b5
- Signed artifacts retained under C:/work/releases/QuickBeauty/1.1.7 and release/1.1.7 (binaries Git-ignored).
- Google review/publication evidence is recorded only after API commit and fresh-read verification succeed; see release/1.1.7/release-result.json when present. A completed API track is not, by itself, confirmation of Google approval or store propagation.

## Submission result
Validation and final API commit succeeded on 2026-11-02 with ERROR_IF_IN_REVIEW. Fresh read confirmed production20/completed rollout configuration and matching ko-KR icon. Other listing text/video/images/tracks unchanged. Review requested; approval and public-store propagation are NOT independently verified. No further user submit action is required for this request.
