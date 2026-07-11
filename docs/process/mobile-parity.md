# LangBang Mobile Parity Workflow

LangBang is mobile-first. Android and iOS are the two product surfaces; the web
app is paused and is not a source of truth, an acceptance surface, or parity
evidence.

## Source-of-truth order

For every new feature, use this order:

1. **APK contract** — implement and verify the feature in the Android app.
2. **Shared contract** — move state, events, selection rules, sequencing, and
   backend calls into `shared/commonMain` whenever they are not platform APIs.
3. **iOS renderer** — render the same shared contract in SwiftUI, preserving the
   same visual hierarchy, density, labels, empty/loading/error states, and
   control slots. Native platform conventions may replace a control only inside
   the same-sized slot.
4. **Paired verification** — exercise the feature on Pixel 10 (phone contract),
   A9 (large-layout contract), and an iPhone simulator or physical iPhone.
5. **Parity matrix** — advance the row in `mobile-parity-matrix.tsv` only after
   the evidence for that stage exists.

Do not implement product behavior independently in Kotlin and Swift. The shared
model owns behavior; Compose and SwiftUI own layout, accessibility, and native
platform integrations.

## Required feature packet

Every feature change should contain:

- one stable feature ID used in the matrix and tests;
- Android source plus a Pixel or A9 screenshot;
- a shared state/event contract and common tests, unless the feature is purely
  platform presentation;
- an iOS renderer plus an iPhone screenshot;
- a note for any deliberate platform-convention difference;
- loading, empty, error, offline, and active states where applicable.

The global Now Voicing surface is part of every audio-producing feature packet.
If a new APK action can start audio, its shared event must update Now Voicing and
the iOS renderer must expose the same transport, star, slow/English, and stop
states.

## Status meanings

- `android-ready`: APK behavior and its device evidence exist.
- `shared-ready`: behavior is represented in common Kotlin with tests.
- `ios-visual`: iOS has the matching screen and control layout, but behavior or
  live data is still incomplete.
- `verified`: shared behavior and both mobile renderers passed paired device
  verification. This is the only status that means parity is done.

## Gate

Run the fast structural gate during implementation:

```bash
scripts/check-mobile-parity.sh --static
```

Run the full gate before pushing a mobile feature:

```bash
scripts/check-mobile-parity.sh --full
```

The full gate runs the structural contract, shared iOS tests, Android EN-PL
build, and an iPhone-simulator Xcode build. Device screenshots remain an
explicit paired verification step because a successful compile is not visual
proof.

## Current baseline

The July 11 baseline establishes the full iOS navigation and visual shell,
including Now Voicing, against Pixel 10 and A9 references. Most rows are marked
`ios-visual`, not `verified`: live lesson repositories, audio queues, auth,
cloud sync, mic scoring, and settings behavior still need to move through the
shared-contract stage. This distinction prevents a polished shell from being
mistaken for behavioral parity.
