# Arcadune 1.1.2

Fixes a bug that could empty your library. If you are on 1.1.0 or 1.1.1, please update.

## The library emptying itself

On some phones Android takes back Arcadune's permission to read the ROMs folder. Games then failed to start, and the next scan treated the folder as empty and removed every game, with its favourites, play time and covers (saves were not touched). Choosing the same folder again did nothing, and Factory reset made it worse.

- A folder that cannot be read now stops the scan instead of counting as empty, and a scan that finds no files never removes a library that has games.
- Choosing a folder always asks Android for permission again, even the same folder.
- If the permission is gone, the library says **Choose your ROMs folder again** and keeps your games, favourites and saves.
- Factory reset keeps your ROMs folder.

## Saves

- A save that exists but cannot be read now stops the game from starting, instead of starting empty and overwriting it.
- The save being replaced is kept in `saves-previous` next to `saves`.
- Games with the same file name on different consoles (for example `Sonic.md` and `Sonic.gg`) no longer share one save.
- If a save cannot be written when you exit (for example the phone is full), you get a notification.
- Restoring a backup only replaces your saves once the whole backup has been read without a problem.

## Other fixes

- Tapping a game twice quickly can no longer start it twice.
- Games start even if you denied notification permission.
- Cores and PSP files that Arcadune downloads are checked against fingerprints built into the app before they are used.
- The update check no longer depends on which GitHub release is flagged "Latest".
- A RetroAchievements sign-in that cannot be stored securely now says so, instead of signing you out later.
- Gamepad buttons are saved without freezing the screen.
- The app is in English only for now; the other languages translated only a third of the text.

Both APKs are signed with the same key as earlier releases, so you can install over your current version. `Arcadune.apk` (Lite) downloads each core the first time you use it; `Arcadune-full.apk` includes the default cores.
