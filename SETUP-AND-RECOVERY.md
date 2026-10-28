# Free My TV: setup and recovery

A practical guide for setting up Free My TV on a Chromecast with Google TV (or another Google TV or Android TV), and for
getting back to a working state when something goes wrong. No technical background needed.

Each step is marked:

- **[TV]**: done on the TV with its remote.
- **[Computer]**: done on a Mac (or PC) in the Terminal. Keep the computer on the same Wi-Fi as the TV.

Steps marked *unverified* describe a menu or command that is not confirmed in this project yet. Check it on your TV
and tell Claude Code what you saw.

## Words you will see

- **Free My TV**: the new home screen.
- **Original**: the TV's own home screen (Google's), with its screensaver and apps as they came from the factory.
- **Debugging** (USB debugging, ADB): a setting that lets a computer talk to the TV. Free My TV uses it to switch.
- **Port 5555**: the door debugging uses over Wi-Fi. It closes on every restart.

## Part 1: One-time setup on the TV

1. **[TV]** Turn on developer options: Settings → System → About → press *Android TV OS build* 7 times.
2. **[TV]** Settings → System → Developer options → turn on **USB debugging**.
3. **[TV]** In the same menu, turn on **Wireless debugging** (*unverified*: the name may differ on your TV). You need it
   to connect over Wi-Fi.
4. **[TV]** Write down the TV's IP address: Settings → Network & Internet. It looks like `192.168.1.50`.
5. **[TV and Computer]** Pair the computer with the TV (*unverified* steps). On the TV, Wireless debugging shows a
   pairing code and an IP address with a port. On the computer, run `adb pair <IP>:<port>` and type the code.
6. **[Computer]** Open port 5555 (*unverified*: `adb tcpip 5555` is a standard adb command, not checked on this TV).

## Part 2: Mac setup for adb (the computer side)

1. **[Computer]** Install the tools (from the [README](README.md#the-manual-way)):
   ```bash
   brew install --cask temurin android-commandlinetools android-platform-tools
   ```
   You only need the `sdkmanager` line too if you build the app yourself (Claude Code can do that for you).
2. **[Computer]** Connect to the TV:
   ```bash
   adb connect 192.168.1.50:5555
   ```
   Use your TV's IP address.
3. **[Computer]** If you see `No route to host` while the TV answers, macOS's Local Network privacy is blocking adb.
   Either run these two commands in the same Terminal window:
   ```bash
   adb kill-server
   adb nodaemon server &
   ```
   or open System Settings → Privacy & Security → Local Network and allow your Terminal app.

## Part 3: Install Free My TV

**Easiest: ask Claude Code.** Open the `free-my-tv` folder in [Claude Code](https://claude.com/claude-code) and say
*"Install Free My TV on my TV at 192.168.1.50."* It builds the app and installs it.

**By hand:**

1. **[Computer]** Build the app: `./build.sh` (in the `free-my-tv` folder). It writes `FreeMyTV.apk`.
2. **[Computer]** Install it:
   ```bash
   adb install -r FreeMyTV.apk
   ```
3. **[TV]** On TCL TVs only, let the app run in the background (the switch never starts otherwise):
   ```bash
   adb shell "appops set io.freemytv AUTO_START allow || appops set io.freemytv APP_AUTO_START allow"
   ```
4. **[TV]** Open the **Free My TV** app from the apps row, choose **Free My TV** and press OK.
5. **[TV]** When the TV asks *Allow USB debugging?*, accept and tick **Always allow**.

## Part 4: Switching between the two home screens

Switching is done from the app, on the TV. Nothing is needed from the computer.

1. **[TV]** Open **Free My TV** from the apps row. You see two choices: **Free My TV** and **Original**. The one in use
   has a ✓.
2. **[TV]** Move to the one you want and press OK. The switch happens in the background; when it finishes the TV
   shows a notice.
3. **[TV]** If the TV asks *Allow USB debugging?* again, accept it.

The grid also has a **Settings** tile that opens the TV's own Settings.

## Part 5: After a restart

Port 5555 closes on every restart. Free My TV's switch then says that debugging is off (`port 5555 closed`). Before
you can switch again:

1. **[TV]** Make sure USB debugging and Wireless debugging are still on (Part 1, steps 2 and 3).
2. **[Computer]** Connect again: `adb connect 192.168.1.50:5555`. If it fails, open port 5555 again (Part 1, step 6,
   *unverified*) and connect again.
3. **[TV]** Try the switch again.

Free My TV checks its own settings after every restart, so the home screen you chose stays in place.

## Part 6: When the switch fails because of the Wi-Fi from setup

**What you see:** the switch stops and a notice says the TV's first setup saved the Wi-Fi, and turning off apps would
erase those networks. Nothing has changed yet.

**Why:** Android deletes the Wi-Fi networks an app saved when that app is turned off. The first setup of the TV saved
your Wi-Fi, so Free My TV stops to protect it.

**Fix, on the TV:**

1. **[TV]** Settings → Network & Internet → tap your Wi-Fi network → **Forget**.
2. **[TV]** Connect to the network again and type the password.
3. **[TV]** Open Free My TV and try the switch again.

**If the network is out of range and does not show in Settings,** the computer can find and remove it. Ask Claude Code
to do it, or run these yourself. Confirm first with the person who owns the TV:

1. **[Computer]** Find the saved network. Each block starts with `ID:`; look for the one with
   `cname=com.google.android.tungsten.setupwraith`:
   ```bash
   adb shell dumpsys wifi
   ```
2. **[Computer]** Remove it, using the number after `ID:`:
   ```bash
   adb shell cmd wifi forget-network <id>
   ```

## Part 7: Turn an app back on

Free My TV turns some apps off. It never uninstalls them, so each can be turned back on.

1. **[Computer]** See which apps are off:
   ```bash
   adb shell pm list packages -d
   ```
2. **[Computer]** Turn one back on, using its package name from the list:
   ```bash
   adb shell pm enable <package>
   ```
   The package name is what the list shows, for example `com.google.android.katniss` for Google TV's voice search.

Things to know:

- Apps on Free My TV's built-in list are checked again after every restart and turned off again if you turned them
  back on. Apps you turned off by hand, outside that list, are left alone and stay off until you turn them back on.
- Turning off **Google TV's voice search (katniss)** turns off voice search on the TV. Turn it back on with
  `adb shell pm enable com.google.android.katniss` if you miss it.

## Part 8: Get Google's home screen back

**[TV]** Open **Free My TV** → choose **Original** → press OK.

That brings back the TV's home screen, its screensaver and every app Free My TV turned off.

If Free My TV's home screen crashes three times in five minutes, it switches back to Original by itself.

## Troubleshooting

| What you see | Why | What to do | Who |
|---|---|---|---|
| The switch says debugging is off (`port 5555 closed`) | Port 5555 closed after a restart | Part 5 | [Computer], then [TV] |
| The notice about the Wi-Fi saved during setup | The first setup saved your network | Part 6 | [TV] (or [Computer] if out of range) |
| `adb connect` says `No route to host` on a Mac | macOS Local Network privacy | Part 2, step 3 | [Computer] |
| The *Allow USB debugging?* prompt keeps coming back | The approval was not saved (*Always allow* was not ticked) | Accept it and tick *Always allow* | [TV] |
| The switch does nothing on a TCL TV | TCL blocks background jobs | Run the `appops` line in Part 3 | [Computer] |
| Voice search stopped working | Google TV's voice search (katniss) is off | Part 7 | [Computer] |
| An app is missing from the grid | It has no TV launcher entry, or it is the kids' YouTube app (hidden on purpose) | Open it from the TV's own apps list | [TV] |
| Something else is wrong | Unknown | Run `adb logcat -s FreeMyTV` and ask Claude Code to read it | [Computer] |
| You want the TV's own home screen back now | You changed your mind | Part 8 | [TV] |
