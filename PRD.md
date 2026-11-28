# Odin Product Requirements Document

**Status:** living product baseline
**Date:** 2026-11-28
**Product:** Odin, Plus Key and edge-swipe action mapper (fork of PlusKey)
**Current version:** 1.9.15 (versionCode 40)

## 1. Product Summary

Odin maps OnePlus Plus Key presses and Android edge swipes to built-in actions,
apps, shortcuts or action chains. A privileged helper, started over Shizuku or
local Wireless debugging, observes key events and runs actions that need shell
permissions. Odin is an Apache-2.0 fork of PlusKey. The source does not establish
compatibility beyond the devices named in the README.

## 2. Problem and Users

### Problem

The Plus Key's firmware options are limited, and Android has no built-in way to
bind a hardware key or edge swipe to a sequence like "Wi-Fi off → mobile data
on → hotspot on".

### Primary users

| User | Need | Surface |
|---|---|---|
| Owner | Fast, reliable shortcuts on his OnePlus | Main screen, Edge swipes tab |
| Sideloading OnePlus owners | Same, without root | Signed APK from GitHub |
| Contributor | Build and test | `build.sh`, `tests/` |

## 3. Goals

1. Fire mapped actions on release with no perceptible delay (single press), or
   after the configurable click interval when multi-press is on.
2. Run chains of up to 16 built-in steps, stopping on the first failure.
3. Work while locked where Android permits, and request unlock otherwise.
4. Keep pairing, identity and helper traffic on the phone.
5. Ship safe self-updates from Odin's own GitHub releases.

## 4. Non-goals

- Root-only features.
- Cloud sync or accounts.
- Overriding OnePlus firmware behaviour; Odin observes the key and OnePlus may
  still act on it ("Managed by OnePlus").
- Paid features, ads or in-app purchases (donations only).

## 5. Functional Requirements

### 5.1 Helper setup

- Shizuku 13+: one tap **Allow Shizuku**.
- Standalone: guided Wireless debugging pairing with a six-digit code entered from
  a notification; saved identity reused on **Start**.
- Optional automatic startup toggles Wireless debugging on, starts the helper and
  turns it off again, only after explicit consent and the protected-settings
  permission.

### 5.2 Button mapping

- Single, double, triple and long press; custom press patterns (dot/dash up to
  eight presses) and hold actions.
- Click interval 100–500 ms, default 200 ms.
- Media-aware mappings ("If media is playing… / otherwise").

### 5.3 Edge swipes

- Left and right handles, each with straight, diagonal-up and diagonal-down
  actions; adjustable size and position; invisible between gestures.
- Swipe indicator chevron with adjustable size, colour, release distance and
  direction buffer; one haptic pulse when ready to release.
- The keyboard area is excluded while the keyboard is open.

### 5.4 Actions

- Built-in actions listed in PRODUCT.md, app launches, static app shortcuts,
  configured shortcuts, quick tiles and the app fan (2–6 apps).
- Chains encode as `chain:` + comma-separated built-in action ids.

### 5.5 Updates

- Daily check of `Biowulf21/odin-android` releases; manual check in settings.
- Verify SHA-256 digest, package name, higher version code and signing
  certificate before installation; Android asks the user to approve.

## 6. Technical Requirements

- Java, Android SDK 37, Build Tools 36.0.0, JDK 21; Gradle; Material Components.
- Bundled libadb-android, spake2-android, Conscrypt and Bouncy Castle with
  pinned inputs in `deps/` (see THIRD_PARTY.md).
- Host tests via `sh build.sh` / `test.sh`.

## 7. Success Metrics

| Area | Target | Evidence |
|---|---|---|
| Gesture reliability | Every mapped gesture fires once | Owner use; host tests |
| Chain safety | Chains stop at the first failure and name it | `ActionChain` tests |
| Update safety | No unverified APK reaches the installer | `Updates` checks |
| Privacy | No network traffic except GitHub update checks | Code review per release |

## 8. Reliability and Security Invariants

- The pairing identity lives in app-private storage and is excluded from backup.
- Pairing codes are never written to settings or logs.
- Helper connections are loopback-only.
- Protected settings change only after consent.

## 9. Risks and Dependencies

| Risk | Mitigation |
|---|---|
| App ID `io.hiddemoens.pluskey` is upstream's | Sideload only; pick an Odin ID and verify the OnePlus integration before any store release |
| Firmware changes break Plus Key events | Test after each OxygenOS update |
| LGPL spake2 native libs | Kept as separate `.so` files with source in `deps/` so users can replace them |
| Origin remote on another GitHub account | Publish from `Biowulf21/odin-android` (`publish` remote) |

## 10. Release Acceptance

Increment `versionCode`, tag `vX.Y.Z`, run host tests, build with the Odin
signing key, attach one signed APK with its SHA-256 digest to a GitHub release on
`Biowulf21/odin-android`, and confirm in-app update from the previous release.
