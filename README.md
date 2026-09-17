# Android Reliability Lab

A small Android portfolio project focused on one question:

> Can a user action remain correct when the network disappears, requests are retried, events arrive twice, or the app is interrupted and resumed?

This repository is intentionally built as a sequence of small, independently verifiable reliability beads rather than as a large demo app.

## Status

**Bead 000 — Headless Android environment:** **PASS.** A clean GitHub Actions runner provisions JDK + Android command-line tooling without Android Studio and proves the SDK/toolchain boundary.

**Bead 001 — Thin vertical slice:** **PASS.** The Kotlin/Compose application passes `test`, `lint`, and `assembleDebug`; CI produces a debug APK, boots an Android API 35 emulator, installs and cold-launches the app, observes the live process, captures a screenshot, and matches the deterministic incident UI through the Android UI hierarchy. Proof was produced on commit `93d08feec4f1e04cfe97ee734af261af00157cad` and merged through PR #4.

**Bead 002 — API boundary:** **PASS on proof commit `85f55c712b46b6515cf5828fcacbd0138554d583`; independently verified and merged through PR #6.** The app defaults to an HTTP-backed repository with explicit DTO mapping and `Loading`, `Content`, and `Error` states. Deterministic HTTP 200/500 JVM tests and an API 35 runtime success path are proven by the receipts below.

**Bead 003 — Room persistence boundary:** **PASS on proof commit `4f154d9138c83f863b5fd822a3e27f4960c926d4`; independently verified and merged through [PR #8](https://github.com/safal207/safal207-android-reliability-lab/pull/8).** Successful HTTP data is written to Room before `Content`. File-backed close/reopen, deterministic replacement, HTTP failure without cached fallback, and failed-write rollback passed on API 35. Runtime database rows were independently inspected with host SQLite.

**Bead 004 — Offline mutation queue boundary:** **CI PASS on proof commit `e3005305d007e515592fdac1bd93e783640245a0`; independent QA pending in [PR #10](https://github.com/safal207/safal207-android-reliability-lab/pull/10).** One loaded incident's state-change intent is durably queued after a transport failure and visibly marked pending. Online confirmation, pending close/reopen, HTTP error handling, atomic rollback and bounded absence of replay are proven below. [Issue #9](https://github.com/safal207/safal207-android-reliability-lab/issues/9) remains open; the PR is not merged.

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
- [x] **002 — API boundary:** HTTP-backed source + DTO mapping + `Loading` / `Content` / `Error`; proof below, merged through [PR #6](https://github.com/safal207/safal207-android-reliability-lab/pull/6)
- [x] **003 — Persistence:** HTTP data persisted in Room; proof below, merged through [PR #8](https://github.com/safal207/safal207-android-reliability-lab/pull/8)
- [ ] **004 — Offline mutation:** durable pending intent after transport failure; CI proof below, independent QA pending in [PR #10](https://github.com/safal207/safal207-android-reliability-lab/pull/10)
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

## Bead 003 receipts

Proof commit: `4f154d9138c83f863b5fd822a3e27f4960c926d4`. [GitHub Actions run 35159110221](https://github.com/safal207/safal207-android-reliability-lab/actions/runs/35159110221), attempt 1, passed both `environment-proof` and `runtime-proof`. The [PR run 35159113998](https://github.com/safal207/safal207-android-reliability-lab/actions/runs/35159113998) also passed. This README update follows the proof; the receipts identify the tested code commit, not this later documentation commit.

- Fixed command: `./gradlew --no-daemon test lint assembleDebug` — **PASS**. Three JVM tests per variant (debug/release), including the preserved HTTP 200/500 tests; zero failures, errors or skips. Lint: zero errors, nine warnings (seven dependency-update notices, backup-rule configuration and missing launcher icon).
- Additional command: `./gradlew --no-daemon assembleDebugAndroidTest` — **PASS**. Both APKs are built in the same container so they share the debug signing key.
- API 35 instrumentation: **5 passed, 0 failed, 0 skipped**, runner output `OK (5 tests)`. Each test uses a file-backed Room database; the reopen test closes it and opens a fresh instance at the same path without another HTTP fetch.
- Runtime: cold launch, live app PID `2618`, exactly one app `GET /incidents` returning HTTP 200 at `2026-09-16T22:50:37.697582+00:00`, and all HTTP-specific ids/titles/statuses in app-owned UI hierarchy nodes.
- After the success-path UI checks, `run-as` copies the stopped app's database together with WAL/SHM. Host Python/sqlite3 verifies integrity, schema version 1, Room identity and the exact fixture rows. This inspection does not use the production repository to read back its own write.

Test class: `com.safal207.androidreliabilitylab.data.RoomPersistenceTest`.

| Vector | Passing test |
|---|---|
| HTTP 200 fields persisted before Content | `http200PersistsAllFieldsBeforeContent` |
| File-backed close + fresh reopen | `fileBackedDataSurvivesCloseAndFreshReopen` |
| Second response changes/removes rows without duplicates | `secondSuccessfulRefreshReplacesRemovedAndChangedRows` |
| HTTP 500 yields Error despite existing rows | `http500ReturnsErrorWithoutServingExistingRoomRows` |
| Failed write rolls back and cannot publish Content | `failedRoomWriteRollsBackAndNeverPublishesContent` |

| Artifact | ID | Archive SHA-256 |
|---|---|---|
| [Debug APK](https://github.com/safal207/safal207-android-reliability-lab/actions/runs/35159110221/artifacts/10472471192) | `10472471192` | `380551ec14514f303108759bef952e4ea6c804ff855951e3d44a3655ae029c38` |
| [Room test APK](https://github.com/safal207/safal207-android-reliability-lab/actions/runs/35159110221/artifacts/10472044789) | `10472044789` | `7594d2f81d0fed20d1ccf4c6409dfb0fd858934b32da93e7ab44e73cdd6b51d0` |
| [Runtime proof](https://github.com/safal207/safal207-android-reliability-lab/actions/runs/35159110221/artifacts/10473135052) | `10473135052` | `5053a1ef62f32101b2c38b82e2785c98ea53b25589804f6eb40164f2447fd702` |
| [JVM/lint reports](https://github.com/safal207/safal207-android-reliability-lab/actions/runs/35159110221/artifacts/10472044782) | `10472044782` | `65c0dc10220abe8d6fdd62c73ed444a4400b5ca05dfbba2674639b2bb5c2bab1` |

APK file SHA-256: `fad4e3c3b0ee6ee558a7c7f3e39940a8c35860ee2a6699e314164a4534c73bf5`. Test APK file SHA-256: `64ed83a4c26c2fbdaaaf090e9211f0125a2eb7a51e6547b07aeb2e3cd28e2de9`. Artifacts currently expire on 2026-12-15.

The runtime archive includes the unchanged HTTP receipt shape (`receipt.json`), `fixture-requests.jsonl`, `incident-list.png`, `window.xml`, launch/process evidence, and these additional persistence receipts:

- `room-database.tar`: original app-owned database, WAL and SHM snapshot.
- `room-rows.json`: schema version, integrity result, Room identity, row count and extracted rows.
- `room-receipt.json`: code SHA, run/attempt, APK/fixture hashes, HTTP receipt hash, original snapshot/file hashes and extracted-row hash.
- `room-instrumentation.txt` and `room-instrumentation-results.json`: raw runner output and named pass/fail/skip results tied to the test APK hash.

| Runtime evidence | SHA-256 |
|---|---|
| `room-database.tar` | `68a03574a6af9245be66fd3e26ac03fc9609df9a6e755785d7c676fa59a708dc` |
| `databases/incidents.db` | `bfaef8fac18709034d1b023086575733ab2f5ba0628478989b5e704180ca2513` |
| `databases/incidents.db-wal` | `26f6bbc5c86408525e4bea8622bf21dd8a0ffa6ac59713bac0fbb04449e94cff` |
| `databases/incidents.db-shm` | `514d7b98a076e55c40dbaab7d22b3c0e41e802bb298a16d5f2a8fe40535af723` |
| `room-rows.json` | `746b20c70f59a2907a8d3bf2aa90c6b45fdf567a57bd64c4e65bc7e1a3f94b8c` |
| `room-instrumentation.txt` | `30224e810192f12d2d59c16d74346ee63153c3cdfea958dc3a97e4fd811f89d2` |
| `incident-list.png` | `edd40bbb46b206b57126e7e011ac633396ecd305e58fa77c5b90068531db9cb2` |
| `window.xml` | `e32c5fae966358b18313f28aee307a0fce8000c784c74e7db1eb49d387cba043` |

The independently extracted rows match the committed fixture and observed response SHA-256 `63163f9f10c5e124434ac6d5370b78ffd1dc4728c1ce57279eb6fda1ef6ec827` exactly:

| id | title | status |
|---|---|---|
| INC-API-001 | HTTP boundary received | OPEN |
| INC-API-002 | DTO mapping checked | INVESTIGATING |
| INC-API-003 | API content rendered | RESOLVED |

Downloaded archive/file hashes and receipt bindings were checked, the database snapshot was independently queried again with host SQLite, and the screenshot was inspected. Independent QA subsequently completed and PR #8 was merged.

### Reproduce the Room proof

Use one build environment/debug signing key for both APKs and start an API 35 emulator:

```bash
./gradlew --no-daemon test lint assembleDebug
./gradlew --no-daemon assembleDebugAndroidTest
mkdir -p runtime-apk instrumentation-apk
cp app/build/outputs/apk/debug/app-debug.apk runtime-apk/
cp app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk instrumentation-apk/
bash scripts/prove-room-runtime.sh
```

This runs every Bead 002 HTTP/UI/process gate first, then independently inspects the database snapshot and executes the five named instrumentation tests. Missing/skipped/failed tests, mismatched rows, or failed pre-existing gates fail CI.

## Bead 004 receipts

Proof commit: `e3005305d007e515592fdac1bd93e783640245a0`. [Actions run 35187382166](https://github.com/safal207/safal207-android-reliability-lab/actions/runs/35187382166), attempt 1, passed both `environment-proof` and `runtime-proof`. The [PR run 35187384479](https://github.com/safal207/safal207-android-reliability-lab/actions/runs/35187384479) also passed. This documentation follows the proof; these receipts identify the tested code commit.

- Fixed `./gradlew --no-daemon test lint assembleDebug` — **PASS**. Existing JVM tests: 3 per variant, 6 executions, no failures/errors/skips. Lint: 0 errors, 9 warnings. Additional `assembleDebugAndroidTest` — **PASS**; both APKs share the container's signing key.
- Original five Bead 003 Room tests — **5 passed**, unchanged. New mutation tests — **8 passed**, no failures/skips. The runtime executes all prior HTTP/UI/process/Room gates before the separate mutation scenario.
- Room schema 1→2 adds only `pending_mutations`. An explicit migration test preserves the old incidents. The original snapshot validator strictly expects version 2 and also requires zero pending rows before any mutation; all original integrity/fixture checks remain.
- Mutation bodies are one-shot, with connection retries and redirects disabled. HTTP 400/500 and `503 Retry-After: 0` remain errors without queueing or HTTP retransmission. The locally generated mutation id never leaves the local queue.

Test class: `com.safal207.androidreliabilitylab.data.OfflineMutationTest`.

| Vector | Passing test |
|---|---|
| A: one confirmed online PUT; no pending row | `onlineMutationConfirmsWithoutPendingOrReplay` |
| B: transport failure; one durable pending intent | `transportFailureQueuesOneIntentWithoutReplay` |
| C: same pending values after file-backed close/reopen | `pendingIntentSurvivesCloseAndFreshReopen` |
| D: HTTP 500; Error, unchanged incident, no queue | `http500MutationIsErrorWithoutQueueOrLocalChange` |
| HTTP 400 stays a non-queueable error | `http400MutationIsErrorWithoutQueueOrLocalChange` |
| HTTP 503 cannot direct automatic retransmission | `http503RetryAfterZeroDoesNotReplayMutation` |
| E: failed pending insert rolls back status; no Pending UI state | `failedPendingWriteRollsBackWithoutPublishingPending` |
| Additive migration preserves existing data | `versionOneMigrationPreservesIncidentRows` |

| Artifact | ID | Archive SHA-256 |
|---|---|---|
| [Debug APK](https://github.com/safal207/safal207-android-reliability-lab/actions/runs/35187382166/artifacts/10482404570) | `10482404570` | `1cb85ddedd6e87e476ecefaabb77a459a4a94b39dd5beb580fc0c903eeddf7c4` |
| [Instrumentation APK](https://github.com/safal207/safal207-android-reliability-lab/actions/runs/35187382166/artifacts/10482434609) | `10482434609` | `d1485591f01746ec697e1674004eb458952185a8e05920b5e7f79347e450618b` |
| [Runtime proof](https://github.com/safal207/safal207-android-reliability-lab/actions/runs/35187382166/artifacts/10482763363) | `10482763363` | `d88488993480e4b27249c22bcf18ef98c51e110f25e4339222701c6a6b8e04e3` |
| [JVM/lint reports](https://github.com/safal207/safal207-android-reliability-lab/actions/runs/35187382166/artifacts/10482349877) | `10482349877` | `31d2e3737218875ca547808b37887f62bf3905fde3fcf126ddfd73ebee548c9d` |

APK file SHA-256: `ff8499c4fae56afe13c02fd17b8f5d2d72702854c6f0b877effe2602530942e9`. Test APK file SHA-256: `622ddcce6df9864afe484b307ac2677ecad6408e4383609599b3774456d1934c`. Artifacts currently expire on 2026-12-16.

### Mutation runtime evidence

The runtime archive retains the original HTTP/Room evidence at its root. The separate Bead 004 scenario is under `offline-mutation/`:

1. One cold-launch `GET /incidents` returned HTTP 200 at `2026-09-17T05:58:06.383675+00:00`. The response matches fixture SHA-256 `63163f9f10c5e124434ac6d5370b78ffd1dc4728c1ce57279eb6fda1ef6ec827`; `before/` retains its receipt, screenshot, hierarchy and request log.
2. `action.json` records one tap on the enabled, visible **Resolve incident** button located from that hierarchy.
3. One app `PUT /incidents/INC-API-001/status` with `{"status":"RESOLVED"}` reached the fixture at `2026-09-17T05:58:11.542514+00:00`. The fixture recorded `applied: false` and disconnected before any HTTP response. No idempotency key was sent.
4. App PID `3761` remained the same. The target incident showed `RESOLVED` and **Pending synchronization**, without server-confirmed success.
5. The fixture was then set to accept online mutations. During **5.019279283 seconds** (`05:58:13.942245`–`05:58:18.961525` UTC), the mutation request count remained **1→1**. This is a bounded observation, not an unlimited guarantee.
6. Only after the UI and observation checks, the app was force-stopped for a quiescent snapshot. Host SQLite independently checked the copied database and WAL: integrity `ok`, schema version 2, exactly three incidents and one pending row. The other two incidents were unchanged.

| mutationId (local only) | incidentId | targetStatus | createdOrder |
|---|---|---|---|
| `9fa78616-c5f9-4caf-b071-36c44b5ccfca` | `INC-API-001` | `RESOLVED` | `1` |

`pending-rows.json` contains every extracted incident field and pending field. `pending-receipt.json` binds the code/run/attempt, APK, initial GET, mutation attempt, prior Room receipt, snapshot hashes and UI evidence. `mutation-instrumentation-results.json` binds all eight test names to the test APK and raw runner output.

| Evidence under `offline-mutation/` | SHA-256 |
|---|---|
| `pending-database.tar` | `3ad872ffd8f0435dd48a7d91a694d374d4d64438578b9b25bbc5cf1114b280e7` |
| Snapshot `incidents.db` | `bfaef8fac18709034d1b023086575733ab2f5ba0628478989b5e704180ca2513` |
| Snapshot `incidents.db-wal` | `0813b6c0fb7a2a63a21b615bb336adffecd8bb02f62a2f6506d913bed55d8e40` |
| Snapshot `incidents.db-shm` | `bbe9f143fedb05ae277f73d7aa39bedfa5f9f141420b400a3455bf0e372e4284` |
| `pending-rows.json` | `33ac20aebc3bb4621c6b335e49ba8792510e85d80714b325dbe27dccbff28d51` |
| `mutation-requests.jsonl` | `d617c439b1497450373f78968e1b33b798b53e20d082d7225991fe43020ccc10` |
| `no-replay.json` | `79b576369230454d0ed0c5e3fc5158275b55246d317728d7196c9276bfc46f05` |
| `pending-incident.png` | `c35cc13a20a12d92d9e9f94baf4dc8265acb46964bfe4827c688aa38b7b00935` |
| `pending-window.xml` | `a3a184a58bdd85073ec3470d0385d07e15747cfd905440f92fc828a522be8172` |

All four downloaded artifact hashes and receipt/file bindings were verified, both original and pending SQLite snapshots were queried independently again, and the pending screenshot was inspected. PR #10 awaits independent QA; Issue #9 remains open.

### Reproduce the mutation proof

Build and copy both APKs as in the Room proof above, using the same signing key. Use a fresh API 35 emulator: the baseline proof requires an empty pending queue. Then run:

```bash
bash scripts/prove-offline-mutation-runtime.sh
```

The script preserves all prior gates, triggers one UI action, observes the bounded interval and retains raw evidence. It does not clear an existing queue or replay it. Startup still requires a successful HTTP load; pending intents are not drained or reconciled in this bead.

## Claim ceiling

The repository claims evidence only on the cited proof commits. Beads 001–003 establish the existing build, HTTP, UI and persistence boundaries. Bead 004 establishes one durable pending local intent after a deterministic transport failure, distinct server-confirmed/pending/error states, atomic queue-write rollback, database close/reopen, the additive Room 1→2 migration, and the stated no-replay observation. HTTP error states are instrumented; their rendered error screens are not emulator-UI-proven.

Automatic replay, retry/backoff, WorkManager/background execution, server idempotency, duplicate-effect safety, process-death recovery, conflict/freshness handling, offline startup fallback, further migrations, authentication and production-backend behaviour remain outside this proof. A transport failure alone does not establish whether a real remote server applied an operation; this fixture's no-effect outcome does not establish duplicate-effect safety.
