# Android Reliability Lab

A small Android portfolio project focused on one question:

> Can a user action remain correct when the network disappears, requests are retried, events arrive twice, or the app is interrupted and resumed?

This repository is intentionally built as a sequence of small, independently verifiable reliability beads rather than as a large demo app.

## Status

**Bead 000 — Headless Android build environment:** implementation proposed; CI evidence is required before this bead is marked proven.

**Bead 001 — Thin vertical slice:** project boundary defined. Android application implementation is next.

## Build philosophy

Android Studio is optional convenience tooling, not part of the build trust boundary.

The target development path is:

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
APK / test evidence / CI receipt
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

Once Bead 001 adds the Gradle Android project, full verification becomes:

```bash
bash scripts/bootstrap-android.sh
```

which is required to execute:

```bash
./gradlew --no-daemon test lint assembleDebug
```

Until a Gradle wrapper and Android project exist, a passing SDK bootstrap is **not** an application build proof.

## Product scenario

The app will model a small incident/task workflow:

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

The exact dependency set will be introduced only when a bead needs it.

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

- [ ] **000 — Headless environment:** JDK + Android SDK + Docker + CI without Android Studio
- [ ] **001 — Thin vertical slice:** Compose app + one incident-list screen using deterministic fake data
- [ ] **002 — API boundary:** replace fake source with a small HTTP API and explicit loading/error states
- [ ] **003 — Persistence:** cache incidents locally with Room
- [ ] **004 — Offline mutation:** queue a state change when connectivity is unavailable
- [ ] **005 — Retry safety:** retry without creating duplicate logical effects
- [ ] **006 — Interrupted-session recovery:** resume pending work after app/process restart
- [ ] **007 — Verification:** unit/UI tests + CI evidence
- [ ] **008 — Portfolio package:** screenshots, architecture diagram, reproducible demo steps, APK artifact

## Claim ceiling

Until the corresponding evidence is committed, this repository claims only what can be independently inspected in its current state.
