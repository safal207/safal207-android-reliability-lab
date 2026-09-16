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

Verify the application:

```bash
bash scripts/bootstrap-android.sh
```

The full application verification command is intentionally fixed to:

```bash
./gradlew --no-daemon test lint assembleDebug
```

If the project later needs additional verification, add it explicitly rather than replacing these checks.

## Current sequence

- **Bead 000 — complete:** Android command-line environment can be provisioned without Android Studio.
- **Bead 001 — complete:** runnable Compose vertical slice builds, tests, lints, installs, launches and leaves runtime evidence in CI.
- **Bead 002 — active:** GitHub Issue #5 introduces the REST API boundary and explicit `Loading` / `Content` / `Error` states. The app must fetch deterministic HTTP data at runtime while later persistence/retry/recovery beads remain out of scope.

For Bead 002, read Issue #5 before editing. A source-only implementation is not completion: the HTTP boundary must be independently proven by the fixed build checks, deterministic HTTP tests, and runtime fixture evidence.
