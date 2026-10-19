# AvLog

A personal flight-duty logbook for Android: block times per sortie, a pre-flight BA check, cumulative
flight-time (FDTL) tracking with planning, and license/rating expiry alerts.

> [!WARNING]
> FDTL limits live only in `fdtl/FdtlLimits.kt`: 24 h / 11 h, plus the CAR "Maximum Cumulative Flight
> Time Limitations" table (sub-paras 9.1-9.5: 7 d 35 h, 14 d 65 h, 28 d 100 h, 90 d 300 h, 365 d 1000 h).
> Record the CAR revision there. AvLog is an aid, not an approved record-keeping system.

## Build

Requirements: Android Studio (AGP 9.4), JDK 17+, Android SDK 36.

```bash
./gradlew assembleDebug            # debug APK
./gradlew testDebugUnitTest        # JVM unit tests
./gradlew connectedDebugAndroidTest  # migration + DAO tests (needs a device/emulator)
./gradlew lintDebug
./gradlew bundleRelease            # R8-shrunk AAB
```

Release signing is read from a git-ignored `keystore.properties` in the project root:

```properties
storeFile=/absolute/path/to/avlog.jks
storePassword=...
keyAlias=...
keyPassword=...
```

Without it, `bundleRelease` produces an unsigned bundle.

## Architecture

Single `app` module, Jetpack Compose + Room + WorkManager, no DI framework.

| Package | Contents |
| --- | --- |
| `core` | `Clock` (injected everywhere; no direct `System.currentTimeMillis()`), second/day tickers, locale-independent formatters |
| `fdtl` | `FdtlLimits` (the only place limits live) and the pure `FdtlCalculator` |
| `data` | Room entities/DAOs, `Migration4To5`, repositories, CSV export, JSON backup/restore |
| `duty`, `records`, `licenses` | Screens and view models per tab; `ExpiryEvaluator` and notifications |
| root | `AvLogApp` (owns the single database and repositories), `MainActivity`, `ExpiryWorker` |

Data model (Room v5):

- `sorties(id, dateUtc, blockOff, blockOn?, editedAt?)`: one row per sortie; `blockOn` is null while airborne.
- `duty_days(date, baCheckDone, baCheckTime)`: BA check per UTC day.
- `licenses(id, name, expiryDate ISO, warningDays, lastNotifiedThreshold?)`.
- `duty_records_v4_backup`: the untouched pre-v5 table, kept for one release; drop it in v6.

Schemas are exported to `app/schemas`. Every version bump needs a migration and a `MigrationTest`;
there is no destructive fallback.

## Rules implemented

| Rule | Behaviour |
| --- | --- |
| Day of a sortie | UTC date of Block Off. A flight crossing 00:00Z stays on the day it started. |
| FDTL windows | 24H (rolling) and 7D / 14D / 28D / 90D / 365D (calendar), all shown on the Records tab. |
| Calendar window (live) | N consecutive UTC days ending on and including today. |
| Rolling 24H (live) | Block time overlapping the last 24 hours; a sortie partly inside counts partly. Re-evaluated every minute. |
| FDTL planning | Limit minus flying before the planned date (calendar: the N-1 days before it; 24H: the last 24 h when planning today, else the 24 h before 00:00Z) = what can be flown that day. |
| Warning | Approaching at 90 % of any window, exceeded at 100 %; the most severe window is shown. |
| Durations | Completed sorties only, each floored at 0; consumed time rounds up, remaining rounds down. |
| BA check | Valid for 12 hours from confirmation (also across UTC midnight). |
| Stale sortie | Open > 12 h shows a banner to set the Block On time. |
| License alerts | Thresholds at `warningDays`, 7, 1, 0 days and expired; each fires once, and missed runs catch up. |

## Backups

- Records → ⋮ → **Back up (JSON)** / **Restore from backup**: versioned format, validated before import,
  merge or replace (with confirmation). This is the recovery path.
- Android Auto Backup is limited to the database (`backup_rules.xml`, `data_extraction_rules.xml`).
