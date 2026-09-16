# AGENTS.md

## Mission

Build this repository as a sequence of small, independently verifiable Android reliability beads.

Android Studio is optional tooling. It is **not** part of the build trust boundary. A clean Linux environment must be able to provision the Android SDK, run Gradle, execute tests/lint, and produce build artifacts from the command line.

## Working contract for Codex / agents

1. Read the active bead / GitHub issue before changing code.
2. Stay inside that bead's scope. Do not pre-build later features.
3. Prefer the smallest dependency set that satisfies the current acceptance criteria.
4. Never claim a build, test, runtime behaviour, screenshot, emulator result, or APK unless the corresponding evidence was actually produced.
5. Do not require Android Studio for any acceptance criterion.
6. Do not silently weaken a failing check. Report the failure and its boundary.
7. Keep deterministic fake data deterministic unless the bead explicitly introduces a real external dependency.
8. Preserve the README claim ceiling: evidence first, claim second.

## Headless commands

Provision / inspect the SDK only:

```bash
bash scripts/bootstrap-android.sh --sdk-only
```

Build the reproducible environment:

```bash
docker build -t android-reliability-lab-headless .
```

Verify the application once a Gradle wrapper exists:

```bash
bash scripts/bootstrap-android.sh
```

The full application verification command is intentionally fixed to:

```bash
./gradlew --no-daemon test lint assembleDebug
```

If the project later needs additional verification, add it explicitly rather than replacing these checks.

## Current sequence

- **Bead 000:** prove the Android command-line environment can be provisioned without Android Studio.
- **Bead 001:** create the smallest runnable Compose vertical slice and produce the first real application build proof.

An emulator is not required for Bead 000. Runtime/device evidence starts only when a later bead explicitly asks for it.
