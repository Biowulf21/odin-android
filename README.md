# PlusKey

Choose separate actions, apps, or public app shortcuts for short, double, triple, and long presses. Double and triple press are off by default. On fresh installs with a flash, Plus Key defaults long press to Toggle flashlight. Custom long press fires once after 500 ms while held. Successful custom actions give brief native haptic feedback. Choose Managed by OnePlus to use the phone's native assignment. For custom long press, set the OnePlus assignment to Do nothing using the app's settings link. This also removes the “Press and hold…” warning.

Built-in actions include flashlight, Sound → Vibrate → Silent, two-state sound toggles, individual sound modes, screenshot, lock, notifications, quick settings, media and volume controls.

## Install and setup

Download newest PlusKey APK from [Releases](https://github.com/hiddemoens/PlusKey/releases/latest). Install APK, then open Plus Key. Existing personal builds update without losing choices. Open Settings in Plus Key for guided setup. Already-completed steps hide automatically. Pair with code opens Android settings and notification for six-digit code; no port entry needed. Keep Android pairing window open while entering code through notification. Notification closes after submission.

Plus Key prefers [Shizuku](https://shizuku.rikka.app/) 13 or later when it’s running and authorized. Start Shizuku, then open Plus Key Settings and tap **Allow Shizuku**. This skips separate Plus Key pairing. Wireless Debugging remains available when Shizuku is unavailable.

After pairing, Start uses saved local connection. Optional automatic startup requests one protected-settings permission so app can enable Wireless debugging for startup and disable it when ready. On this OnePlus firmware, permission grant is blocked until Developer options → Disable system optimisation is enabled. This native setting may increase background power usage. Manual startup remains available.

APK stays installed across reboot. Helper may need restarting after reboot or system termination. Open Plus Key and tap Start if the button is disconnected. Automatic startup manages the connection while the app starts the helper. Android may still require approval for each new Wi-Fi network.

## Behavior and privacy

Native button events are observed, not suppressed. Published static app shortcuts are offered when accessible; private or dynamic launcher shortcuts may not appear. No account or analytics. Optional automatic update checks contact GitHub once daily when app opens; disable them in Settings → More options. Pairing and helper connections stay on this phone. Pairing identity stays in app-private storage and is excluded from backup.

## Source and build

Repository includes Gradle wrapper, app, host tests, native libraries and dependency licenses. Requires JDK 21, Android SDK 37, build tools 36.0.0 and network for Gradle/Material dependency resolution. Supply the keystore password privately through `PLUS_LENS_KEYSTORE_PASSWORD`, then run `sh build.sh`. Original signing key is excluded; keep it to update original installation. Rebuilding with another key requires uninstalling existing app.

## Verification

Host behavioral and ADB stream regression tests pass; signed release build passes. Phone tested flashlight, three-state sound cycle and native feedback command. Hardware hold timing and felt feedback need user confirmation. Update-check policy tests pass; full future-version installation remains untested. Automatic-startup permission granted and Helper ready observed with Wireless debugging off; repeated restart and reboot recovery remain unverified. No reboot or long-term battery validation performed.

## Compatibility

Developed and tested on OnePlus 15 (CPH2747), Android 17 / OxygenOS 17. This is an early release; other phones and firmware versions are unverified. Raw key detection depends on OnePlus input events and native button setting names. App installs on Android 11+, which does not imply every firmware is supported.

When double and triple press are off, short press fires on release. If either is configured, each release gives you 200 ms by default to start the next press. Adjust Click interval from 100 to 500 ms in the app. Longer intervals delay single- and double-press actions. Triple press fires on the third release. Choose Managed by OnePlus for native long press; otherwise set native assignment to Do nothing.

Flashlight, sound, media, volume, and lock actions can run while locked. On Android 16 or later, apps, shortcuts, and other actions can wake the screen and run once you unlock within 30 seconds, provided the firmware delivers the required unlock callbacks. A newer custom press replaces any pending action. Screen-off operation depends on the phone delivering Plus Key events during standby.

## USB startup fallback

If local pairing cannot start helper, install APK and open app once, enable USB debugging and authorize computer. From directory containing APK, run:

```sh
adb install -r PlusKey-1.5.1.apk
adb push PlusKey-1.5.1.apk /data/local/tmp/plus-lens-helper.apk
adb shell chmod 444 /data/local/tmp/plus-lens-helper.apk
adb shell cmd package list packages -U io.hiddemoens.pluskey
```

Use UID printed by last command in place of APP_UID:

```sh
adb shell 'CLASSPATH=/data/local/tmp/plus-lens-helper.apk setsid nohup app_process / io.hiddemoens.pluskey.Helper APP_UID </dev/null >/data/local/tmp/plus-lens.log 2>&1 &'
```

Use app Stop button before replacing an already-running helper. After startup, USB can disconnect.

## License

Original code: [Apache License 2.0](LICENSE). Bundled dependencies use their own licenses; see [THIRD_PARTY.md](THIRD_PARTY.md). Not affiliated with OnePlus.

## Updates

App checks latest public GitHub release once daily when opened. Settings → More options → Check for updates checks immediately. Automatic checks can be turned off there. Download requires a tap; APK checksum, package name, version code and signing certificate are verified before Android installer opens. Android asks to allow installs from Plus Key once and confirms each installation. Updates cannot install silently.

Maintainers: use an increasing Android versionCode and numeric release tag (for example v1.5 or v1.4.1); upload one signed APK to a normal published GitHub Release. Keep original signing key private and reuse it. GitHub asset SHA-256 digest is required. Draft and pre-release builds are not offered by updater.
