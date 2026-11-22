# Arcadune 1.1.3

Bigger buttons that stay apart, a proper DS screen layout, Immersive mode, a start-up fix and a cursor for gamepads in the library. Thank you to everyone who reported things on GitHub.

## Fixed: a game that would not start

Some games showed **Something bad happened with your save file** and did not open. This came from 1.1.0 and could hit any console: when a game's in-game save was newer than its quick-resume state, Arcadune dropped the quick-resume state as it should, but the read it had already started was reported as a save error. It no longer is, and a damaged quick-resume file can no longer stop a game from starting.

## Buttons no longer overlap when you make them bigger

In Edit Controls, making buttons bigger (the **All buttons** slider, or one button's own size) used to pile them on top of each other. Now:

- Buttons that sit together (the A/B/X/Y diamond, L and R, the small buttons along the bottom) grow as one group and keep their shape and spacing.
- Making one button bigger opens up room around it.
- Rows stay level and nothing is pushed off the screen.
- When there is no room left, the size simply stops at the biggest one that fits. On a few crowded upright layouts that is below 135% (for example 113% on the 6-button arcade pad, 121 to 126% on PlayStation, Saturn and Genesis 6-button). Every sideways layout reaches the full 135%.
- Layouts you made too big in an older version are untangled the next time you change a size.
- You can still drag any button wherever you like.

## DS screens

- **Sideways:** the DS now shows the big screen and the small screen the same way on melonDS and DeSmuME. Before, DeSmuME cut off the bottom screen, melonDS showed black after Swap, and display filters were hard to see. The core now keeps its normal stacked picture and Arcadune places the two screens itself, so filters work on both, Swap works, and touch lands in the right place.
- **With a gamepad connected,** the DS is shown big screen plus small screen sideways, like the 3DS (it used to be two small screens stacked).

## Immersive mode (new setting, off by default)

**Settings > Immersive mode** fills the empty space around the game with a soft blur of the picture. Console skins keep their bodies and only lose the black around the game, and the Minimal skins turn see-through so the blur shows behind the controls. Skins whose body fills the whole screen (the DS, for example) are not changed.

## Library with a gamepad or keyboard

The cover the cursor is on now has a ring, grows slightly and its name turns bold, the list scrolls with it and keeps it clear of the bottom bar, and the page icons show where the cursor is too.

## Other

- The Display and Settings screens were checked again on a phone, upright and sideways, with no crashes.

Both APKs are signed with the same key as earlier releases, so you can install over your current version. `Arcadune.apk` (Lite) downloads each core the first time you use it; `Arcadune-full.apk` includes the default cores.
