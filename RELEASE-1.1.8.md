# QuickBeauty 1.1.8 (21)

## Included / user acceptance
- System Back exits the editor with save/discard/cancel for unsaved edits; the header photo icon still changes photos. Cancelling an OEM Gallery picker preserves edits. Saves fail safely without closing.
- Story controls/font presets centered and aligned. Caption/date default to bottom center with initial spacing; manually positioned content and independent date/caption sizing retained.
- WHITE text/date backgrounds and preset preview match canvas warm white #FAF9F7.
- Verified-body slimming uses a constant-transfer arm band with smooth inner/outer transitions. User accepted the installed arm trial. This reduces the original stretching in the protected band; it does not remove expansion from all background/overlap regions or identify anatomical arms.

## Tone experiment rejected / excluded
User rejected face-local Tone because the face and body looked disconnected. Face-local renderer/test and matrix removed. Global Tone matrix, export process and preview were restored byte-for-byte against accepted navigation/story commit 1e352e2. No MediaPipe/model/dependency addition. Further Tone/brightness changes remain deferred.

## Validation
- 65 JVM tests, release APK/AAB build, release vital lint, APK signature and 16KB ZIP alignment passed.
- Earlier same-day Samsung tests: Gallery cancellation/Back exit and unsaved cancel, Story default placement and controls, independent text/date resize/drag, pinch, IME/recreation and exact preview/export+PNG round-trip passed.
- Arm plateau unit checks cover non-expansion in its mathematical transfer band and monotone transitions. Group-photo diagnostic render at body95 passed mesh ordering and was accepted by the user; no universal no-distortion guarantee.
- Phone disconnected before the final post-rollback reinstall/rerun. The installed QuickBeauty Test may still be the rejected face-local experiment; the submitted APK/AAB use global Tone. Do not confuse them.

## Artifacts
- APK SHA256: 43fd6548a3b956d8d237af50329fe48cbce55f0c18bec7b5fd347ad9285a9c67
- AAB SHA256: de0c7fc285e27c4215e443656146405f1bbed59f33e828e8584e33dab55c5bd0
- Artifacts under release/1.1.8 and C:/work/releases/QuickBeauty/1.1.8, binaries Git-ignored.
- Actual submission evidence appears in release/1.1.8/release-result.json only after validation, final commit and fresh read succeed. Completed track configuration is not independent proof of Google review approval or store propagation.

## Submission result
Validation and final commit succeeded with ERROR_IF_IN_REVIEW. Fresh edit confirms production21/completed rollout configuration and bundle SHA-256. Store listings, all existing image assets/video and other tracks were unchanged. Review requested; Google approval and store propagation not independently verified. No further user submission action required.
