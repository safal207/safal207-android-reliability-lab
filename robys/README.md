# Roby's Coffee House — Android demo

Native Kotlin / Jetpack Compose demo imported from the supplied Roby's archive into the Android Reliability Lab as the independent `:robys` application. The existing incident app remains `:app`.

## User flow

- **Ana Sayfa:** local hero photo and example coffee cards.
- **Keşfet:** introduction to the Taste Journey and category-filtered samples. The primary action opens the existing [Taste Journey](https://safal207.github.io/robys-coffee-house-demo/discover.html) in the user's browser. The journey itself is not implemented natively.
- **Ziyaret:** scrollable contact information, directions and Instagram.

Screen/category selection survives Activity recreation and tab changes. Android Back returns from a secondary tab to Home. Category controls expose their selected state to accessibility services. External links show a message when there is no compatible handler.

The six local samples and their prices came from the supplied draft, not a live menu feed; the UI labels them as demo prices. Hours, address and Instagram match the website source at review time, but that is not independent confirmation from the cafe. This demo has no cart, checkout, payments, account or backend.

## Build without Android Studio

Use the repository's existing JDK 17 / SDK 35 / Gradle 8.9 / AGP 8.7.3 headless toolchain:

```bash
docker build -t android-reliability-lab-headless .
docker run --rm -v "$PWD:/workspace" -w /workspace \
  android-reliability-lab-headless \
  bash -lc './gradlew --no-daemon test lint assembleDebug :robys:assembleDebugAndroidTest'
```

With Android command-line tools already installed:

```bash
bash scripts/bootstrap-android.sh
./gradlew --no-daemon :robys:assembleDebugAndroidTest
```

Installable debug APK: `robys/build/outputs/apk/debug/robys-debug.apk`.
Android 8.0 (API 26) or later is required. A release build needs an owner-managed signing key before distribution.

## Checks and evidence

The Roby's workflow runs the fixed `test lint assembleDebug` verification for both applications, builds matching application/test APKs, then uses an API 35 emulator for five UI contracts:

1. Home action and all three tabs navigate correctly.
2. Category selection filters the sample list and exposes selected semantics.
3. Screen and category survive Activity recreation.
4. The last contact action remains reachable in landscape.
5. The Taste Journey action emits the exact HTTPS browser Intent (intercepted; no external network assertion).

It captures Home, Discover, Visit and landscape screenshots plus cold-launch process/UI evidence. Receipts bind APK hashes, checkout SHA, PR head and workflow run. Source code or a successful build alone does not prove device behavior. Runtime claims require a completed emulator run and inspected artifacts.

## Review fixes

The supplied draft lacked `gradlew` and used an unimported `EspressoSoft`, an unsupported Instagram Material icon and an unescaped resource apostrophe. Importing this module reuses the lab's headless launcher and compatible tooling, corrects those source issues, adds matching Java/Kotlin 17 targets, improves text contrast, and makes contact content scrollable.
