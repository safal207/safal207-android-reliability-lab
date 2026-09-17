# AGENTS.md

## Mission

Build this repository as a sequence of small, independently verifiable Android reliability beads.

Android Studio is optional tooling. It is **not** part of the build trust boundary. A clean Linux environment must be able to provision the Android SDK, run Gradle, execute tests/lint, and produce build artifacts from the command line.

## Working contract for Codex / agents

1. Read the active bead / GitHub issue before changing code.
2. Stay inside that bead's scope. Do not pre-build later features.
3. Prefer the smallest dependency set that satisfies the current acceptance criteria.
4. Never claim a build, test, runtime behaviour, screenshot, emulator result, database result, HTTP effect, queued mutation, or APK unless the corresponding evidence was actually produced.
5. Do not require Android Studio for any acceptance criterion.
6. Do not silently weaken a failing check. Report the failure and its boundary.
7. Keep deterministic fixtures deterministic unless the bead explicitly introduces a real external dependency.
8. Preserve the README claim ceiling: evidence first, claim second.
9. A passing UI screenshot does not prove persistence or queue durability; database evidence must be independently inspected.
10. Distinguish transport failure from an HTTP server response. In Bead 004, only a transport/network failure may become a locally queued mutation.
11. Do not add automatic replay, retry loops, WorkManager, reconnect drains, idempotency keys, or duplicate-effect claims in Bead 004.
12. A locally generated pending-mutation id is only local identity in Bead 004; it is not a server-side idempotency proof.

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

If the project needs additional instrumentation/runtime verification, add it explicitly rather than replacing these checks.

## Current sequence

- **Bead 000 — complete:** Android command-line environment can be provisioned without Android Studio.
- **Bead 001 — complete:** runnable Compose vertical slice builds, tests, lints, installs, launches and leaves runtime evidence in CI.
- **Bead 002 — complete:** HTTP-backed incident source, DTO mapping, explicit `Loading` / `Content` / `Error`, HTTP 200/500 tests and API 35 runtime success-path evidence were independently verified and merged through PR #6.
- **Bead 003 — complete:** successful HTTP data persists in Room, replacement/rollback and file-backed close/reopen are proven, and API 35 runtime evidence independently inspects SQLite; merged through PR #8.
- **Bead 004 — active:** GitHub Issue #9 introduces one durable offline mutation queue boundary only.

For Bead 004, read Issue #9 before editing. Completion requires more than a pending table or a button:

- one online mutation path must produce one server-confirmed request with no pending row;
- one deterministic transport failure must create exactly one durable pending mutation;
- the pending row must survive a file-backed database close + reopen boundary;
- HTTP 500 must remain an explicit error and must not be silently treated as offline success;
- a failed pending-write transaction must not publish a pending-success UI state;
- no automatic retry/replay may occur during the bead;
- API 35 runtime evidence must trigger the user action, visibly show pending state, and independently inspect the app-owned database.

Bead 004 evidence must not be described as automatic retry, WorkManager/background execution, idempotency, duplicate-effect safety, process-death recovery, reconnect replay, conflict resolution, or offline startup proof.
