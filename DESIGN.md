---
version: 1
name: Biowulf21 apps design system
description: A shared Material 3 "Expressive" language for Gjall, Heimdall, Odin and Cashew: calm cool-grey surfaces, one user-chosen accent, grouped rounded cards, round tonal badges and quick, unbouncy motion.
source_of_truth: Heimdall `chooloolib/.../ui/theme/` and Gjall `presentation/.../common/widget/ExpressiveStyler.kt`

colors:
  # Neutral ramp shared by Heimdall and Gjall (Expressive). Light / dark.
  background:               { light: "#F8FAFC", dark: "#0D0E12" }
  surface:                  { light: "#FFFFFF", dark: "#131418" }
  surface-container:        { light: "#EAF0F5", dark: "#18191E" }
  surface-container-high:   { light: "#DFE8F1", dark: "#1C1F26" }
  surface-container-highest:{ light: "#D8E3EE", dark: "#25272E" }
  on-surface:               { light: "#17212B", dark: "#F1F1FC" }
  on-surface-variant:       { light: "#495B6C", dark: "#B5B5BD" }
  outline: "{colors.on-surface-variant}"
  outline-variant: "{colors.surface-container-high}"
  # Accent: one colour per app, user-changeable. Every accent role is derived from it.
  accent-default:
    heimdall: "#42A5F5"
    gjall: "#0097A7"
    odin: "#FF902F"
    cashew: "dynamic (Material You) until migrated"
  primary: "accent, mixed toward on-surface until it reaches 4.5:1 against surface-container-highest"
  on-primary: "black or white, whichever contrasts more with primary"
  primary-container: "the raw accent"
  secondary-container: "mix(surface-container, accent, 12% light / 20% dark)"
  on-secondary-container: "{colors.on-surface}"
  danger: "Material 3 error role (end call, delete); Gjall swipe-delete #D93025"

typography:
  font-family: "System sans (Roboto on most devices); honour the user's font choice where the app offers one (Gjall: Lato or system)"
  scale: "Material 3 default type scale"
  overrides:
    headlineSmall: { fontWeight: 600 }
    titleLarge:    { fontWeight: 600 }
    titleMedium:   { fontWeight: 500 }
  toolbar-title: "1.4 × the toolbar's base size"
  drawer-title: { fontSize: 28sp }
  drawer-label: { fontSize: 16sp, fontWeight: 500 }
  section-pill: { fontSize: 12sp, fontWeight: 700 }

rounded:
  extra-small: 4dp
  small: 8dp
  medium: 12dp
  large: 16dp
  extra-large: 20dp
  group-outer: 28dp
  group-inner: 4dp
  bubble: 18dp
  bubble-tail: 4dp
  pill: 9999dp

spacing:
  xs: 4dp
  sm: 8dp
  md: 16dp
  lg: 24dp
  xl: 32dp
  page-margin: 12dp
  group-gap: 3dp
  group-break: 12dp

sizes:
  toolbar: 72dp
  bar: 64dp
  list-row: 64dp
  drawer-row: 56dp
  button: 56dp
  button-large: 72dp
  keypad-key: 80dp
  icon-badge: 40dp
  avatar-small: 44dp
  avatar-large: 96dp
  min-touch-target: 48dp

motion:
  ease-out: "cubic-bezier(0.23, 1, 0.32, 1)"
  surface-change: 220ms
  ceiling: 300ms
  press-spring: { dampingRatio: 1.0, stiffness: 3800 }

elevation:
  soft-shadow: "12dp blur, 3dp y-offset, black 12% + 1dp black 10% ring"
---

# Design

This file is shared, word for word, by every app in Biowulf21's personal app
collection: **Gjall** (messages), **Heimdall** (phone and contacts), **Odin**
(Plus Key and edge-swipe actions) and **Cashew** (budgeting). Edit it in one repo,
then copy it to the others in the same change.

The language comes from Gjall and Heimdall only. Heimdall's Compose theme
(`ChoolooTheme` with `DesignStyle.EXPRESSIVE`) is the reference implementation,
and Gjall reproduces it in the View system through `ExpressiveStyler`. Odin and
Cashew have not adopted it yet; see [Adoption by app](#adoption-by-app).

## Principles

- **Quiet surfaces, one accent.** Screens are cool, low-contrast greys. The accent
  marks the one thing that is selected, active or primary, and nothing else.
- **Group, don't divide.** Related rows sit in one rounded card group instead of
  being split by divider lines.
- **The phone's own furniture.** Follow Android's conventions (drawer, top app
  bar, system back, default-app prompts) so the apps feel like the system apps they
  replace. Gjall and Heimdall label themselves **Messages** and **Phone** in the
  launcher for the same reason.
- **Fast and calm.** Motion confirms an action and gets out of the way. Nothing
  bounces, nothing loops.
- **Local and honest.** No promotional surfaces, no upsells. The only "support"
  surface is an optional donation link (see each README).

## Colour

Use the neutral ramp in the front matter for every app. Light and dark share the
same roles; dark is near-black with neutral cool greys, not a navy tint.

- `background` is the window; `surface` sits on it; `surface-container` is the
  default fill for card groups; `-high` and `-highest` are for pills, search
  fields and unselected badges.
- The **accent** is a single RGB value. Apps ship a default and let the user pick
  a Material swatch or a custom `#RRGGBB`. On Android 12+ an app may use dynamic
  (wallpaper) colour when the user has not chosen an accent.
- Derive accent roles, don't hand-pick them:
  - `primary` = the accent, stepped toward `on-surface` in 5% steps until it
    reaches 4.5:1 against `surface-container-highest`.
  - `on-primary` = black or white, whichever has the higher contrast ratio.
  - `primary-container` = the raw accent (used for avatars and filled toggles).
  - `secondary-container` = `surface-container` mixed with 12% (light) or 20%
    (dark) accent. It fills the selected drawer row and tonal icon badges.
  - Heimdall's `heimdallColors()` implements this; copy it, don't re-derive it.
- `danger` uses the Material error role. Destructive actions (end call, delete,
  swipe-to-delete) keep red regardless of accent.
- Never use colour alone for state. Pair it with an icon, label or fill change.
- An optional pure-black theme (Gjall's **Black**) is allowed for OLED screens; it
  replaces `background` and `surface` with `#000000` and keeps the rest of the ramp.

## Typography

- Use the Material 3 type scale on the system sans. Weight carries hierarchy:
  `headlineSmall` and `titleLarge` are SemiBold (600), `titleMedium` is Medium (500).
- Large readouts (dialled numbers, call timers, totals) use the display or
  headline styles with tabular figures.
- Only bundle fonts with an open licence (OFL or Apache). Gjall's Lato is OFL.
  Heimdall's legacy *Classic* style still ships Google Sans files inherited from
  Koler; they are not openly licensed and should be removed before any store
  release (tracked in Heimdall's PRD).

## Shape

- Base shapes: 4 / 8 / 12 / 16 / 20dp (extra-small → extra-large).
- **Grouped cards:** consecutive rows form one group. The first row has 28dp top
  corners, the last row 28dp bottom corners, inner corners are 4dp, and rows are
  3dp apart. A new group starts 12dp below the previous one. Groups inset 12dp from
  the screen edge.
- **Pills:** search fields, section headers, call actions and the end-call button
  are fully rounded.
- **Message bubbles:** 18dp radius with a 4dp corner on the sender's side of the
  first/last bubble in a run.
- **Cookie FAB:** the primary floating action (Gjall compose, Heimdall dial) is a
  nine-lobe scalloped "cookie" that morphs toward a circle while pressed. Reserve
  it for that one action per screen.

## Layout and spacing

- Spacing steps: 4 / 8 / 16 / 24 / 32dp. Page margin is 12dp inside the safe area,
  with 16dp above the first element.
- Top app bars are 72dp tall with a 1.4× title. Toolbar actions are plain icon
  buttons with no container.
- Search sits in a full-width pill in the top bar.
- List rows are at least 64dp; drawer rows are 56dp; keypad keys are 80dp; primary
  buttons are 56dp and large call actions 72dp.
- Heimdall supports **left-handed mode**; mirror one-handed controls rather than
  only flipping text direction.

## Components

| Component | Spec |
|---|---|
| Card group row | `surface-container` fill, `secondary-container` when activated, ripple foreground clipped to the shape |
| Icon badge | 40dp circle, 8dp padding, `secondary-container` fill, `on-secondary-container` icon; 16dp start margin |
| Section header | Small pill: 12dp radius, `surface-container-high`, 12sp bold `on-surface-variant`, 8×4dp padding, 20dp start and top margin |
| Navigation drawer | 28dp corner on the open edge, `surface-container-low`; 28sp title padded 28/32/28/16dp; 56dp rows with a 40dp badge and 16sp medium label; the selected row fills with `secondary-container` so its badge melts into it; badge is `surface-container-highest` when unselected |
| Prompt card (e.g. rating, setup) | Plain tonal card, 28dp corners, `surface-container-high`; no outline |
| Call action | Pill container, `surface-container-high` when off, filled `primary` when on, label below |
| End call | Wide pill, Material error colour, centred at the bottom |
| Avatar | Circle filled with `primary-container` (user may choose squircle in Heimdall) |
| Alphabet index | Fast-scroll rail on long contact lists; announces "Jump to X" |

## Motion and haptics

- Enter/exit: strong ease-out `cubic-bezier(0.23, 1, 0.32, 1)`. Small surface
  changes (search reveal, chip toggles) take 220ms; nothing exceeds 300ms.
- Press feedback: a critically damped spring (stiffness 3800, no overshoot).
- Respect the system animator duration scale; when it is 0, change state instantly.
- Haptics confirm physical-feeling input: dialpad keys, a swipe reaching its
  release distance (Odin), long-press selection. One pulse, never a pattern.

## Elevation

Surfaces are flat. Use the soft shadow only for things that float over content
(FAB, call avatar, swipe indicator). Never stack shadows on cards.

## Writing

- Sentence case, plain verbs: "Add contact", "Pair with code".
- Errors say what failed and what to do: "Couldn’t save the contact. Try again."
- Use curly apostrophes and quotes in UI strings.
- Name Android settings exactly as Android shows them, in bold in docs.

## Accessibility

- Body text ≥ 4.5:1, large text and meaningful graphics ≥ 3:1. The accent
  derivation above guarantees this for `primary`.
- Touch targets ≥ 48dp. Every icon-only control has a content description.
- Support system font scaling without clipping; card rows grow, they don't truncate
  the primary line.
- Support right-to-left layouts where the app ships RTL translations (Gjall,
  Cashew).

## Adoption by app

| App | Status | Notes |
|---|---|---|
| Heimdall | Reference | Compose. `ChoolooTheme` + `heimdallColors()`; `DesignStyle.EXPRESSIVE` is the default, `CLASSIC` keeps Koler's look. |
| Gjall | Adopted | View system. `AppTheme.Expressive` + `ExpressiveStyler` restyles every screen in place; the drawer matches Heimdall's `HomeDrawer`. The classic QKSMS themes remain as an option. |
| Odin | Not yet | Uses a fixed charcoal and orange Material theme (`res/values/styles.xml`). Migrate to the neutral ramp with `#FF902F` as the default accent, grouped cards for settings, and pill headers. |
| Cashew | Not yet | Mirrors upstream Cashew's look screen for screen. Adopt this system only after the reconstruction is verified, and keep Cashew-specific components (budget bars, category grid) on these tokens. |
