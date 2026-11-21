# Arcadune 1.1.1

A fix for a crash in 1.1.0. If you updated to 1.1.0, please update to this.

- Fixed: the app closed when you opened a choice dialog under **Settings**. This hit Display filter, Viewport alignment, Vibrate on touch, Cache size limit, and every other settings item that opens a list of options.
- Fixed: tapping a console under **Settings > Change Cores** closed the app. The core picker now opens, and choosing an alternate core downloads and selects it as intended.
- All Settings screens, dialogs and the in-game menu were opened and checked on a phone in portrait and landscape.

Everything else is as in 1.1.0 (see `docs/RELEASE_NOTES_1.1.0.md`). Both APKs are signed with the same key as earlier releases, so you can install over your current version. `Arcadune.apk` (Lite) downloads each core the first time you use it; `Arcadune-full.apk` includes the default cores.
