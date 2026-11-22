# Release checklist

Do every step, in order, for every release. A release is only made when all of them pass.
1.1.0 shipped with Settings dialogs that closed the app because steps like these were skipped.

## 1. Code checks (on the PC)

```powershell
./gradlew --max-workers=2 test --rerun
./gradlew --max-workers=2 :lemuroid-app:lintFreeBundleRelease
./gradlew --max-workers=2 ktlintCheck
```

- Read the real test counts in `*/build/test-results/**/TEST-*.xml` (0 failures, 0 errors). Never trust a run whose output was filtered with `-q` or `grep`: a compile error can hide behind it, and old XML files stay behind.
- Lint must say `BUILD SUCCESSFUL`. New warnings are fixed, not added to `lint-baseline.xml`, unless they are reviewed and harmless.
- After a controller layout change: `./gradlew -p tools/skin-studio art`.

## 2. On the phone (debug build)

```powershell
./gradlew --max-workers=2 :lemuroid-app:installFreeBundleDebug
powershell -ExecutionPolicy Bypass -File scripts/smoke-test.ps1
```

The smoke test opens every Settings screen and dialog, the library actions and a game's pause menu, in portrait and landscape. It must end with "Smoke test passed".

Then by hand:
- Play one game of each console that changed; quick save, quick load, exit, start it again and check the save is there.
- Rotate during a game.
- Anything the release notes mention: try it.

## 3. Release build

- Bump `versionCode` and `versionName` in `lemuroid-app/build.gradle.kts`; update `docs/HARDCORE_COMPLIANCE_REVIEW.md`.
- `RA_HARDCORE_APPROVED` stays `false` (check the generated `BuildConfig`).
- Build `assembleFreeDynamicRelease` and `assembleFreeBundleRelease` (one at a time).
- `apksigner verify --print-certs` on both: same certificate as the last release.
- Install the Full APK over the current release on the phone and repeat the Settings part of the smoke test.

## 4. Publish

- README "What's New" and `docs/RELEASE_NOTES_X.Y.Z.md`.
- Commit (no Claude attribution), push, tag `vX.Y.Z`.
- `gh release create vX.Y.Z --repo shahrukhmaniyar/Arcadune --latest` with `Arcadune.apk` and `Arcadune-full.apk`.
- Download both public links again and compare sha256 with the build.

## When cores change

After uploading a new `cores-N` or `psp-files-N` release, run `scripts/update-core-fingerprints.ps1` and rebuild: the app refuses any core whose fingerprint it does not know.
