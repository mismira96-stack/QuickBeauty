# Front-facing two-person photo regression — 2026-10-17

## Follow-up: reported 948 and 827 failures

The user identified 20261017_165948.jpg and 20261017_165827.jpg. Both returned
zero selectable faces at the original 563 × 1000 preview size in three runs.
At 1920px both faces were detected, but the left person's 948 face failed the
40-row mesh spacing condition and the 827 face was below the 100px width limit.
The final preview target is now 2000px and the preview mesh is 60 × 60, matching
the export mesh. The three-cell coverage rule and 100px minimum are preserved.

Final left-face bounds: 827 = 104 × 137; 948 = 108 × 145. Both are editable.
All four fixtures pass three repeated detection and nonzero warp checks.
24 JVM tests pass, including the reported face sizes and existing mesh inversion
and adjacent-face protection checks. The earlier measurements below describe
the initial 1920px fix, not the final configuration.

The actual Activity test taps the left person's face and verifies all three
face sliders across four image loads. This passed after the final detection fix.

## Resolved: 757 small-face distortion

Fixture: Beauty_20261017_134757.jpg (two people seated in front of a red wall).
Visual inspection confirmed the reported issue: face height contracted by 31px
(chin pulled upward significantly because headCenterY is above face center),
while cheek width only narrowed by ~20px out of a wide smiling face. The
disproportionate vertical squash made cheeks look relatively plumper and squashed.

Fix applied in BeautyFilterEngine:
- Strengthened horizontal (X-axis) narrowing to 1.35x to slim cheekbones/cheeks.
- Softened vertical (Y-axis) compression to 0.70x to prevent height squashing.
- Post-fix measurement: cheek width contracted by 8.1% (scale 0.919), face height
  contracted by 4.2% (scale 0.958).
- Visual result: cheeks slim down smoothly into a natural oval contour without
  squashing the face vertically.
- Added unit test: testFaceSizeContractsCheekWidthMoreThanHeightToPreventPlumpDistortion.
- All 25 JVM unit tests pass, and full on-device UI regression runner passes.

Tested against the 1.1.2 source in this checkout on the connected Android device.
Fixtures are private local photos, excluded from the repository. Side-profile
photos were excluded from regression testing at the user's request.

## Reproduction

| Fixture | Before preview | Before detected face bounds | Face editing |
| --- | --- | --- | --- |
| 20261017_170017.jpg | 563 × 1000 | 61 × 81, 60 × 78 | Both disabled |
| 20261017_170018.jpg | 1030 × 1830 | 113 × 149, 108 × 144 | Both enabled |

Power-of-two decoding rounded the first 4000px image down to 1000px despite
requesting 1920px. Detected faces then failed the existing 100px editing minimum.
The group-photo message also took precedence over the small-face explanation.

## Changes

- Decode previews at or above the target and resize to the requested limit;
  retain the conservative sampling policy for high-resolution export.
- Clear previous face edits, comparison mode, slider values and detection state
  when loading; ignore late results from superseded image requests.
- Show the small-face explanation when appropriate and explain group-photo
  body restrictions specifically on the shoulder/body tabs.
- Use a separate debug application ID and the label `QuickBeauty 테스트`.

## Verification

After the fix both previews are 1080 × 1920. Detected bounds are 116 × 153 and
112 × 149 for the first photo, and 122 × 160 for both faces in the second.
Both faces pass the editing gate in three repeated detections per photo.
Small-face, jaw and face-length parameters produce nonzero mesh changes for
each detected face. All three sliders respond to accessibility progress actions
in the actual Activity across three image loads and reset to zero between loads.
All 23 existing JVM tests pass; debug and instrumentation APK builds pass.

The custom instrumentation runner accepts `directory` and `prefix` arguments.
Copy these two fixtures to the debug app's internal `files/regression` directory
and run `com.quickbeauty.app.debug.test/com.quickbeauty.app.PhotoRegressionRunner`.
It does not save edited photographs or modify fixture files.

## Limits

Intermittent complete face-detection failure was not reproduced on these two
front-facing fixtures. Reload state fixes are defensive; they do not establish
the cause of every reported intermittent failure. Multi-person shoulder/body
editing remains disabled under the existing safety policy. The production app
was not replaced; the tested changes are installed as a separate debug app.


## 2026-10-18 completed review (supersedes earlier verification/status)

- User clarified that small-face changes need a magnified preview, not greater
  effect strength. Added pinch zoom (1–6x), double-tap (3x / fit), bounded pan,
  and hold-to-compare at the same transform. Face taps use transformed hit areas.
  Changing faces retains zoom; a new photo resets it. Export is unaffected.
- Moved leg anchors into global pose state, separate from selected-face body
  fallback estimates. Leg detection validates confidence, frame coordinates,
  and hip/knee/ankle ordering. Face selection no longer moves the leg warp.
- Preserve all detected face rectangles for leg overlap protection, including
  faces too small to select. Scale this protection consistently for export.
  The message now describes overlapping faces rather than claiming to detect
  sitting people. This is not a general standing/sitting classifier.
- Removed the forced minimum leg displacement that folded the bottom mesh when
  the ankles had little space beneath them. Shared leg edits are applied once.
- Korean/default-English/English resource keys match (35 each); added zoom hint,
  corrected missing-leg English text, and allowed centered multiline hints.
- Image VIEW filter now explicitly supports content/file URIs; lint has zero
  errors, with 60 warnings remaining (not a warning-free review).

Validation: 36 JVM tests pass. Galaxy device tests pass on 165827, 165948,
170017, 170018, three detections each at a 2000px preview; both foreground faces
are editable. Actual Activity tests select the left face and change all three
face sliders across four loads. Gesture tests pass for double-tap zoom, pinch,
pan, hold comparison, and transformed face hit testing.

Beauty_20261018_004548.jpg: leg adjustment enabled with both foreground faces
selected. Applying 70, saving JPEG, reopening and detecting repeated three
rounds without locking. Detected raw background faces remain above the actual
leg region; they do not require removal from safety checks. This test proves
this fixture passes after the fix, not the historical cause in every session.

Beauty_20261017_134757.jpg: left-face render at 0/50/100 completed. At 100,
measured mesh width ratio 0.919 and height ratio 0.958 (not height-only shrink).
The existing Antigravity effect coefficients were not increased in this review.

Built and installed the signed release APK over the existing QuickBeauty on
SM-F971N, version 1.1.3 (16); install succeeded without uninstalling/data reset.
Separate debug test app also remains installed. Release APK:
app/build/outputs/apk/release/app-release.apk
SHA256: 0D18CA7E06441FFC43AF27B738BE878EC81D921000434862942988CA7FA15DDC
The earlier AAB was not rebuilt or uploaded in this review.

Device harness render/legs modes write test outputs only to the debug app's
internal files/cache, leaving original photo fixtures unchanged.


## 2026-10-18 shoe compression correction (latest)

The earlier monotonic-mesh test did not detect visible shoe compression.
Latest saved Beauty_20261018_005558.jpg confirmed this release-blocking defect.
Replaced the ankle-to-bottom compression with a rigid lower region: stretch
ends 4% of image height above the higher detected ankle, snapped to a mesh row.
Upper image translates upward, equivalent to extending the lower section then
cropping the top. Output dimensions stay unchanged. Crop is bounded by detected
face headroom with a hair margin. Only one pose detection is used; no new UI.
The other person's foot location is not guaranteed by this single-pose heuristic.
Face indicator and tap coordinates now include the leg-induced translation.

User identified 20261017_165827.jpg as the original. Rendered original at full
2252x4000 with amounts 0/50/100, inspected the maximum render. Protected region
begins at y=3266.67; maximum top crop is 89.74 px. Sampled protected-region RGB
channels differ by at most 1/255 from source after mesh rendering (PNG), so
shoes/floor no longer undergo vertical compression there. JPEG may introduce
normal encoding differences. Previously flattened saved shoes are not restored.

Updated tests check rigid upper-body translation, protected higher ankle,
unchanged lower mesh, headroom cap, export scaling, and monotonic rows. 37 JVM
tests pass. Signed APK/AAB rebuilt with this correction; no Play upload or
review submission. Version remains 1.1.3 (16). Release APK installed on phone.
