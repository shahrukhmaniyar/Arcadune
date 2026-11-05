# Skin studio

A small JVM build, outside the Android project, for working on the touch controller skins
(`lemuroid-touchinput/.../skin`) without an Android device or SDK:

- **`gradle test`** runs the skin tests (`lemuroid-touchinput/src/test`): touch handling, layout
  overlaps on many screen shapes, shell placement.
- **`gradle art`** renders the Console skins' artwork into
  `lemuroid-touchinput/src/main/assets/skin-art/<controller>/<portrait|landscape>/`: a body
  overlay (grain, sheen, wordmark, grille) and one picture per control (normal and pressed) with
  blurred shadows, gloss and moulded letters. The app draws these instead of its code-drawn
  controls (see `SkinArt`); anything without a picture is still drawn by code. A designer can
  replace any picture by hand: keep the file name and the size (the control's frame plus its
  reach plus `ART_BLEED` on every side, at 3 pixels per skin unit).
- **`gradle preview`** renders every skin as it looks in a game, on phones and tablets, upright
  and sideways, into `build/preview` (`ONLY=GB,NES` limits the consoles, `VARIANT=minimal`
  renders the Minimal skins).

It compiles the controller code straight from `lemuroid-touchinput`, against Compose
Multiplatform for the desktop (the same `androidx.compose` packages) and Robolectric's
`android-all` for the Android framework classes, with stand-ins for the few classes that only the
Android build has (`stubs/`, and two generated from the real sources at build time).

Run it with Gradle 8 and JDK 17 or newer: `cd tools/skin-studio && gradle art`.
