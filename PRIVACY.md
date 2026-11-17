# Arcadune Privacy Policy

**Effective Date:** November 16, 2026  
**Application:** Arcadune (`io.github.shahrukhmaniyar.arcadune`)  
**Maintainer:** Shahrukh Maniyar ([github.com/shahrukhmaniyar/Arcadune](https://github.com/shahrukhmaniyar/Arcadune))

Arcadune is a free, open-source multi-system game emulator for Android released under the GNU General Public License v3.0 (GPLv3). Arcadune does **not** operate any proprietary backend servers, does **not** include analytics or crash-reporting SDKs, does **not** serve advertisements, and does **not** sell or share personal data.

---

## 1. Data Stored Locally on Your Device

By default, all data created or used by Arcadune remains strictly inside your device's local storage:

- **ROM Library & Artwork Metadata:** Folder access URIs granted by you via Android's Storage Access Framework (`ACTION_OPEN_DOCUMENT_TREE`), indexed game titles, custom game names, and cached cover thumbnails stored in app-private storage (`RetrogradeDatabase` / `filesDir`).
- **In-Game Saves & Emulator Save States:** Cartridge battery saves (`.srm`), memory card images, save states, and `256dp` save-state preview screenshots stored inside app-private storage (`saves/` and `states/`), plus any `.srm` or `.zip` backups you explicitly export to folders you choose.
- **Controller, Core & Display Preferences:** Custom touch-skin layouts, gamepad key bindings, selected emulator cores and cached on-demand `.so` core binaries (`filesDir/cores/`), per-game shader/core settings, and local cheat lists stored in `SharedPreferences` and `filesDir/cheats/`.
- **RetroAchievements Session Token (Opt-In):** If you sign in to RetroAchievements, your password is sent **once** over HTTPS to `retroachievements.org` to exchange for a Connect session token and is **never** written to disk or logs. The returned Connect token is encrypted at rest using **AES-256-GCM** backed by the hardware-isolated **Android Keystore** (`ra_token_enc_v1`).
- **Offline Achievement & Leaderboard Queue (Opt-In):** If a RetroAchievements unlock or leaderboard submission occurs while your device is offline, the minimal payload metadata (username, game hash, achievement/leaderboard ID, mode flag, score, active-core `User-Agent`, and creation timestamp — never your token or password) is stored locally (`ra_offline_queue_v1`) until it is delivered to RetroAchievements.org or you clear app data.

---

## 2. Optional Network Connections & Third Parties

Arcadune initiates outbound network connections **only** to the following third-party services when required by features you use:

| Service | Endpoints | When Contacted | Data Transmitted | Governing Policy |
|---|---|---|---|---|
| **RetroAchievements** | `https://retroachievements.org/dorequest.php`<br>`https://media.retroachievements.org` | Only when you sign in or play with **RetroAchievements** enabled | Username, password (one-time sign-in only) or Connect token, ROM MD5/rcheevos hash (`r=gameid`), achievement & leaderboard definitions (`r=patch`), Rich Presence heartbeats (`r=ping`), achievement unlocks (`r=awardachievement`), leaderboard scores (`r=submitlbentry`), badge image requests, and `User-Agent` (`Arcadune/<version> (Android <release>) [<core>/<version> ]rcheevos/<version>`) | [RetroAchievements Privacy Policy](https://docs.retroachievements.org/general/privacy-policy.html) |
| **GitHub** | `https://api.github.com`<br>`https://github.com`<br>`https://objects.githubusercontent.com`<br>`https://raw.githubusercontent.com` | Update checks (**Settings > Updates**), on-demand Libretro core downloads (**Settings > Change Cores** for optional alternative cores in both editions, and default cores in the Lite APK), missing cover-art lookups (`libretro-thumbnails` / `thumbnails.libretro.com`), and online cheat lookups (`libretro-database`) | Standard HTTP/HTTPS request headers (IP address, `User-Agent`) and the requested release, core `.so` binary, game title thumbnail path, or cheat file path | [GitHub General Privacy Statement](https://docs.github.com/en/site-policy/privacy-policies/github-general-privacy-statement) |
| **Google Images Search** *(User-Initiated Cover Picker)* | `https://www.google.com` + image host you tap | Only when you explicitly open **Change cover > Search online** on a game card (`CoverSearchActivity` in-app `WebView`) and tap a cover image to download | Search query you enter/confirm in the cover picker `WebView` and standard browser request headers to Google Images and the image URL you select | [Google Privacy Policy](https://policies.google.com/privacy) |
| **Google Drive API** *(Play variant only)* | `https://www.googleapis.com` | Only in the `play` build flavor if you explicitly sign in and enable **Save Sync** (the `free` GitHub releases contain zero Google Play Services libraries) | OAuth access token and your own save/state files stored in your personal Google Drive `appDataFolder` | [Google Privacy Policy](https://policies.google.com/privacy) |

Arcadune never transmits your ROM files, save files (except to your own personal Google Drive in the `play` flavor when you opt in), device identifiers, contact lists, location, or microphone audio to Arcadune or any analytics service. Microphone access (`android.permission.RECORD_AUDIO`) is requested only if you grant it for emulated consoles with microphone hardware (such as Nintendo DS) and is processed strictly in-memory by the local emulation core.

---

## 3. Data Retention, Server Locations & Deletion Controls

- **On-Device Retention:** Local library entries, saves, preferences, cached cores, and encrypted RetroAchievements tokens remain on your device until you remove them, sign out, clear app storage, or uninstall Arcadune.
- **Signing Out of RetroAchievements:** Tapping **Sign out** in **Settings > RetroAchievements** immediately deletes both your encrypted Connect token (`ra_token_enc_v1`) and any legacy token keys from device storage.
- **Full Local Deletion:** Clearing Arcadune's storage in Android System Settings or uninstalling Arcadune permanently deletes all app-private databases, saves, save states, cached cores, cached artwork, encrypted tokens, and queued offline submissions on your device. (Files you manually exported to shared folders such as `Downloads` or `Documents` remain under your control.)
- **Third-Party Server Locations & Retention:** Arcadune operates **no servers**. Data sent to RetroAchievements.org, GitHub, Google Images, or Google Drive (when opted in) is processed and retained on those providers' infrastructure in accordance with their respective privacy policies linked above. To manage or delete your RetroAchievements account data, visit [retroachievements.org](https://retroachievements.org/).

---

## 4. Contact

Questions or privacy inquiries regarding Arcadune can be submitted via [GitHub Issues](https://github.com/shahrukhmaniyar/Arcadune/issues).
