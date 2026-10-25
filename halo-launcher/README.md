# Halo — a glass launcher for Android TV

Halo replaces your TV's home screen with one place for everything you're in the middle of:
shows, films, music and games from **every** app, each one a press away from playing again
in its own app, at your spot.

Home · Watching · Library (apps + search) · Settings · Inputs · Halo quick ring (Menu key).

---

## 1. Get the APK (pick one)

**A. GitHub (no software to install)**
1. Create a new private repo on github.com and upload this whole folder (or `git push` it).
2. Open the **Actions** tab → *Build Halo APK* runs automatically (≈5 min).
3. Open the finished run → download **Halo-APK** → unzip → `app-release.apk`.

**B. Android Studio** — Open this folder → wait for Gradle sync → *Build › Build APK(s)*.
Or on the command line: `./gradlew assembleRelease` → `app/build/outputs/apk/release/`.

**C. Claude Code** — in this folder run `claude` and ask: *"build the release APK and fix any
compile errors"*. `CLAUDE.md` tells it everything about the project.

## 2. Install on the TCL TV

1. TV: **Settings › System › About** → click **Android TV OS build** 7 times → Developer mode on.
2. **Settings › System › Developer options** → turn on **USB debugging** (and *Network debugging* if listed).
3. Note the TV's IP: **Settings › Network & Internet › your Wi-Fi**.
4. On your computer install **ADB** (Android "platform-tools") and run:

```
# Mac (Terminal) — install ADB once:  brew install android-platform-tools
./tools/setup-tv.sh 192.168.1.50 app-release.apk
# Windows:  .\tools\setup-tv.ps1 -Tv 192.168.1.50 -Apk .\app-release.apk
```

The script installs Halo, turns on Continue Watching access, sets Halo as home and — only if
you say yes — disables the stock Google TV home so the **Home button always opens Halo**.
Undo any time: `tools/undo-tv.ps1 -Tv <ip>` / `tools/undo-tv.sh <ip>`.

> Without ADB: install the APK with a file manager / "Send Files to TV", then in Halo go to
> **Settings › Access** and turn on what it lists. Google TV hides the "default launcher" choice,
> so use the **Home button helper** there (Accessibility) instead of disabling the stock home.

## 3. How Continue Watching works (honest version)

Android doesn't let a third-party launcher read other apps' private watch history, so Halo
combines every public route, best first:

| Source | What it gives | Needs |
|---|---|---|
| **Watch Next** (system list Google TV uses) | Exact episode/film + deep link → opens straight into it | `ACCESS_WATCHED_PROGRAMS` — many TVs refuse this to sideloaded apps; Halo uses it automatically if granted |
| **Media sessions** (what's playing) | Title, artwork, position for anything you play in any app that reports playback (most video & music apps do) | Notification access — granted by the setup script |

When you press play on a card Halo tries, most precise first:

1. **Watch Next deep link** → the exact episode/film.
2. **The app's own player intent** for that title (its media session / notification tap action)
   → lands on the player, then Halo presses play and seeks to your spot. Works for anything
   you've played since the TV last restarted.
3. **The app's live media session** → bring forward, play, seek.
4. **A content link built from ids the app reported** → YouTube video at your timestamp,
   Spotify track/playlist, any web/app link the app exposed.
5. **Playback resumption** (music & podcast apps resume the exact track).
6. **"Play from search"** with the title, inside that app.
7. Open the app (Halo tells you when an app allowed nothing better).

Library search also looks **inside your apps** the way Google TV's global search does: apps
that publish search results (with a deep link per title) show up as posters you can open
straight into. Apps that keep their results private still get a "Search in <app>" shortcut.

## 4. Remote shortcuts

| Key | Does |
|---|---|
| OK | Play / open |
| Hold OK | Options: start over, remove, pin to Dock, reorder Dock |
| Menu (☰) | Halo quick ring — volume, play/pause, sleep timer, picture, sound, inputs, screen saver, mute |
| Search / Mic | Library search — apps, settings, and titles inside your apps (voice works) |
| Input | Inputs screen |
| Back | Back to Home |

## Project map

```
app/src/main/java/com/halo/launcher/
  ui/theme/Tokens.kt         ← every colour, radius, size, type style and motion curve
  ui/components/             ← glass, focus primitive, icons, cards, tiles, buttons
  ui/screens/                ← Home, Watching, Library, Settings, Inputs, QuickRing
  data/                      ← apps, history, Watch Next, inputs, prefs
  service/                   ← media-session listener, Home-button helper
  resume/ResumeEngine.kt     ← "open the right app at the right thing"
tools/                       ← ADB setup / undo scripts
```
