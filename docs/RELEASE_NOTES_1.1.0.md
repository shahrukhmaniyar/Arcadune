# Arcadune 1.1.0

A big update: ten new systems, full RetroAchievements, a saves and backups manager, much faster fast-forward, and a rebuilt set of controller layouts.

## New systems (35 in total, 52 cores)

- Sega Dreamcast, Sega Saturn, Sega 32X, 3DO, Atari 5200, Atari 8-bit (400/800/XL/XE), ColecoVision, Commodore 64, Apple II and MSX / MSX2.
- 29 cores come built in to the Full APK. 23 alternative cores can be downloaded on demand under **Settings > Change Cores**.
- Reads CHD disc images directly, plus more ROM formats (.cso, CloneCD, .mds, flat arcade zips), and searches your library as you type.

## RetroAchievements

- Sign in once under **Settings > RetroAchievements**. Your token is encrypted with the Android Keystore.
- In-game achievements browser, unlock popups with sound and haptics, live progress, rich presence and leaderboards. Unlocks earned offline are queued and sent later.
- Hardcore Mode is fully built and tested, but it is **locked in public builds** until RetroAchievements approves Arcadune. All achievements are earned in Casual Mode for now. The compliance review for RetroAchievements admins is in `docs/HARDCORE_COMPLIANCE_REVIEW.md`.

## Saves and backups

- New **Settings > Saves & backups** manager: see storage use, export all saves to a folder, export or import a single game's save, or back everything up as one zip.
- Play time is tracked per game, with a "Most played" sort.

## Fast-forward and speed

- Fast-forward (2x, 4x, 6x, 8x) now skips the frames you would not see, so it really is faster on every console. Before, some consoles (the Nintendo DS for example) barely sped up.
- Fast-forward audio modes: keep pitch, classic, or mute.
- Closing a game is much quicker: the long exit freeze on GBA, DS and PlayStation is gone. Games start with the controller already on screen, with no loading spinner.

## Controllers

- **Exit** is now always on the controller. Quick Save, Quick Load and Fast Forward are optional buttons you can add in Edit Controls.
- Every console's layout has been arranged by hand and tidied: buttons in a row share one size and one height, with the same gap between them and the same space at both ends. Small buttons show an icon or a short name inside, with no names under them (the NES keeps its own look).
- **Nintendo DS and 3DS** have new default layouts: a bigger main screen, the second screen where your thumb can reach it, and the screens move out of the way of buttons on other phone shapes.
- **PlayStation (standard)**, **PSP**, **NES**, **GBA** and **3DO** layouts were adjusted so the extra buttons sit where they make sense. The NES Menu and Rewind buttons were restyled to match the others.
- Fixed: the **3DO** touch buttons did not respond because the controller was not being set up for the 3DO core. The **Dreamcast** had the same set-up problem and got the same fix.
- Tap **Reset** in Edit Controls to return a console to its new default layout. Layouts you saved on an older version start again from the new defaults.

## Other

- Fixed: gamepad shortcuts for Quick Save, Quick Load and Fast-Forward could not be set (they never appeared in the settings list). They are listed now, as "Not set", under Settings > Controllers: tap one, then press two buttons together.
- 10 new console icons, an updated About screen listing every core and its license, and a privacy policy (`PRIVACY.md`).
- Updates are still offered inside the app under **Settings > Updates**.

Both APKs are signed with the same key as earlier releases, so you can install over your current version. `Arcadune.apk` (Lite) downloads each core the first time you use it; `Arcadune-full.apk` includes the 29 default cores.
