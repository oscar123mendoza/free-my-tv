<div align="center">

<img src="docs/assets/icon-512.png" width="96" alt="">

# Free My TV

**A fast, ad-free home screen for Google TV and Android TV.**

Your installed apps in a clean grid, the streaming apps you use first, and the TV's own clutter switched off.<br>
Open source. Built with Claude Code. One button to undo.

[**freemytv.io**](https://freemytv.io) · [Install](#install) · [Going back](#going-back) · [Setup and recovery guide](SETUP-AND-RECOVERY.md) · [Any TV](#any-tv) · [LG and Samsung (older design)](#lg-and-samsung-older-design)

</div>

## What you get

- **No ads.** No sponsored rows, banners or autoplaying promos.
- **Your apps first.** The streaming apps you use go to the front of the grid, in the order you choose. Every other app on the TV follows, by name.
- **A quiet screensaver.** A large clock on black.
- **The TV's own home, one press away.** *Settings* → *Original* brings back the factory home screen, screensaver and apps.

## Install

About ten minutes. You need a Google TV or Android TV (Android 9 or newer) and a computer on the same Wi-Fi.

For a step-by-step guide that assumes no technical background, see [SETUP-AND-RECOVERY.md](SETUP-AND-RECOVERY.md).

### The easy way: Claude Code

1. **On the TV**, turn on developer mode: Settings → System → About → press *Android TV OS build* 7 times. Then Settings → System → Developer options → turn on **USB debugging**. Write down the TV's IP address (Settings → Network & Internet).
2. **On your computer**, get the code:
   ```bash
   git clone https://github.com/simbasjungles/free-my-tv && cd free-my-tv
   ```
3. **Open [Claude Code](https://claude.com/claude-code) in that folder** and say:
   > Install Free My TV on my TV at 192.168.1.50.

   It installs the tools it needs, builds the app and puts it on your TV. Swap in your TV's IP.
4. **On the TV**, open *Free My TV* from the apps row, choose *Free My TV*, and accept the debugging prompt (tick *Always allow*).

### The manual way

1. **On the TV**, do step 1 above.
2. **Tools:** a JDK (17+), the Android SDK command-line tools (build-tools 34, platform android-34) and adb. On a Mac:
   ```bash
   brew install --cask temurin android-commandlinetools android-platform-tools
   sdkmanager "build-tools;34.0.0" "platforms;android-34"
   ```
   On Linux, get OpenJDK 17, Android's command-line tools and adb from your package manager, then run the same `sdkmanager` line. On Windows, use Git Bash (which Claude Code uses there) or WSL.
3. **Build:**
   ```bash
   ./build.sh
   ```
   The first run creates `config.properties`. There is nothing you have to change in it, then run it again.
4. **Install:**
   ```bash
   adb connect 192.168.1.50:5555
   adb install -r FreeMyTV.apk
   ```
   On TCL TVs, also let it run in the background (TCL blocks it by default, and the switch never starts):
   ```bash
   adb shell "appops set io.freemytv AUTO_START allow || appops set io.freemytv APP_AUTO_START allow"
   ```
5. **On the TV**, open *Free My TV*, choose *Free My TV*, and accept the debugging prompt.

## Switching between the two home screens

Open *Free My TV* from the apps row. It shows two choices, **Free My TV** and **Original**; the one in use has a ✓. Choose one and press OK. The switch runs in the background, and the TV shows a notice when it is done.

The grid also has a *Settings* tile that opens the TV's own Settings.

The switch works over the TV's own debugging port (`127.0.0.1:5555`), so debugging must be on. That port turns off on every restart; see [SETUP-AND-RECOVERY.md](SETUP-AND-RECOVERY.md#part-5-after-a-restart).

## What it changes

- **The home screen and the screensaver.** Free My TV replaces them. The TV's own home screen and screensaver are recorded first, so *Original* restores exactly what was there.
- **Some apps are turned off, never uninstalled.** The list is in [`HomeMode.java`](app/src/io/freemytv/HomeMode.java) (`DEBLOAT`): Google's extras (Play Games, YouTube Music), and on each brand the promo, store-demo, telemetry and manual apps (TCL, Multilaser, Philco, Xiaomi, Skyworth and KTC are listed there). The voice assistant is turned off only with `disable_assistant=true`.
- **Never touched.** Picture, sound, inputs, remote pairing, Google services and the Play Store are on a protected list (`PROTECTED`), checked before every change.
- **Wi-Fi is protected.** Apps that saved a Wi-Fi network are never turned off, because Android would erase the network. If the TV's first setup saved yours, the switch stops before changing anything and asks you to forget the network and connect again in Settings.

## Any TV

Free My TV runs on Google TV and Android TV: TCL, Sony, Hisense, Philips, Xiaomi, Vestel-made TVs (F&U), KTC-made TVs (NextStar) and more, plus streamers like Chromecast with Google TV, the Google TV Streamer, onn 4K and NVIDIA Shield.

**Any other TV:** plug a Google TV streamer into HDMI and set Free My TV up on that.

**A different brand or model:** ask Claude. Open the folder in Claude Code and say *"set it up for my Sony"*: it reads your TV, finds its home screen and its promo apps, and updates the lists below. [CLAUDE.md](CLAUDE.md) tells it how.

| What | Where |
|---|---|
| Apps to turn off | `DEBLOAT` in [`HomeMode.java`](app/src/io/freemytv/HomeMode.java) |
| Apps never to touch | `PROTECTED` in the same file |
| Apps at the front of the grid | `PREFERRED` in [`HomeApps.java`](app/src/io/freemytv/HomeApps.java) |
| Apps hidden from the grid | `HIDDEN` in the same file |

## Make it yours

It's your code, and Claude Code finds its way around it. Try:

- *"Move Crunchyroll near the front of the grid."*
- *"Turn off Google Assistant too."* (sets `disable_assistant=true` and rebuilds)
- *"Hide the TV's own Settings tile."*

## How it works

- **A plain Android app, no Gradle, no libraries.** [`build.sh`](build.sh) runs aapt2 → javac → d8 → zipalign → apksigner and makes a small APK.
- **It uses the TV's own ADB, from inside the TV.** The app connects to `127.0.0.1:5555` with its own key (that's the *Allow USB debugging?* prompt) and runs the same commands you would: `cmd package set-home-activity`, `settings put secure screensaver_components`, `pm disable-user`. Nothing listens on the network, and the app makes no internet requests.
- **The grid is drawn by hand on one Canvas.** [`HomeView.java`](app/src/io/freemytv/HomeView.java) lays out five tiles a row. The focused tile lifts and glows in its app's color. The last 15 tiles are kept in memory.
- **The app list comes from the TV.** [`HomeApps.java`](app/src/io/freemytv/HomeApps.java) lists every app with a TV launcher entry, puts the `PREFERRED` ones first, then sorts the rest by name. System apps are left out unless they are in `PREFERRED`.
- **Settings.** The only setting in `config.properties` is `disable_assistant`. The generated `Config.java` is produced by `build.sh`; edit `config.properties`, not that file.
- **A safety net.** A watchdog in a separate process gives the TV's own home screen back after three crashes in five minutes. A boot check re-applies Free My TV mode if a TV update turned things back on.
- **Text is English only.** Plain strings, no translations.
- **Data.** No database. The app keeps its state in one JSON file on the TV (`home-state.json`).

| File | What it is |
|---|---|
| [`HomeView.java`](app/src/io/freemytv/HomeView.java) | The home screen grid |
| [`HomeApps.java`](app/src/io/freemytv/HomeApps.java) | Which apps are on the grid, and how each opens |
| [`HomeMode.java`](app/src/io/freemytv/HomeMode.java) | Switching between Free My TV and Original |
| [`Adb.java`](app/src/io/freemytv/Adb.java) | The TV's own ADB, from inside the TV |
| [`SettingsActivity.java`](app/src/io/freemytv/SettingsActivity.java) | The switch screen (the *Free My TV* tile) |
| [`DreamView.java`](app/src/io/freemytv/DreamView.java) | The screensaver (a clock) |
| [`Watchdog.java`](app/src/io/freemytv/Watchdog.java) | The safety net |

The website ([freemytv.io](https://freemytv.io)) is in [`docs/`](docs): plain HTML, CSS and JS on GitHub Pages.

## LG and Samsung (older design)

The LG (webOS) and Samsung (Tizen) versions are **the older design**: a web app with the weather, trending picks (from TMDB), crypto prices, a drawer, and light and dark themes. They live in [`web/`](web), [`webos/`](webos) and [`tizen/`](tizen), and they have not been updated to the grid. They still read their own settings (`tmdb_key`, `latitude`, `longitude`, `city`, `show_crypto`) from `config.properties`; those keys are no longer in `config.example.properties`, so add them by hand if you use these versions.

### LG TVs (webOS), older design

It installs through LG's own Developer Mode, with nothing rooted or changed on the TV. LG's home screen stays where it is, and Free My TV is one press of a number key away.

You need an LG TV (webOS 3 or newer) and a computer on the same Wi-Fi, with Python 3.

1. **On the TV**, open the LG Content Store and install **Developer Mode**. Sign in with a free [LG developer account](https://webostv.developer.lge.com), turn on **Dev Mode Status** (the TV restarts), then turn on **Key Server**. Write down the IP address and the passphrase it shows.
2. **On your computer**, get the code and build:
   ```bash
   git clone https://github.com/simbasjungles/free-my-tv && cd free-my-tv
   python3 webos/build.py
   ```
3. **Install.** The first time, it asks for the TV's passphrase; updates don't:
   ```bash
   python3 webos/install.py 192.168.1.50
   ```
4. **On the TV**, while Free My TV is open, hold a number key on the remote. From then on, that key opens it from anywhere.

Developer Mode lasts about 40 days: open the Developer Mode app now and then and press **EXTEND**. To remove Free My TV, run `python3 webos/install.py 192.168.1.50 --uninstall`.

### Samsung TVs (Tizen), older design

You need a Samsung TV from 2017 or newer (Tizen 3+) and a computer on the same Wi-Fi.

1. **On the TV**, open **Apps** and type `1 2 3 4 5` on the remote. Turn **Developer mode** on, type your computer's IP address in **Host PC IP**, and restart the TV.
2. **On your computer**, download [TizenBrew Installer](https://github.com/reisxd/TizenBrewInstaller/releases/latest), the community's installer for Samsung TVs, open it and connect to your TV.
3. **Install from GitHub:** type `simbasjungles/free-my-tv`. On 2023 and newer TVs it asks you to sign in to your Samsung account once, to sign the app for your TV.
4. **Straight to it:** in the TV's settings, under Start Screen Options, turn on **Autorun Last App**: the TV turns on into Free My TV.

For your own build with your own city, see [`tizen/build.py`](tizen/build.py); the Android app does not cover it.

## Settings

`config.properties` (created by the first `./build.sh`) has one setting for the Android app:

| Key | What it does | Default |
|---|---|---|
| `disable_assistant` | Also turn off Google Assistant ("Ok Google"). The remote's mic button stops working. | `false` |

Changed something? Run `./build.sh` and install again. Keep `freemytv.keystore`: updates are signed with it.

## Going back

On the TV: open *Free My TV* and choose *Original*. The factory home screen, screensaver and every app come back as they were. If the new home screen ever crashes three times in five minutes, it switches back by itself.

To remove it for good, switch to *Original* first, then uninstall *Free My TV* (Settings → Apps).

## Privacy

No accounts, no analytics, no tracking. The app makes no internet requests. Your settings stay on your computer and your TV.

## Credits

- Built with [Claude Code](https://claude.com/claude-code).
- The LG version builds on what [@danielthejoker18](https://github.com/danielthejoker18) found in [FreeMyTvWebOS](https://github.com/danielthejoker18/FreeMyTvWebOS) (MIT): installing through Developer Mode and opening apps through the TV's service bus.
- On Samsung TVs it installs with [TizenBrew Installer](https://github.com/reisxd/TizenBrewInstaller) by [@reisxd](https://github.com/reisxd).
- Icons: [Material Symbols](https://fonts.google.com/icons) (Apache 2.0). Font: [Inter](https://rsms.me/inter/) (SIL OFL 1.1). Samsung app logos: [Simple Icons](https://simpleicons.org) (CC0).
- Not affiliated with Google, TCL, LG, Samsung or any streaming service. App names and logos belong to their owners.

## License

[MIT](LICENSE). Fork it, change it, ship it.
