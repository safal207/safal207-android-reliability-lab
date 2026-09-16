# Android Reliability Lab

A small Android portfolio project focused on one question:

> Can a user action remain correct when the network disappears, requests are retried, events arrive twice, or the app is interrupted and resumed?

This repository is intentionally built as a sequence of small, independently verifiable reliability beads rather than as a large demo app.

## Status

**Bead 000 — Headless Android environment:** **PASS.** A clean GitHub Actions runner provisions JDK + Android command-line tooling without Android Studio and proves the SDK/toolchain boundary.

**Bead 001 — Thin vertical slice:** **PASS.** The Kotlin/Compose application passes `test`, `lint`, and `assembleDebug`; CI produces a debug APK, boots an Android API 35 emulator, installs and cold-launches the app, observes the live process, captures a screenshot, and matches the deterministic incident UI through the Android UI hierarchy. Proof was produced on commit `93d08feec4f1e04cfe97ee734af261af00157cad` and merged through PR #4.

**Bead 002 — API boundary:** **PASS on proof commit `85f55c712b46b6515cf5828fcacbd0138554d583`; PR #6 awaits review.** The app defaults to an HTTP-backed repository with explicit DTO mapping and `Loading`, `Content`, and `Error` states. Deterministic HTTP 200/500 JVM tests and an API 35 runtime success path are proven by the receipts below. Issue #5 stays open for review/merge.

## Build philosophy

Android Studio is optional convenience tooling, not part of the build trust boundary.

The development path is:

```text
Codex / developer
      ↓
Git repository
      ↓
Headless Android toolchain
(JDK + SDK + Gradle)
      ↓
test + lint + assembleDebug
      ↓
APK
      ↓
headless Android emulator
      ↓
runtime screenshot / UI evidence / CI receipt
```

The repository includes a Dockerfile and command-line bootstrap so the Android environment can be created without Android Studio.

SDK-only environment check:

```bash
bash scripts/bootstrap-android.sh --sdk-only
```

Reproducible container:

```bash
docker build -t android-reliability-lab-headless .
```

Full application verification:

```bash
bash scripts/bootstrap-android.sh
```

which is required to execute:

```bash
./gradlew --no-daemon test lint assembleDebug
```

A green build is not automatically a runtime claim. Runtime/device behaviour is claimed only when the emulator/device evidence is also produced.

## Product scenario

The app models a small incident/task workflow:

1. Load a list of incidents from an API.
2. Open an incident.
3. Change its state.
4. Persist the local state.
5. Synchronize the change with the server.
6. Recover correctly after offline periods, retries, duplicate submissions, and process interruption.

The UI is deliberately small. The interesting part is the behaviour at system boundaries.

## Reliability questions

The lab will progressively prove behaviour for:

- API success, loading, empty, and error states
- temporary network loss
- local persistence
- retry after failure
- duplicate action prevention / idempotent submission
- interrupted-session recovery
- stale or conflicting state
- observable evidence for pass/fail behaviour

## Planned Android stack

- Kotlin
- Jetpack Compose
- ViewModel + StateFlow
- Retrofit / OkHttp
- Room
- WorkManager
- JUnit
- Compose UI tests
- GitHub Actions

The exact dependency set is introduced only when a bead needs it.

## Evidence model

Each reliability bead should leave a small proof trail:

| Failure / condition | Expected behaviour | Evidence |
|---|---|---|
| Normal API response | Incident list renders once | test / screenshot |
| Network unavailable | User sees recoverable state | test / screenshot |
| Retry | One logical action produces one logical effect | test / trace |
| Process interruption | Pending work survives and resumes safely | test / trace |
| Duplicate submission | Server-side effect is not duplicated | test / receipt |

No row is marked as proven until executable evidence exists in the repository.

## Portfolio goal

This is not a production application and not a claim of production deployment experience.

It is a compact, inspectable proof of Android implementation plus QA/reliability reasoning around API boundaries, state, retries, duplicate actions, persistence, and recovery.

## Beads

- [x] **000 — Headless environment:** JDK + Android SDK + Docker + CI without Android Studio
- [x] **001 — Thin vertical slice:** Compose app + deterministic incident-list screen + build/runtime evidence
- [x] **002 — API boundary:** HTTP-backed source + DTO mapping + `Loading` / `Content` / `Error`; proof below, review in [PR #6](https://github.com/safal207/safal207-android-reliability-lab/pull/6)
- [ ] **003 — Persistence:** cache incidents locally with Room
- [ ] **004 — Offline mutation:** queue a state change when connectivity is unavailable
- [ ] **005 — Retry safety:** retry without creating duplicate logical effects
- [ ] **006 — Interrupted-session recovery:** resume pending work after app/process restart
- [ ] **007 — Verification:** unit/UI tests + CI evidence
- [ ] **008 — Portfolio package:** screenshots, architecture diagram, reproducible demo steps, APK artifact

## Bead 002 receipts

Proof commit: `85f55c712b46b6515cf5828fcacbd0138554d583`. [GitHub Actions run 35155156588](https://github.com/safal207/safal207-android-reliability-lab/actions/runs/35155156588), attempt 1, passed both `environment-proof` and `runtime-proof`. This README update follows that proof; the receipt names the code commit, not this later documentation commit.

- Fixed command: `./gradlew --no-daemon test lint assembleDebug` — **PASS**.
- Three JVM tests per variant (debug/release): HTTP 200, HTTP 500, and the preserved fake-source test; zero failures, errors or skips. Lint: zero errors, five warnings.
- API 35 emulator: cold launch, live process, exactly one `GET /incidents` with HTTP 200, and all three HTTP-specific ids/titles/statuses in app-owned UI hierarchy nodes.
- Runtime files: `receipt.json`, `fixture-requests.jsonl`, `incident-list.png`, `window.xml`, guest `/health` evidence, install/launch output and logcat.

| Artifact | ID | Archive SHA-256 |
|---|---|---|
| [Debug APK](https://github.com/safal207/safal207-android-reliability-lab/actions/runs/35155156588/artifacts/10470947360) | `10470947360` | `9c2cc438b795f6c628b74d4bd2f65dff6608938f34f81082be1a6de55bebd1c0` |
| [Runtime proof](https://github.com/safal207/safal207-android-reliability-lab/actions/runs/35155156588/artifacts/10470803480) | `10470803480` | `47145811d57a91f68307a210e154abe6f5da87bef0d82abb4db7ef79218f68e7` |
| [JVM/lint reports](https://github.com/safal207/safal207-android-reliability-lab/actions/runs/35155156588/artifacts/10470444390) | `10470444390` | `28704aff9d753b88dec46bf1c635b80840618492cd34782be102fd6b882907b5` |

The APK inside its archive has SHA-256 `62fe14177ce2d708d22d21fdda632f7a75b38ea11ba5db7afa5c39d9999533f4`. The committed fixture and observed response share SHA-256 `63163f9f10c5e124434ac6d5370b78ffd1dc4728c1ce57279eb6fda1ef6ec827`. Downloaded archive and receipt hashes were verified, and the screenshot was inspected. Artifacts currently expire on 2026-12-15.

### Reproduce the HTTP demo

Build with the fixed command above and start an API 35 emulator. The debug app uses `http://10.0.2.2:8765/`; cleartext is allowed only for that emulator host in the debug manifest. The repository factory accepts a base URL, and the APK can be configured with `-PincidentBaseUrl=https://your-host/` at build time.

```bash
mkdir -p runtime-apk
cp app/build/outputs/apk/debug/app-debug.apk runtime-apk/
bash scripts/prove-http-runtime.sh
```

The script starts the host fixture, verifies `/health` from the guest before launching the app, and checks the runtime evidence. Guest readiness checks never request `/incidents`; the app issues that request. A failed runtime check remains a failure and retains diagnostic artifacts. Without the fixture, the app shows `Unable to load incidents.` rather than fake content.

## Claim ceiling

The repository proves only the completed beads above, on their cited proof commits. Bead 002 proves HTTP fetch -> DTO mapping -> explicit `Loading` / `Content` / `Error`, JVM HTTP 200/500 tests, and a deterministic API 35 runtime success path. The HTTP 500 screen is not emulator-proven. Persistence, offline behaviour, retry safety, idempotency, recovery, authentication and production-backend behaviour remain unproven.
