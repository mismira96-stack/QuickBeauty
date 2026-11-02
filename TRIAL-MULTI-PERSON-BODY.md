# Multi-person body adjustment trial — 2026-11-02

Historical trial record, branch codex/multi-person-body-trial. The user subsequently accepted device testing and additional photos, and explicitly authorized 1.1.7/code20 production submission on 2026-11-02. See RELEASE-1.1.7.md and release/1.1.7 for final evidence.

- Preserve face selection and shared/global leg-length adjustment.
- Detect a pose within each face-anchored crop (maximum 8 faces). Accept a torso only when confident in-frame shoulders/hips, anatomical proportions, and the pose nose uniquely match that face. Full-image pose can be reused only if it passes the same checks.
- Immutable per-face body anchors survive selection and scaling/export. Body edits are saved independently alongside face edits. A face without a verified body keeps its body controls disabled; no group-photo inferred torso fallback.
- For verified bodies, Body Slim covers lower shoulders through pelvis with a smooth vertical plateau, a narrower horizontal influence, protected image edges, sibling faces and verified neighboring torsos. Existing unverified single-person fallback profile is unchanged.
- This is NOT person segmentation: overlapping clothing/arms and background lines may still distort. No promise of recognizing every body or eliminating all collateral deformation. Global leg behavior is unchanged.

Validation:
- 62 JVM tests passed, including pose ownership/ambiguity/confidence, per-face warp persistence, scaled anchors, fixed neighbor region and horizontal mesh monotonicity.
- Release/debug/instrumentation builds passed, release vital lint passed.
- On connected Samsung SM-F971N, a diagnostic crop of the displayed photo (964x1286; NOT the original full-resolution file) detected 5 faces and verified bodies for indices 0 and 3. Foreground user index0 matched via a crop; mismatched full-image pose was rejected. Independent body warp/export at 85 passed horizontal mesh monotonicity; result visually inspected.
- Signed release trial installed without removing app data. User reports initial success and is trying other photos; broader real-photo review remains pending.
- Debug/instrumentation helper apps and copied device fixture are development-only; private photos/results stay outside Git. No brightness/tone change in this trial.

Implementation limits: geometric pose association is conservative, not proof of identity. Depth ordering/occlusion is not explicitly reconstructed. Review close overlap and combined maximum sliders before considering production release.
