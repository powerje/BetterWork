# BetterWork

[![Verify](https://github.com/powerje/BetterWork/actions/workflows/check.yml/badge.svg)](https://github.com/powerje/BetterWork/actions/workflows/check.yml)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.2.0-7F52FF.svg?logo=kotlin&logoColor=white)](https://kotlinlang.org/)
[![Android](https://img.shields.io/badge/Android-SDK%2037-3DDC84.svg?logo=android&logoColor=white)](https://developer.android.com/)

Like the woman said, you [better work](https://www.youtube.com/watch?v=szhf51FTYgY).

Interval timers for Android phones and Wear OS watches.

## Screenshots

<p align="center">
  <img src="docs/images/android-app.png" alt="BetterWork routine library on Android" width="280">
  &nbsp;&nbsp;&nbsp;
  <img src="docs/images/wear-os-app.png" alt="BetterWork routine picker on Wear OS" width="280">
</p>

## Features

- [Japanese Walking](https://japaneseintervalwalking.com/blog/japanese-walking-your-complete-guide/), [Tabata](https://www.womenshealthmag.com/fitness/a34221488/what-is-tabata/), [Pomodoro](https://en.wikipedia.org/wiki/Pomodoro_Technique), and custom routines
- Named steps, repeats, time limits, and automatic or confirmed transitions
- Distinct vibration cues and optional sound
- Standalone phone and watch playback
- Phone-to-watch routine sync through the Wear OS Data Layer
- Android notification controls and home-screen widgets
- Import and export for routine libraries
- Light, dark, and system themes on Android

## Requirements

- JDK 21
- Android SDK 37
- Android SDK command-line tools

## Build

```sh
just build
```

The debug APKs are written to:

```text
phone/build/outputs/apk/debug/phone-debug.apk
wear/build/outputs/apk/debug/wear-debug.apk
```

## Test

```sh
just test       # JVM tests and walk-report tests
just lint       # Detekt and Android lint
just precommit  # Full local verification gate
```

## Project layout

| Module | Purpose |
| --- | --- |
| `shared` | Routine domain, persistence, and presentation logic |
| `platform` | Android services, cues, notifications, and device sync |
| `phone` | Android phone app and widgets |
| `wear` | Wear OS app |

