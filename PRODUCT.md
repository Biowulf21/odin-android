# Product

## Platform

Android 11 (API 30) or later. Built for OnePlus phones with a Plus Key (tested
upstream on OnePlus 15, OxygenOS 17); edge swipes work on any Android phone.
Distributed as signed APKs from GitHub releases at
[Biowulf21/odin-android](https://github.com/Biowulf21/odin-android).

## Users

- **The owner (Biowulf21)** on his own OnePlus phone.
- **OnePlus owners who sideload apps** and want more from the Plus Key than the
  firmware offers.

## Product Purpose

Odin turns Plus Key presses (single, double, triple, long and custom patterns)
and invisible edge-swipe handles into actions, app launches or chains of actions.
Success means a gesture fires the right action instantly, including while locked
where Android allows it, without accounts, servers or root.

## Positioning

Odin is a fork of Hidde Moens's [PlusKey](https://github.com/hiddemoens/PlusKey)
(Apache-2.0). It adds action chains, edge swipes with a live swipe indicator, an
app fan and its own update channel. It is independent of OnePlus and of the
upstream PlusKey project.

## Operating Context

- Reading the Plus Key and running privileged actions needs a helper process
  started through **Shizuku** or Odin's built-in **Wireless debugging** pairing.
  The helper runs from `/data/local/tmp` as the shell user and only listens on
  loopback.
- Back, Home and Recents from edge swipes work without the helper.
- Odin checks GitHub releases once a day (can be turned off) and verifies an
  update's checksum, package, version code and signing certificate before handing
  it to Android's installer.

## Capabilities and Constraints

- Gestures: single/double/triple/long press, custom press patterns, hold actions,
  left and right edge handles with straight/diagonal-up/diagonal-down swipes.
- Actions: flashlight, sound modes, DND for an hour, screenshot, lock, quick
  settings and notifications, media and volume, Wi-Fi, mobile data, SIM switch,
  hotspot, previous app, default assistant, voice recording, app shortcuts, quick
  tiles, app fan.
- Chains: up to 16 built-in steps; a chain stops at the first failed step.
- **App ID caveat:** Odin keeps upstream's app ID `io.hiddemoens.pluskey` so the
  OnePlus integration keeps working. It cannot be installed next to official
  PlusKey and must not be published to a store under that ID.

## Brand Commitments

- Name **Odin**. Do not use "PlusKey" or OnePlus branding as Odin's name or icon.
- Always credit PlusKey and Hidde Moens (see NOTICE).

## Monetisation

None. Odin is free and Apache-2.0. The only money flow is an optional donation
link (Buy Me a Coffee, also in **Settings → Support**). Donations unlock nothing
and buy no support, warranty or priority. Never add ads, paid features or in-app
purchases.

## Evidence on Hand

Owner-tested on his own device; host tests in `tests/`. No telemetry.

## Product Principles

- Local only: no account, no analytics, connections constrained to loopback.
- Fail visibly: a failed chain step names the step.
- Ask before changing protected settings (automatic startup needs consent).
- Move toward [DESIGN.md](DESIGN.md) (Odin has not adopted it yet).

## Accessibility & Inclusion

English only. Swipe handles stay clear of the keyboard; the swipe indicator size
and colour are adjustable. No formal conformance target.
