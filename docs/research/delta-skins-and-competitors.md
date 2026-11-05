# Controller skins: what Delta and the other leading emulators do

Research notes for Arcadune's touch controllers. Written so that design decisions about skins
can be checked against what the best apps actually do, instead of being guessed. Update it when
something new is learned.

## Method and sources

- **Delta's own source code**, read directly (it is open source):
  - [`rileytestut/Delta`](https://github.com/rileytestut/Delta): the app (settings, pause menu, action inputs).
  - [`rileytestut/DeltaCore`](https://github.com/rileytestut/DeltaCore): the skin format and touch handling.
  - The default skin of each console, `Controller Skin/info.json` in
    [`NESDeltaCore`](https://github.com/rileytestut/NESDeltaCore),
    [`SNESDeltaCore`](https://github.com/rileytestut/SNESDeltaCore),
    [`GBCDeltaCore`](https://github.com/rileytestut/GBCDeltaCore),
    [`GBADeltaCore`](https://github.com/rileytestut/GBADeltaCore),
    [`N64DeltaCore`](https://github.com/rileytestut/N64DeltaCore),
    [`MelonDSDeltaCore`](https://github.com/rileytestut/MelonDSDeltaCore).
  All numbers below come from these files (the iPhone "edgeToEdge" layouts: 320 wide upright,
  812 x 375 sideways, 414 x 896 / 896 x 414 for the DS).
- **Screenshots of Delta in use** supplied by the product owner (default skins, a GBA skin with
  a Pokémon theme, and the third-party "PIXEL" DS skin).
- **Skin format docs**: the [DeltaCore skins wiki](https://github.com/rileytestut/DeltaCore/wiki/Skins),
  [Delta docs](https://noah978.gitbook.io/delta-docs/skins).
- **Market**: [Pocket Gamer.biz](https://www.pocketgamer.biz/news/83845/delta-game-emulator-hits-no-1-on-the-app-store-download-charts-in-35-countries/),
  [Appfigures](https://app.appfigures.com/resources/insights/20240517?f=2),
  [Manic EMU on the App Store](https://apps.apple.com/us/app/manic-emu-game-emulator/id6743335790),
  [PPSSPP control settings](https://www.ppsspp.org/docs/settings/controls).

## The market in short

- **Delta** (iOS) went to No. 1 on the App Store in 35 countries at its launch in April 2024
  (about 5.3 million devices in the first weeks, per Appfigures). It is the reference for how
  touch controls should look and feel.
- **Manic EMU** (iOS) competes on breadth (3DS, PSP, DS, N64), shaders and third-party skins;
  store reviews mention freezes and overheating.
- **PPSSPP** (Android) competes on customisation: movable buttons, an analog stick that centres
  where the thumb lands, combo and turbo buttons.
- **RetroArch / Lemuroid** (Android): Arcadune started from Lemuroid; RetroArch's overlays are
  powerful but not polished.

The leaders win on how the controls feel and on the size of the game, not on decoration.

## Delta's skin system

- A skin is a `.deltaskin` zip: an `info.json` plus artwork. For each device type (`iphone`,
  `ipad`) and screen shape (`standard`, `edgeToEdge`, `splitView`) it gives a portrait and a
  landscape **representation**: a coordinate space (`mappingSize`), where each control is
  (`frame`), how far beyond its frame it still takes touches (`extendedEdges`, a global value
  plus per-control overrides), where the game's screens go (`screens` / `gameScreenFrame`), and
  whether it is drawn see-through over the game (`translucent`).
- **Touch areas reach the edges.** Controls next to a screen edge extend 15 to 30 units towards
  it; neighbouring face buttons have 0 extension on the sides that face each other.
- **Skin choice**: one preferred skin per console, per orientation, with a separate choice for
  when a gamepad is connected, and an optional per-game override (`preferredPortraitSkin`,
  `preferredLandscapeSkin`). See-through skins are drawn at 70% opacity by default
  (`translucentControllerSkinOpacity`, adjustable).
- Delta has **no in-app layout editor**: to move a button you install another skin.

## How Delta's default skins split the screen

| Console | Upright | Sideways |
|---|---|---|
| NES, SNES, GBA, N64 | Game on top, edge to edge in black; controller body at the bottom | **Full screen**: the game full height, centred; see-through controls in the corners |
| GBC | Same | **Framed**: the body in colour from top to bottom; the screen pinned to the top centre (300 x 270 of 812 x 375); controls low in the corners; Select / Start under the screen; Menu top left |
| DS | **Body behind everything**: both screens 354 of 414 wide (7% in from each side), stacked; L / R under the touch screen; controls below | **Both screens side by side across the top** (each 342 x 257 of 896 x 414, flush with the top); L / R just under the screens; d-pad and X / Y / A / B in the bottom corners |

Control sizes in the sideways (812 x 375) layouts:

| | NES | GBC | GBA | SNES | N64 |
|---|---|---|---|---|---|
| D-pad | 153, bottom left (x 30, y 207) | 131 | 161 | 161 | 123, under the stick (86) |
| Face buttons | A / B 74, bottom right | 64 | 75, plus an **A+B** area under A | X / Y / A / B 53 in a diamond | A / B 56, C buttons 40 |
| Shoulders | none | none | L / R 100 x 40, top corners | same | same |
| Small buttons | Select / Start bottom centre, Menu top centre | Select / Start under the screen, Menu top left | as NES | as NES | Start bottom centre, Menu top centre |

Upright (320 wide), every default puts a d-pad of about 120 to 132, face buttons of 51 to 64,
and Menu / Select / Start in a row at the bottom.

## Quick buttons (fast forward, save / load state)

- Delta defines six **action inputs** a skin can place like any button
  (`Delta/Emulation/ActionInput.swift`): `quickSave`, `quickLoad`, `fastForward` (while held),
  `toggleFastForward`, `reverseScreens` and `screenshot`.
- **None of Delta's default skins use them.** Every default skin only has the console's own
  buttons plus Menu. They appear in **third-party skins**, for example the "PIXEL" DS skin
  (fast forward, save, load down the left side, sideways).
- Delta also offers them elsewhere: the **pause menu** has Save State, Load State, Fast Forward,
  Screenshot, Cheat Codes and Hold Buttons; and a gamepad's buttons can be mapped to quick save,
  quick load, fast forward and screenshot.
- Delta's **quick save is kept**: it is a saved state stored with the game, there the next time
  the game starts.
- **Hold Buttons**: hold Menu and tap a button, and that button stays held until it is tapped
  again (for games that need a button held for long).

## What Arcadune does, compared

| Topic | Delta | Arcadune | Why |
|---|---|---|---|
| Upright layout | Game edge to edge in black, controller at the bottom | Same | Adopted |
| Sideways, home consoles and GBA | Full screen, see-through controls in the corners | Same, with the console's colours on the controls | Adopted |
| Sideways, GB / Game Gear / Neo Geo Pocket | Framed like the GBC | Same | Adopted |
| DS | Body behind the screens upright; screens across the top sideways | Same | Adopted |
| Skins per console | Any number, installed by the player | Two built in: Console and Minimal | Two good defaults first; imported skins are an open item |
| Layout editing | None (install another skin) | Drag any button, one size for all, opacity; in Settings and in game | Goes beyond Delta |
| Touch areas | Extended edges; nothing dead between buttons | Reach around each control, plus a grace area so no strip between face buttons is dead | Same aim |
| A+B | GBA area under A | GBA AB button under A, and a thumb between two face buttons presses both | Adopted |
| Quick buttons | Not in default skins; in third-party skins | **DS only** (sideways, left column), as the product owner asked | Other consoles stay like Delta's defaults |
| Quick save | Kept on disk | Kept on disk (was kept only in memory, lost when the game closed) | Adopted |
| See-through opacity | 70% by default | 100% by default; adjustable in the editor | Open item |
| Skin per game, skin for gamepad use | Yes | Not yet | Open items |

## Open items

1. **Quick buttons on other consoles**: Delta's defaults have none. If players ask for them,
   offer a setting ("Show quick buttons") rather than adding them to every layout.
2. **Hold Buttons** (hold Menu, tap a button to keep it held).
3. **Screenshot** as a quick button and in the pause menu.
4. **Per-game skin choice**, and a separate skin choice when a gamepad is connected.
5. **Default opacity of see-through controls**: consider Delta's 70%.
6. **Importing skins** (`.deltaskin`-like files) beyond the two built-in ones.
