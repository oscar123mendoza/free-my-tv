# Free My TV: notes for Claude

A home screen for Google TV and Android TV that replaces the stock one: a grid of the apps installed on the TV, the
streaming apps first, and a switch between Free My TV and the TV's original home screen (in the app's own Settings).
A plain Android app in Java, built without Gradle by `build.sh`. English only, no network, no API keys.

LG TVs (webOS) and Samsung TVs (Tizen) have **the older design** (weather, TMDB picks, prices, a drawer, themes): a web
app in `web/`, packed by `webos/` and `tizen/` (see below). It is not kept in step with the Android grid.

The website (freemytv.io) is in `docs/`: static HTML, CSS and JS, no build step. A push to `main` that changes it
publishes it to GitHub Pages (`.github/workflows/pages.yml`). `docs/index.html` (English) and `docs/pt/index.html`
(Brazilian Portuguese) are the same page in two languages, so change both; visitors from Brazil land on the
Portuguese one by themselves, and the PT/EN switch in the menu is remembered.

The person you're helping may not be technical. Keep it short and friendly, say what you're about to do on their TV
before you do it, and let them do the taps on the TV. [SETUP-AND-RECOVERY.md](SETUP-AND-RECOVERY.md) is the guide
for them; point to it when they're stuck.

## Installing it for someone

1. **Ask** for the TV's IP address, if they didn't say. The Android app needs no city and no API key.
2. **Tools:** a JDK 17+, the Android SDK command-line tools (build-tools 34+, platform android-34) and adb. Check
   what's already there (`java -version`, `$ANDROID_HOME`, `adb version`) and install only what's missing.
   - macOS: `brew install --cask temurin android-commandlinetools android-platform-tools`, then
     `sdkmanager "build-tools;34.0.0" "platforms;android-34"`.
   - Linux: OpenJDK 17, Android's command-line tools and adb from the package manager, then the same `sdkmanager` line.
   - Windows: Git Bash (what Claude Code runs there; `build.sh` handles it) or WSL, then as on Linux.
3. **Settings:** run `./build.sh` once; it creates `config.properties`. Its only setting is `disable_assistant`
   (`false` by default; `true` also turns off Google Assistant and breaks the remote's mic button). Ask before changing it.
4. **Build:** `./build.sh` → `FreeMyTV.apk`. Keep `freemytv.keystore`: updates must be signed with the same key.
5. **Install:** `adb connect <IP>:5555`. The TV asks *Allow USB debugging?*: tell them to accept and tick *Always
   allow*. Then `adb install -r FreeMyTV.apk`. On TCL, also `adb shell "appops set io.freemytv AUTO_START allow || appops set io.freemytv APP_AUTO_START allow"`
   (some firmware calls the operation `APP_AUTO_START`; this tries both):
   TCL's `TclAppBoot` blocks background jobs by default, so the switch (and the watchdog) never run. The log shows
   `forbid bind service ... ModeJob`. Some TCL firmware has neither (a 2024 MediaTek Google TV, Android 14, has no
   `TclAppBoot`): both names answer `Unknown operation string`, and nothing needs allowing.
   On a Mac, `adb connect` can fail with `No route to host` while the TV answers (`nc -z <IP> 5555` connects): macOS's
   Local Network privacy blocks the adb server that adb starts in the background. Start it from the same terminal
   instead (`adb kill-server; adb nodaemon server &`), or allow the terminal app under System Settings → Privacy &
   Security → Local Network.
6. **Tell them what the switch does** before they make it: the home screen and screensaver change, a short list of
   apps is turned off (never uninstalled), and *Original* brings it all back.
7. **They switch on the TV:** open *Free My TV* from the apps row → choose *Free My TV* → OK. The app uses the TV's own
   ADB on 127.0.0.1:5555 with its own key, so the TV asks once more to allow debugging. Don't switch modes from the
   computer. If the TV's Wi-Fi was saved during its first setup, the switch stops before changing anything and asks
   them to forget the network and connect again in Settings (Network & Internet); then the switch goes through.
   Saved networks out of range don't show in Settings: find the ones with `cname=com.google.android.tungsten.setupwraith`
   in `adb shell dumpsys wifi` (each block starts with its `ID:`), and with their OK remove them with
   `adb shell cmd wifi forget-network <id>`.
8. **Port 5555 resets on every reboot.** The switch needs TCP debugging on 5555. If the switch says debugging is off
   (`port 5555 closed`), it needs the computer again to turn 5555 back on; the setup guide has the steps.
9. **Check:** `adb logcat -s FreeMyTV` shows what the switch did.

## Installing it on an LG TV (webOS), older design

This is the older design; its features (weather, TMDB picks, prices, drawer) are the ones the Android grid dropped.
On LG it's an app, installed through LG's Developer Mode, and it changes nothing else on the TV: LG's home screen
stays, and Free My TV opens with a long press of a number key.

1. **Ask** for the TV's IP address and their city, if they didn't say. Tools: Python 3 and OpenSSH (`ssh`,
   `ssh-keygen`), already on macOS, Linux and Windows 10+.
2. **On the TV** (they do it): LG Content Store → *Developer Mode* → sign in with a free LG developer account
   (webostv.developer.lge.com) → *Dev Mode Status* on (the TV restarts) → *Key Server* on. It shows the IP and a
   passphrase.
3. **Settings:** `config.properties` (the first `python3 webos/build.py` creates it). The older design reads its own
   keys there (`city`, `latitude`, `longitude`, `tmdb_key`, `tmdb_region`, `show_crypto`, `currency`, `temperature`,
   `language`), which are not in `config.example.properties`. The TMDB key is the person's own and secret: they create
   it (free: themoviedb.org → Settings → API) and paste it after `tmdb_key=` themselves. Don't ask for it in the chat,
   never print it, never commit it. If they paste it in the chat anyway, put it only in `config.properties`.
4. **Build:** `python3 webos/build.py` must end with `Built FreeMyTV-webos.ipk`.
5. **Install:** the first time needs the TV's passphrase, typed by them: ask them to run
   `python3 webos/install.py <IP>` in their own terminal (yours can't answer its prompt, and the passphrase doesn't go
   in the chat). It keeps the unlocked key in `~/.ssh/freemytv_webos_<IP>`, so later updates run from your terminal
   with the same command.
6. **Tell them:** while Free My TV is open, holding a number key pins it to that key. Developer Mode lasts about 40
   days; *EXTEND* in that app renews it (if it runs out, the TV removes developer apps, and installing again brings it
   back). `--uninstall` removes Free My TV.

## Installing it on a Samsung TV (Tizen), older design

On Samsung it's an app too (2017 and newer, Tizen 3+), installed through Samsung's Developer Mode; Samsung's home
screen stays. This is the older design, like LG. Two ways:

- **The release, no tools:** TizenBrew Installer (github.com/reisxd/TizenBrewInstaller) installs the latest GitHub
  release when they type `simbasjungles/free-my-tv`, and on 2023+ TVs signs it with their Samsung account. That build
  has no TMDB key (no picks) and takes the city from the TV's time zone. Walk them through the README's steps.
- **Their own build** (their city, their TMDB key), below.

1. **Ask** for the TV's IP address and their city, if they didn't say.
2. **On the TV** (they do it): *Apps* → type `1 2 3 4 5` on the remote → *Developer mode* on, *Host PC IP* = this
   computer's IP → restart the TV.
3. **Tools:** Samsung's `tizen` command to sign (Tizen Studio's CLI, or Samsung's Claude Code plugin
   `tizen-sdk-skills` from github.com/Samsung/tizen-agent-skills, which installs it and makes Samsung certificates),
   and `python3 tizen/install.py` to install, which speaks SDB itself. `tizen` runs on Java; Samsung's `sdb`, its new
   `tz` and Tizen Studio's installer (with its own JDK) are Intel binaries on macOS. On Apple Silicon without Rosetta,
   take the CLI from Samsung's package repository (download.tizen.org/sdk/tizenstudio/official, `pkg_list_macos-64`;
   check each zip's SHA256 there): unzip the `data/` of `new-common-cli`, `certificate-generator`, `sdk-utils` and
   `sdk-utils-core` into `~/tizen-studio`, then write `~/tizen-studio/sdk.info` with three lines,
   `TIZEN_SDK_INSTALLED_PATH=<that folder>`, `TIZEN_SDK_DATA_PATH=<home>/tizen-studio-data` and `JDK_PATH=<a native
   JDK 17's .jdk folder>` (Homebrew: `/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk`). That `tizen` signs natively.
4. **Certificate, once:** 2023+ TVs (Tizen 7+) need a Samsung certificate, made with their Samsung account for this TV
   (its DUID): the plugin's Samsung profile opens the browser for them to sign in, or Tizen Studio's Certificate
   Manager does it. 2017-2022 TVs take a Tizen certificate (`tizen certificate`, then `tizen security-profiles add`
   and `tizen cli-config "profiles.path=<home>/tizen-studio-data/profile/profiles.xml"`; on macOS the passwords go to
   the login Keychain). Certificate passwords are theirs: they type them in their own terminal, never in the chat.
5. **Settings:** the same `config.properties`; the TMDB key rules in the LG section apply.
6. **Build and sign:** `python3 tizen/build.py --sign <profile>` must end with `Built FreeMyTV-tizen.wgt (...,
   signed with "<profile>")`.
7. **Install:** `python3 tizen/install.py <IP>`: it pushes the app over SDB (port 26101), installs it with the TV's
   `0 vd_appinstall` and opens it with `0 was_execute` (tested on a 2017 Tizen 3.0 TV); `--uninstall` removes it.
   With Samsung's tools instead: `sdb connect <IP>`, `tizen install -n FreeMyTV-tizen.wgt -t <serial from sdb
   devices>` and `tizen run -p FreeMyTVio.FreeMyTV -t <serial>`. Updates need the same certificate: the TV refuses
   one signed by another (uninstall first). A retail TV's sdbd only takes files (`filesync_support:push`): nothing
   can be pulled from it. To look inside the running app, `0 debug FreeMyTVio.FreeMyTV 300` over SDB opens it with
   a Web Inspector port (Runtime.evaluate works on Tizen 3; screenshots don't).
8. **Tell them:** *Autorun Last App* in the TV's settings (under Start Screen Options; where it is changes by year)
   makes the TV turn on into Free My TV.

## Releases

TizenBrew Installer takes the first `.wgt` of the latest GitHub release, so every release carries
`FreeMyTV-tizen.wgt`: built with `--generic` (no config.properties: no key, the city from the time zone) and signed
with a Tizen certificate (`--sign`), which 2017-2022 TVs install as it is; on 2023+ TVs the installer signs it again
with the person's Samsung certificate. Sign every release with the same author certificate: older TVs refuse an
update signed by another. `python3 webos/build.py --generic` makes the LG `.ipk` that goes along.

## Adapting it to another TV

Read first, change after. Read-only commands that tell you what the TV has:

```bash
adb shell getprop ro.product.brand; adb shell getprop ro.product.model; adb shell getprop ro.build.version.release
adb shell cmd package query-activities --brief -a android.intent.action.MAIN -c android.intent.category.HOME
adb shell cmd role get-role-holders android.app.role.HOME
adb shell settings get secure screensaver_components; adb shell settings get secure screensaver_default_component
adb shell pm list packages -s     # system apps (-3 for the ones they installed)
```

Then, in the code:

- `HomeMode.DEBLOAT`: add the brand's promo, demo, store-mode and telemetry apps, one comment per line. Only those.
- `HomeMode.PROTECTED`: add the brand's system pieces: picture, sound, inputs and HDMI-CEC, the tuner and live TV,
  remote and Bluetooth pairing, setup, updates, system UI. When in doubt, protect it.
- `HomeApps.PREFERRED` and `HIDDEN`: the apps at the front of the grid, and apps to keep off it.
- Build, install, have them switch on the TV, read `adb logcat -s FreeMyTV`, then have them switch back to
  *Original* once and confirm everything returned.

Other TVs that don't run Google TV or Android TV: the way is a Google TV streamer on HDMI (Chromecast with Google TV,
Google TV Streamer, onn 4K, NVIDIA Shield). Set Free My TV up on the streamer.

**Uninstalled while in Free My TV mode?** The apps it turned off stay off. Bring them back with
`adb shell pm enable <package>` (the `DEBLOAT` list and the stock home screen), then
`adb shell cmd package set-home-activity <stock home activity>` and
`adb shell settings put secure screensaver_components <stock screensaver>`.

## The code

- Java 8 language level, minSdk 28 (Android 9), targetSdk 33. No Gradle, no libraries: only the Android SDK.
  To check for newer APIs, compile against `platforms/android-28/android.jar`.
- **The grid** (`HomeView`): drawn by hand on one Canvas, five tiles a row. The focused tile lifts and glows in its
  app's color. Tile images come from each app's TV banner, cropped. The last 15 tiles are cached (`MAX_TILES`). Keep it
  smooth: no allocations in `onDraw`, no work on the UI thread.
- **The app list** (`HomeApps.installedPackages`): every app with a `LEANBACK_LAUNCHER` entry, `PREFERRED` first in
  that order, then the rest by name. System apps are left out unless they are in `PREFERRED`. `HIDDEN` drops the kids'
  YouTube app. The grid ends with the *Free My TV* switch tile and a *Settings* tile (the TV's own Settings).
- **The switch** (`SettingsActivity` → `HomeMode` through `ModeJob`): the screen has two rows, *Free My TV* and
  *Original*, and the one in use has a ✓. Every switch runs as a background job in the main process, with a notice
  for the home screen. The UI is in the `:home` process.
- **ADB** (`Adb`): connects to `127.0.0.1:5555` (TCP debugging must be on; it resets on reboot) and fails with
  `debugging is off on the TV (port 5555 closed)` when the port is closed.
- **State** is one JSON file, `home-state.json`, written atomically by `HomeMode.save`. There is no database (no Room).
- `Watchdog` goes back to the stock home screen after 3 crashes in 5 minutes. `HomeMode.verifyNow` (boot check and
  app update) puts back anything a TV update turned on.
- **The screensaver** (`DreamView`): a large clock on black. No photos, no network. Android 11 runs the screensaver as
  `ScreensaverAlone`, in the main process (its DreamActivity needs the dream in the app's default process, without a
  home activity); `res/values-v30/bools.xml` picks it, every other version keeps `Screensaver` in `:home`. Keep both in step.
- Settings come from `config.properties` through the generated `out/gen/io/freemytv/Config.java` (never edit that).
- Comments in English, short, only where the why isn't obvious. Text is English only: plain strings, no `Ui.tr`.
- After any change, `./build.sh` must end with `Built FreeMyTV.apk`.
- LG and Samsung (`web/`, older design): plain ES5 JavaScript and CSS without variables, grid or backdrop-filter, since
  older TVs (webOS 3 and 4, Tizen 3) run a Chromium from 2015 to 2017; only transform and opacity animate. `web/tv.js`
  is the TV's side: on LG, Luna (`listLaunchPoints`, `launch`, the inputs); on Samsung, `tizen.application`
  (`getAppsInfo`, `launch`), the `VIDEOSOURCE` property and `tizen.tvwindow.setSource` for the inputs, then Samsung's
  viewer (`org.tizen.tv-viewer`) to show them. Samsung's own data is unreliable: on Tizen 3 no app's `iconPath` loads
  (on Tizen 4 and 5, YouTube's and Prime Video's are a placeholder), so logos are bundled in `web/icons/` and keyed by
  app id in `SAMSUNG_ICONS` (SVGs need a width and height for old WebKit); some names are empty or long
  (`SAMSUNG_TITLES`); and Tizen 3 answers no "settings" app control, so TV Settings opens `org.tizen.menu`.
  `web/home.js` was built to follow an earlier `HomeView` layout; it does not track the current grid, so don't port
  Android design changes there unless asked. Text: `tr(pt, en, es)`, with the German and Russian in `web/strings.js`
  (keyed by the English). Built without a city (`--generic`), `web/zones.js` (`tools/make_zones.py`, from the tz database)
  turns the TV's time zone into the city, the currency and the region. `tools/webapp.py` is what both builds share.
  Preview: `python3 webos/build.py --preview` (or `tizen/build.py`), then open `out/webos/index.html` in a browser (a
  stand-in lists sample apps).

## Always

- Never turn off an app that saved a Wi-Fi network: Android deletes its networks and Original can't bring them
  back (`wifiOwners` in `HomeMode`).
- Never commit `config.properties`, `*.keystore` or any key.
- Never uninstall or clear the data of system apps; only `pm disable-user --user 0`, through `HomeMode`, so
  *Original* can bring it back.
- Don't change the TV's picture, sound, network or account settings.
- Keep the way back working: the *Original* mode and the watchdog.
