# Luteal

A menstrual cycle tracker for Android, built around one rule: what you recorded
and what the app calculated are never presented as the same thing.

Recorded observations are labelled as recorded. Predictions are labelled as
estimates, shown as a range rather than a confident date, and the app says what
the range was derived from. Luteal makes no diagnosis.

The app is French-first; English and Spanish are also shipped. It works
entirely offline by default, with optional end-to-end encrypted sync.

## Features

### Today

- **Countdown ring.** Day 1 is the start of the last period; the ring counts
  down to the next one and is tinted with what will probably happen on each
  day (period, follicular, around ovulation, luteal), with a legend. Logged
  flow is drawn on the ring.
  - The ring length comes from your own recorded cycles. Until one cycle is
    complete, a typical 28-day cycle is used and labelled as such.
  - With pill tracking on, the ring is one pack long (active + pause days) and
    counts down to the next pill break; the pause is marked as the expected
    withdrawal bleed.
  - A late period is reported as "N days later than expected" instead of the
    ring wrapping around.
- **Period starts from logged flow.** Logging light, medium or heavy flow at
  least 14 days after the last start begins a new cycle automatically, so the
  cycle day and estimate never go stale because "Log period start" was not
  tapped. Spotting never starts a cycle. Missing starts in older data are
  added once after updating.

### Journal

- Months scroll vertically; a bar under each day marks period days, darker for
  heavier flow, and pill days carry a pill icon.
- The title, view tabs, own/partner switch, month toolbar and weekday row stay
  pinned while the months scroll.
- With pill tracking on, the Journal shows the calendar only: the timeline,
  variability, temperature and pattern views are hidden because they do not
  apply to a pill regimen.

### Settings

- Choose which screen the app opens on (Today, Journal, …).

### Duo (optional, needs sync)

A tracker can pair with a partner and choose, field by field, what the partner
may see. Everything is composed and encrypted on the tracker's device; a field
that is not granted is never sent.

- Cycle day, period estimate, mood, energy and pill status (with an optional
  partner-side reminder and "pill taken" alert).
- **Cycle countdown:** the same ring as on Today. The partner's device keeps the
  day count moving between updates and rolls a pill pack forward on its own.
- **Shared journal:** flow, estimate, mood/energy and pill days for the last 3,
  6 or 12 months, shown read-only in the partner's Journal calendar, which also
  reaches a year ahead for shared pill packs and the estimate. A line shows when
  the shared copy was last updated and how many period starts it contains.
- Private notes, symptoms, pain and biomarkers are never shared.

Support requests were removed in 2.0.4; a grant left over from an older version
is withdrawn automatically.

## Privacy

- Tracking data lives in a local Room database. Nothing leaves the device until
  sync is explicitly enabled - `SyncMode` defaults to `OFFLINE_LOCAL`.
- Android cloud backup is disabled for the app's data (`allowBackup=false`).
- Home-screen widgets start concealed and never show private notes or detailed
  observations. Revealing a widget sends its visible content to the launcher,
  which may retain previews or screenshots outside Luteal's control.
- No advertising, no analytics, no Google Play Services, no Firebase.
- Crash reporting is ACRA with a consent dialog: nothing is sent unless the user
  reads it and chooses to send. Tracking data is not included.
- When sync is on, records are encrypted on this device before upload. The
  server stores ciphertext and routing metadata. It also issued the account
  code and receives that code again when a device is added, so the app does
  not claim the operator has never seen the key. See
  [docs/architecture/E2EE_DESIGN.md](docs/architecture/E2EE_DESIGN.md).

## Building

Requires JDK 21 and the Android SDK (compileSdk 37, minSdk 26).

```sh
./gradlew assembleDebug          # debug build
./gradlew testDebugUnitTest      # unit tests
./gradlew assembleRelease        # release build
```

A plain clone builds with no other checkout present: the folicular API contract
is vendored under [`contract/`](contract/README.md).

Release signing reads `keystore.properties` at the repository root - copy
`keystore.properties.example` and fill it in. Without that file the release
build is left unsigned rather than falling back to the debug key.

### Side-by-side build

`-Pluteal.sideBySide=true` builds with the application ID
`fr.luteal.app.custom` and the launcher label "Luteal Custom", so it installs
next to the regular `fr.luteal.app` instead of replacing it. Default builds,
including F-Droid's, are unaffected.

```sh
./gradlew assembleRelease -Pluteal.sideBySide=true
```

### Releases

Pushing a tag `v<versionName>` runs `.github/workflows/release.yml`, which
builds a signed side-by-side APK and attaches it to a GitHub release. Before
tagging:

1. Bump `versionCode` and `versionName` in `app/build.gradle.kts`. The tag must
   match `versionName` exactly (`v2.0.4` for `"2.0.4"`).
2. Add `fastlane/metadata/android/{en-US,fr-FR,es-ES}/changelogs/<versionCode>.txt`,
   each at most 500 bytes.
3. Keep `values`, `values-fr` and `values-es` strings in parity
   (`scripts/check-string-parity.py`).

```sh
git tag v2.0.4
git push origin v2.0.4
```

The workflow needs the secrets `RELEASE_KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`,
`KEY_ALIAS` and `KEY_PASSWORD`.

## Layout

| Path | Contents |
| --- | --- |
| `app/src/main/java/fr/luteal/app` | Screens, navigation, view models, DI wiring |
| `app/src/main/java/fr/luteal/core` | Domain model, Room storage, sync, crypto |
| `contract/` | Vendored folicular OpenAPI spec and conformance fixtures |
| `docs/architecture` | Sync boundary, E2EE design, backend integration |
| `docs/product` | Feature research, roadmap, and widget behavior |
| `docs/research` | Cycle research notes and source register |
| `fastlane/` | F-Droid listing metadata |

## Backend

Sync talks to [folicular](https://github.com/JoeriKaiser/folicular), a Go
service. It is optional: the app is fully usable without it. The server address
defaults to the official Luteal service (`BuildConfig.SYNC_BASE_URL`) and can be
pointed to any self-hosted folicular instance in Settings.

## Licence

GNU General Public License v3.0 or later. See [LICENSE](LICENSE).

Luteal is free software: you can redistribute it and/or modify it under the
terms of the GNU General Public License as published by the Free Software
Foundation, either version 3 of the License, or (at your option) any later
version. It is distributed in the hope that it will be useful, but WITHOUT ANY
WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
PARTICULAR PURPOSE. See the GNU General Public License for more details.
