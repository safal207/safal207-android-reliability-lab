# AGENTS.md

## Mission

Build this repository as a sequence of small, independently verifiable Android reliability beads.

Android Studio is optional tooling. It is **not** part of the build trust boundary. A clean Linux environment must be able to provision the Android SDK, run Gradle, execute tests/lint, and produce build artifacts from the command line.

## Working contract for Codex / agents

1. Read the active bead / GitHub issue before changing code.
2. Stay inside that bead's scope. Do not pre-build later features.
3. Prefer the smallest dependency set that satisfies the current acceptance criteria.
4. Never claim a build, test, runtime behaviour, screenshot, emulator result, database result, or APK unless the corresponding evidence was actually produced.
5. Do not require Android Studio for any acceptance criterion.
6. Do not silently weaken a failing check. Report the failure and its boundary.
7. Keep deterministic fixtures deterministic unless the bead explicitly introduces a real external dependency.
8. Preserve the README claim ceiling: evidence first, claim second.
9. A passing UI screenshot does not prove persistence; database evidence must be independently inspected.
10. Do not turn Room into an offline fallback in Bead 003. HTTP failure must remain an explicit error until a later bead says otherwise.

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

If the project needs additional Room/instrumentation verification, add it explicitly rather than replacing these checks.

## Current sequence

- **Bead 000 — complete:** Android command-line environment can be provisioned without Android Studio.
- **Bead 001 — complete:** runnable Compose vertical slice builds, tests, lints, installs, launches and leaves runtime evidence in CI.
- **Bead 002 — complete:** HTTP-backed incident source, DTO mapping, explicit `Loading` / `Content` / `Error`, HTTP 200/500 tests and API 35 runtime success-path evidence were independently verified and merged through PR #6.
- **Bead 003 — active:** GitHub Issue #7 adds the Room persistence boundary only.

For Bead 003, read Issue #7 before editing. Completion requires more than Room source files:

- successful HTTP data must be persisted;
- a file-backed Room database must survive close + reopen;
- repeated successful refreshes must replace rows deterministically without stale/duplicate rows;
- HTTP 500 must still produce `Error`, not cached content;
- API 35 runtime evidence must independently inspect the app-owned database and match it to the committed HTTP fixture.

Persistence evidence must not be described as offline, retry, idempotency, WorkManager, process-recovery, or conflict-resolution proof.
