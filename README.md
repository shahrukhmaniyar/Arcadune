# Arcadune

Arcadune is an open-source emulation app for Android based on Libretro. It focuses on ease of use, good Android integration and a clean experience.

## Download

**[Download the latest APK](https://github.com/shahrukhmaniyar/Arcadune/releases/latest/download/Arcadune.apk)** (about 180 MB, emulator cores included)

All versions are on the [Releases page](https://github.com/shahrukhmaniyar/Arcadune/releases). To install, open the APK on your phone and allow installs from your browser or file manager when Android asks. Arcadune needs Android 6.0 or newer.

Arcadune is a fork of [Lemuroid](https://github.com/Swordfish90/Lemuroid) by Swordfish90, which itself grew out of [Retrograde](https://github.com/retrograde/retrograde-android) and is built on [LibretroDroid](https://github.com/Swordfish90/LibretroDroid). Many thanks to their authors.

## What's different from Lemuroid

- **Search artwork online:** pick a cover from Google Images with an editable search box, from a game's long-press menu.
- **Smarter automatic covers:** covers are fetched from the libretro-thumbnails GitHub mirror first, then retried under other region names and matched against the real list of covers.
- **Rename a game and its cover follows:** the cover is looked up again under the new name.
- **Grid view** for the games of a system, remembered between launches.

### Supported systems
- Atari 2600 (A26) ([stella](https://docs.libretro.com/library/stella/))
- Atari 7800 (A78) ([prosystem](https://docs.libretro.com/library/prosystem/))
- Atari Lynx (Lynx) ([handy](https://docs.libretro.com/library/handy/))
- Nintendo (NES) ([fceumm](https://docs.libretro.com/library/fceumm/))
- Super Nintendo (SNES) ([snes9x](https://docs.libretro.com/library/snes9x/))
- Game Boy (GB) ([gambatte](https://docs.libretro.com/library/gambatte/))
- Game Boy Color (GBC) ([gambatte](https://docs.libretro.com/library/gambatte/))
- Game Boy Advance (GBA) ([mgba](https://docs.libretro.com/library/mgba/))
- Sega Genesis (aka Megadrive) ([genesis_plus_gx](https://docs.libretro.com/library/genesis_plus_gx/))
- Sega CD (aka Mega CD) ([genesis_plus_gx](https://docs.libretro.com/library/genesis_plus_gx/))
- Sega Master System (SMS) ([genesis_plus_gx](https://docs.libretro.com/library/genesis_plus_gx/))
- Sega Game Gear (GG) ([genesis_plus_gx](https://docs.libretro.com/library/genesis_plus_gx/))
- Nintendo 64 (N64) ([mupen64plus](https://docs.libretro.com/library/mupen64plus/))
- PlayStation (PSX) ([PCSX-ReARMed](https://docs.libretro.com/library/pcsx_rearmed/))
- PlayStation Portable (PSP) ([ppsspp](https://docs.libretro.com/library/ppsspp/))
- FinalBurn Neo (Arcade) ([fbneo](https://github.com/libretro/FBNeo/))
- Nintendo DS (NDS) ([desmume](https://docs.libretro.com/library/desmume/)/[MelonDS](https://docs.libretro.com/library/melonds/))
- NEC PC Engine (PCE) ([beetle_pce_fast](https://docs.libretro.com/library/beetle_pce_fast/))
- Neo Geo Pocket (NGP) ([mednafen_ngp](https://docs.libretro.com/library/beetle_neopop/))
- Neo Geo Pocket Color (NGC) ([mednafen_ngp](https://docs.libretro.com/library/beetle_neopop/))
- WonderSwan (WS) ([beetle_cygne](https://docs.libretro.com/library/beetle_cygne/))
- WonderSwan Color (WSC) ([beetle_cygne](https://docs.libretro.com/library/beetle_cygne/))
- Nintendo 3DS (3DS) ([citra](https://docs.libretro.com/library/citra/))

### Features
- Android TV support
- Automatically save and restore game states
- ROMs scanning and indexing
- Optimized touch controls
- Quick save/load
- Support for Zipped ROMs
- Display simulation (LCD/CRT)
- Gamepad support
- Local multiplayer
- Tilt input
- Customizable touch controls (size and position)
- Cloud save sync
- HD mode

## Building

You need JDK 17 and the Android SDK (platform 35).

```bash
git clone --recurse-submodules https://github.com/shahrukhmaniyar/Arcadune.git
cd Arcadune
./gradlew :lemuroid-app:assembleFreeBundleDebug
```

Gradle may run out of memory while merging dex files. If it does, raise the limit in `~/.gradle/gradle.properties`, for example `org.gradle.jvmargs=-Xmx6g`.

**Windows:** the emulator cores in `lemuroid-cores/bundled-cores` are git symlinks, which Windows git checks out as small text files. Run this once after cloning, before building, or every game will fail with "libretro core was not loaded":

```bash
powershell -ExecutionPolicy Bypass -File scripts\fix-bundled-cores.ps1
```

### Signing a release

Create `keystore.properties` in the project root (it is git-ignored) and keep the key file and its passwords somewhere safe:

```properties
storeFile=path/to/your-release-key.jks
keyAlias=your-alias
storePassword=your-store-password
keyPassword=your-key-password
```

Without this file, release builds fall back to the debug key and cannot be used to update an app signed with your real key.

## License

Arcadune is free software released under the [GNU General Public License v3.0](COPYING), the same license as Lemuroid. The original copyright notices are kept in the source files. The Libretro cores bundled with the app are distributed under their own licenses.
