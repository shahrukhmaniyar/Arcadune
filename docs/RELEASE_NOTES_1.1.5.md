# Arcadune 1.1.5

A RetroAchievements sign-in fix and gamepad page switching in the library.

## Fixed: RetroAchievements sign-in was not kept

Signing in to RetroAchievements worked, but Arcadune then said **"Signed in, but this phone could not store your sign-in securely"** and forgot the account. This happened on every phone, not just some: the way the sign-in was encrypted for Android's secure key storage was refused by Android. The sign-in is now stored properly and stays after you close the app or restart the phone.

- If you saw that message, just sign in again once after updating.
- The sign-in is still stored encrypted, protected by Android's secure key storage, and never as plain text.
- If the stored key ever becomes unusable (for example after restoring the phone from a backup), Arcadune now makes a new one instead of refusing, and asks you to sign in again.

Thanks to the person who reported this on GitHub (issue #4).

## Gamepad: switch consoles with L and R

With a gamepad or controller connected, the shoulder buttons now turn the library's pages: **L1 or L2** goes to the previous console, **R1 or R2** to the next. It works as soon as the library is open, even before the cursor is on a game, and stops at the first and last page. It is off while you are searching.

## Other

- Library with a gamepad: moving the cursor near the top of a short page no longer shows a stuck pull-to-refresh arrow or leaves the list nudged down. Only a real finger pull counts now.
- Settings > Controllers: the controller layouts are now prepared in the background shortly after the app starts.

Both APKs are signed with the same key as earlier releases, so you can install over your current version. `Arcadune.apk` (Lite) downloads each core the first time you use it; `Arcadune-full.apk` includes the default cores.
