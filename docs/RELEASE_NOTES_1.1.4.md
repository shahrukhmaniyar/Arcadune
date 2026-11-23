# Arcadune 1.1.4

A quicker way to refresh your library, working display filters on the DS, and a quieter microphone question.

## Fixed: display filters on the DS

The LCD and CRT filters were almost invisible on the DS (melonDS and DeSmuME), sideways and upright. The effect was sized from the whole stacked picture instead of from each screen, so it faded out. Each screen now gets its own filter strength, so scanlines and the LCD grid show properly on both screens.

## Pull down to rescan

Added or removed ROMs? On the main screen, scroll to the top of the game list and **pull down**, like in other apps. The whole list follows your finger and a small circular arrow turns as you pull. It takes a deliberate pull, so a casual drag does nothing. When the arrow turns purple you feel a short buzz: let go and Arcadune rescans your ROMs folder, the same as **Settings > Rescan**. The arrow keeps spinning and the thin accent line under the title shows the scan is running. Pull back up before letting go to cancel.

- Works on every page, including pages with only a few games.
- Scrolling back up to the top does not start a rescan: the pull is a new drag that starts at the top.
- Pulling again while a scan is running does nothing.
- **Settings > Rescan** is still there if you prefer it, and for gamepad or screen reader use.

## Microphone for the DS

The "Enable microphone" card on the main screen is gone. Instead, the first time you start a DS game, Android asks whether Arcadune may use the microphone. If you say no, the game starts without it and Arcadune never asks again (you can still allow it later in Android's app settings).

Both APKs are signed with the same key as earlier releases, so you can install over your current version. `Arcadune.apk` (Lite) downloads each core the first time you use it; `Arcadune-full.apk` includes the default cores.
