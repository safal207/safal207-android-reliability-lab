# Android Reliability Lab

A small Android portfolio project focused on one question:

> Can a user action remain correct when the network disappears, requests are retried, events arrive twice, or the app is interrupted and resumed?

This repository is intentionally built as a sequence of small, independently verifiable reliability beads rather than as a large demo app.

## Status

**Bead 000 — Headless Android environment:** **PASS.** A clean GitHub Actions runner provisions JDK + Android command-line tooling without Android Studio and proves the SDK/toolchain boundary.

**Bead 001 — Thin vertical slice:** **PASS.** The Kotlin/Compose application passes `test`, `lint`, and `assembleDebug`; CI produces a debug APK, boots an Android API 35 emulator, installs and cold-launches the app, observes the live process, captures a screenshot, and matches the deterministic incident UI through the Android UI hierarchy. Proof was produced on commit `93d08feec4f1e04cfe97ee734af261af00157cad` and merged through PR #4.

**Bead 002 — API boundary:** **ACTIVE in Issue #5.** The next proof replaces the app's default fake source with a deterministic HTTP-backed repository and adds explicit `Loading`, `Content`, and `Error` states. No API-boundary claim is made until that issue's tests and runtime fixture evidence pass.

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
- [ ] **002 — API boundary:** HTTP-backed incident source + explicit `Loading` / `Content` / `Error` states — Issue #5
- [ ] **003 — Persistence:** cache incidents locally with Room
- [ ] **004 — Offline mutation:** queue a state change when connectivity is unavailable
- [ ] **005 — Retry safety:** retry without creating duplicate logical effects
- [ ] **006 — Interrupted-session recovery:** resume pending work after app/process restart
- [ ] **007 — Verification:** unit/UI tests + CI evidence
- [ ] **008 — Portfolio package:** screenshots, architecture diagram, reproducible demo steps, APK artifact

## Claim ceiling

The repository currently proves only the completed beads above. Bead 002 does **not** yet prove HTTP/API behaviour. Persistence, offline behaviour, retry safety, idempotency, and recovery also remain unproven until their own executable evidence exists.
