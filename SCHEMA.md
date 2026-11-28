# Odin Data Schema

**Source of truth:** `src/io/hiddemoens/pluskey/` (`Mailbox`, `Targets`,
`ActionChain`, `EdgeSettings`, `Updates`, `WirelessController`, `AdbIdentity`,
`ConfiguredShortcut`)

Odin has no database. State is split between small files shared with the helper
process and Android `SharedPreferences` owned by the app.

## 1. Stores

| Store | Owner | Contents |
|---|---|---|
| Mailbox directory (`Mailbox`) | App ↔ helper | One file per mapping and request; see §2 |
| `SharedPreferences "choices"` | App | Display labels for chosen targets, keyed by target string |
| `SharedPreferences "configuredShortcuts"` | App | Shortcuts created through an app's "configure shortcut" flow (owner package, gesture) |
| `SharedPreferences "edges"` (`EdgeSettings.PREFS`) | App | Edge service on/off, per-side handle enablement, size, position, swipe actions, indicator size/colour, release distance, direction buffer |
| `SharedPreferences "setup"` | App | Helper state: `paired`, `started`, `automatic`, `automatic_consent_revision`, ports, `user_stopped` |
| `SharedPreferences "updates"` | App | Last check time (`checked`), latest `tag_name`, `browser_download_url`, `pending_install` |
| `files/wireless-adb.identity` | App | RSA identity for Wireless debugging pairing. Private, excluded from backup |
| `/data/local/tmp/plus-lens-helper.apk`, `plus-lens.log` | Helper (shell user) | Helper code copy and its log |
| `Settings.System` OnePlus action setting | Android | Read to show whether OnePlus also handles the key |

## 2. Mailbox files

| File | Meaning |
|---|---|
| `short-target`, `long-target`, `double-target`, `triple-target` | Target string for each press type; absent = none (short press always active) |
| `gesture-<pattern>` | Marker that a custom pattern (`.` short, `-` long, up to 8) exists |
| `pattern-target-<pattern>` | Target for that pattern |
| `click-interval` | Multi-press window in ms (100–500, default 200) |
| `helper-status`, `status-request` | Helper heartbeat (max age 7 s) |
| `edge-action-request` / `edge-action-reply` | Edge swipe action hand-off to the helper (35 s timeout) |
| `preview-request`, `helper-stop` | Control requests |

## 3. Target strings

| Form | Example | Meaning |
|---|---|---|
| `action:<id>` | `action:torch` | Built-in action (`Options.builtIn`) |
| `chain:<a>,<b>,…` | `chain:action:wifi_off,action:data_on` | 2–16 built-in steps, ≤ 512 chars |
| `hold:<target>` | `hold:action:torch` | Runs while a pattern's final long press is held (torch, record, tiles) |
| `tile:<component>` | | Quick Settings tile |
| `locked-activity:` / `secure-camera:` prefixes | | Lock-screen launches |
| flattened `ComponentName` | `com.example/.Main` | App or shortcut activity |

## 4. Invariants

- The helper only reads mailbox files and loopback sockets; it never opens network
  connections.
- Pairing codes are never persisted.
- Changing a target format requires reading old values or migrating them, since
  mappings survive app updates.
