# Android Reliability Lab

A small Android portfolio project focused on one question:

> Can a user action remain correct when the network disappears, requests are retried, events arrive twice, or the app is interrupted and resumed?

This repository is intentionally built as a sequence of small, independently verifiable reliability beads rather than as a large demo app.

## Status

**Bead 000 — Headless Android environment:** **PASS.** A clean GitHub Actions runner provisions JDK + Android command-line tooling without Android Studio and proves the SDK/toolchain boundary.

**Bead 001 — Thin vertical slice:** **PASS.** The Kotlin/Compose application passes `test`, `lint`, and `assembleDebug`; CI produces a debug APK, boots an Android API 35 emulator, installs and cold-launches the app, observes the live process, captures a screenshot, and matches the deterministic incident UI through the Android UI hierarchy. Proof was produced on commit `93d08feec4f1e04cfe97ee734af261af00157cad` and merged through PR #4.

**Bead 002 — API boundary:** **PASS on proof commit `85f55c712b46b6515cf5828fcacbd0138554d583`; independently verified and merged through PR #6.** The app defaults to an HTTP-backed repository with explicit DTO mapping and `Loading`, `Content`, and `Error` states. Deterministic HTTP 200/500 JVM tests and an API 35 runtime success path are proven by the receipts below.

**Bead 003 — Room persistence boundary:** **PASS on proof commit `4f154d9138c83f863b5fd822a3e27f4960c926d4`; independently verified and merged through [PR #8](https://github.com/safal207/safal207-android-reliability-lab/pull/8).** Successful HTTP data is written to Room before `Content`. File-backed close/reopen, deterministic replacement, HTTP failure without cached fallback, and failed-write rollback passed on API 35. Runtime database rows were independently inspected with host SQLite.

**Bead 004 — Offline mutation queue boundary:** **PASS on proof commit `e3005305d007e515592fdac1bd93e783640245a0`; independently verified and merged through [PR #10](https://github.com/safal207/safal207-android-reliability-lab/pull/10).** One loaded incident's state-change intent is durably queued after a transport failure and visibly marked pending. Online confirmation, pending close/reopen, HTTP error handling, atomic rollback and bounded absence of replay are proven below. [Issue #9](https://github.com/safal207/safal207-android-reliability-lab/issues/9) was closed after independent QA.

**Bead 005 — Retry safety:** **CI PASS on proof commit `69313ed2993025513f160c37d5c76e9acd2be493`; independent QA pending in [PR #12](https://github.com/safal207/safal207-android-reliability-lab/pull/12).** One stable action identity is created before the first PUT and reused by explicit replay. The fixture durably records one effect/receipt before dropping the first response; explicit replay returns that receipt without another effect. Room commits the matching receipt and pending deletion before confirmation. [Issue #11](https://github.com/safal207/safal207-android-reliability-lab/issues/11) remains open and the PR is not merged.

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
- [x] **004 — Offline mutation:** durable pending intent after transport failure; independently verified and merged through [PR #10](https://github.com/safal207/safal207-android-reliability-lab/pull/10)
- [ ] **005 — Retry safety:** stable identity, explicit replay, fixture duplicate-effect suppression and durable matching receipt; CI proof below, independent QA pending in [PR #12](https://github.com/safal207/safal207-android-reliability-lab/pull/12)
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
- Room schema 1→2 adds only `pending_mutations`. An explicit migration test preserves the old incidents. At that proof commit, the original snapshot validator strictly expected version 2 and also required zero pending rows before any mutation; all original integrity/fixture checks remain.
- Mutation bodies are one-shot, with connection retries and redirects disabled. HTTP 400/500 and `503 Retry-After: 0` remain errors without queueing or HTTP retransmission. At that proof commit, the locally generated mutation id never left the local queue; Bead 005 introduces a pre-request identity sent on the wire.

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

All four downloaded artifact hashes and receipt/file bindings were verified, both original and pending SQLite snapshots were queried independently again, and the pending screenshot was inspected. Independent QA subsequently completed; PR #10 was merged and Issue #9 was closed.

### Reproduce the mutation proof

Build and copy both APKs as in the Room proof above, using the same signing key. Use a fresh API 35 emulator: the baseline proof requires an empty pending queue. Then run:

```bash
bash scripts/prove-offline-mutation-runtime.sh
```

The script preserves all prior gates, triggers one UI action, observes the bounded interval and retains raw evidence. It does not clear an existing queue or replay it. Startup still requires a successful HTTP load; pending intents are not drained or reconciled in this bead.

## Bead 005 receipts

Proof commit: `69313ed2993025513f160c37d5c76e9acd2be493`. [Actions run 35195398590](https://github.com/safal207/safal207-android-reliability-lab/actions/runs/35195398590), attempt 1, passed `environment-proof` and `runtime-proof`. The [PR run 35195440317](https://github.com/safal207/safal207-android-reliability-lab/actions/runs/35195440317) also passed. These receipts identify the tested code commit; this README update follows the inspected proof. Independent QA owns acceptance of [Issue #11](https://github.com/safal207/safal207-android-reliability-lab/issues/11) and [PR #12](https://github.com/safal207/safal207-android-reliability-lab/pull/12).

- Fixed `./gradlew --no-daemon test lint assembleDebug` — **PASS**: 3 JVM tests per variant, 6 executions, no failures/errors/skips; lint 0 errors, 9 warnings. Additional `assembleDebugAndroidTest` — **PASS**.
- API 35 instrumentation: **5 Room + 8 offline-mutation + 8 retry-safety tests passed**, with zero failures/skips. The original JVM/Room test sources and strict five/eight-test result validators remain intact. Bead 004 scenarios retain their assertions; successful 204/no-key protocol expectations are upgraded to matching receipt/stable-header assertions. HTTP 400/500/503 error, one-shot HTTP bodies, rollback and bounded no-replay gates remain.
- The actual Python runtime fixture has **3 passing HTTP tests**, including durable receipt reuse after fixture reopen, payload conflict, and preservation of Bead 004's pre-effect disconnect. This fixture reopen test is not Android process-recovery evidence.
- Room schema 2→3 adds only `mutation_receipts`. Migration tests preserve v1 incidents through 1→2→3 and v2 pending rows through 2→3. The historic `pending_mutations.mutationId` column now stores the same `actionId` sent in `Idempotency-Key`; old rows are preserved without startup replay.

Test class: `com.safal207.androidreliabilitylab.data.RetrySafetyTest`.

| Vector | Passing test |
|---|---|
| A: identity exists before first PUT; one effect and durable matching receipt | `vectorAOnlineSuccessStoresMatchingReceipt` |
| B: effect committed, response lost; same identity remains pending | `vectorBAppliedThenLostResponseKeepsSamePendingIdentity` |
| C: one manual replay; same identity/payload/receipt, two attempts, one effect | `vectorCExplicitReplayReturnsSameReceiptWithoutSecondEffect` |
| D: same identity, different payload; HTTP 409 without local/server corruption | `vectorDSameIdentityDifferentPayloadIs409WithoutCorruption` |
| E: wrong action, incident or target receipt; pending retained, no confirmation | `vectorEMismatchedReceiptNeverClearsPendingOrConfirms` |
| F: abort after receipt INSERT and pending DELETE; both roll back, pending survives reopen | `vectorFFailedReceiptTransactionRollsBackAndRetainsDurablePending` |
| G: matching receipt and no pending after file-backed close/fresh reopen | `vectorGReceiptSurvivesFileBackedCloseAndFreshReopen` |
| Additive v2→v3 migration without replay | `versionTwoMigrationPreservesPendingWithoutReplay` |

| Artifact | ID | Archive SHA-256 |
|---|---|---|
| [Debug APK](https://github.com/safal207/safal207-android-reliability-lab/actions/runs/35195398590/artifacts/10486021619) | `10486021619` | `8a08efb688406e273abc74310f44d02d1e93b0c0bf50260b4183db1c6ff69c5a` |
| [Instrumentation APK](https://github.com/safal207/safal207-android-reliability-lab/actions/runs/35195398590/artifacts/10485867337) | `10485867337` | `6d3feb284f04b3d739f0afecb7a7c2962a310956c395370ad670df5baffd0250` |
| [Runtime proof](https://github.com/safal207/safal207-android-reliability-lab/actions/runs/35195398590/artifacts/10485019898) | `10485019898` | `61b924bcba8e1d63a942bae2cd2e1a0b7d9bd36826ba9b3e90b230507a673bb2` |
| [JVM/lint reports](https://github.com/safal207/safal207-android-reliability-lab/actions/runs/35195398590/artifacts/10486046309) | `10486046309` | `9e54f42835cb4374c6a0547fc1f0df1818a4e6d8caac79caceb819531def628e` |

APK file SHA-256: `7315d0c3027df56818a429643849f324a9053b890fc41cf08247e61b9c9c311d`. Test APK file SHA-256: `da74d7e9c9317fd2d6333ea31b7ab6db51242614255963b8d2f5e5c6fa5565fb`. Downloaded archives matched all four GitHub digests. Artifacts currently expire on 2026-12-16.

### Explicit replay runtime evidence

The archive retains all prior HTTP/Room and `offline-mutation/` evidence. New evidence is under `retry-safety/`. After all previous gates pass, the script explicitly clears the disposable emulator app's data to isolate this new scenario; the earlier snapshots and receipts remain unchanged. This is test setup, not a production reset or recovery path.

1. Cold launch loads the same deterministic HTTP fixture. App PID `4585` stays unchanged through Pending and confirmation.
2. One visible **Resolve incident** tap sends `PUT /incidents/INC-API-001/status`, body `{"status":"RESOLVED"}`, with `Idempotency-Key: 9a28aa91-76e9-4567-a8a7-3ce51bf0b507` at `2026-09-17T07:44:12.046580+00:00`.
3. The fixture commits incident state, one effect, its receipt and the attempt to an independent SQLite ledger (`synchronous=FULL`, rollback journal). It fsyncs the request log, then disconnects before sending the response.
4. The UI shows **Pending synchronization** and **Retry pending change**. Independent host SQLite inspection finds pending=1, receipts=0, the same action identity and expected incident fields. Two consecutive DB/WAL/SHM snapshots are byte-identical while the app remains alive and idle; no force-stop/relaunch occurs at this boundary.
5. With the fixture ready to return its stored receipt, no replay occurs during **5.052817450 seconds**, from `2026-09-17T07:44:14.279348+00:00` to `2026-09-17T07:44:19.332142+00:00`. Attempts remain 1 and effects remain 1.
6. One visible **Retry pending change** tap sends the same identity and payload at `2026-09-17T07:44:20.171138+00:00`. The fixture returns HTTP 200 and the original receipt. Independent ledger rows prove **attempts=2, logical effects=1, same action=true, same receipt=true**.
7. UI becomes **Resolved on server** after the local receipt transaction commits. Only then is the app force-stopped for a quiescent final snapshot: pending=0, exactly one matching receipt, incident `INC-API-001` RESOLVED, other incidents unchanged. SQLite integrity is `ok`, schema version 3, Room identity `4221b15cdadae644c12bf27e13268675`.

| Stable receipt field | Value |
|---|---|
| actionId | `9a28aa91-76e9-4567-a8a7-3ce51bf0b507` |
| incidentId / targetStatus | `INC-API-001` / `RESOLVED` |
| effectId | `effect-1` |
| receiptVersion / effectSequence | `1` / `1` |
| Canonical payload SHA-256 | `ee4a9a505754294e4808c7af16c1ff26a6818a2ef107ff1c9c98b41e2caa9853` |
| Canonical receipt SHA-256 | `7d32f5b4a80ce7a26122204a596c9ae3647047976d871cede8d4cc2fe87ffc0b` |

Canonical hashes use UTF-8 JSON with sorted keys and compact separators. The first ledger receipt, replay response receipt and durable local receipt match in every field. `fixture-ledger.sqlite` contains independently inspectable `attempts`, `effects` and `incident_state` tables; effect count is not inferred from the local UI or pending rows.

| Evidence under `retry-safety/` | SHA-256 |
|---|---|
| `first-request.jsonl` | `d144b9e124ea311680eb0c9f55acc94347f0e735922a468e3b2d50a566c2a47f` |
| `mutation-requests.jsonl` | `52f4d9dd473964536bf2670c5665312f555e5d691cecb50fff54e96f77317a1f` |
| `pending-fixture-ledger.sqlite` | `6df5ada8c9a855b0c370dcd6bb334b8097891373a273c3be49e1bc390e15c375` |
| `fixture-ledger.sqlite` | `660d421ea5633e7f6b01548b528e7aa47ef76e63e9aee781e2a617af818d6546` |
| `fixture-rows.json` | `ea57483696aca3296e94ca0e660a7893436033b2c044398a60b5da26e0ecb43f` |
| `pending-database.tar` | `c23f9b3444264ee0df9db080db985be6d96dae4bb493fb73c8c1264a07306bec` |
| `confirmed-database.tar` | `082db5fc877a95b8266ac2bcf7a4b195d5f8cdcd30fa89d7cab05b7ffc0742ac` |
| `pending-rows.json` | `50a2a72be4267060183458e1848a9270787230384cc6dd0ceab28e9bee396722` |
| `confirmed-rows.json` | `25f4bb6720b792697a41a554b7e026a67447e58acbffea3a5e94f81a81239173` |
| `pending-incident.png` | `055df2064c6b30933a906d4a51ef92c3ef4fd2299ed2a0ff69de2ac770b7e022` |
| `pending-window.xml` | `8907ce0f11b4bbcf803f498bc8f416edd8d55d93a2a2cc121de38e110fce1d98` |
| `confirmed-incident.png` | `a00f211c2a3c605c02ca8a6713d6d71be92c81fcb7e8757a02f772227fadc707` |
| `confirmed-window.xml` | `cca3e09c45f1be3a84bcec07fdb6b39b4562bfbc98912445c5f4966c3671c207` |
| `retry-instrumentation.txt` | `1e295c5a01d20e8b7c9d7997d5461d8cd0e60ab4afa7779c50e75bc84991ebfa` |

`retry-receipt.json` binds the proof SHA/run/attempt, APK, requests, receipt identity/hash, before/after snapshots, extracted rows, screenshots/hierarchies and all prior gate receipts. `pending-proof.json` includes raw DB/WAL/SHM hashes and the live snapshot boundary. `retry-instrumentation-results.json` binds the eight named results to the test APK and raw runner output. Downloaded file bindings were checked, raw Room snapshots and the fixture ledger were independently queried again with host SQLite, and both screenshots were inspected.

### Reproduce the retry-safety proof

Build and copy both APKs as in the Room proof, using the same signing key. Start a fresh, disposable API 35 emulator (the preserved initial gates require an empty pending queue), then run:

```bash
python3 scripts/test-mutation-fixture.py
bash scripts/prove-retry-safety-runtime.sh
```

This runs every Bead 001–004 runtime gate and their 13 instrumentation tests before the isolated Bead 005 scenario and eight new tests. Missing/skipped/failed tests, a third mutation request, a second effect, changed identity/payload/receipt, mismatched database rows or failed earlier checks fail CI. The app has no scheduler or startup replay path; each replay requires the visible button or an explicit test call.

## Claim ceiling

The repository claims evidence only on the cited proof commits. Beads 001–003 establish the existing build, HTTP, UI and persistence boundaries. Bead 004 establishes durable pending intent after its deterministic pre-effect transport failure, atomic queue rollback, database close/reopen and bounded absence of replay. Those historical receipts retain their original scope.

Bead 005 establishes one stable action identity/payload, explicit replay after an applied effect and lost response, duplicate-effect suppression by the deterministic fixture, and a matching durable local receipt before confirmation. It also checks payload conflict, mismatched receipts, receipt-transaction rollback and file-backed receipt close/reopen. HTTP/receipt error states are instrumented; their rendered error screens are not emulator-UI-proven. Independent QA has not yet accepted Bead 005.

This does not establish automatic retry/backoff, WorkManager/background execution, reconnect drains, queue-wide replay, Android process-death/startup recovery, concurrent conflict/version handling, cross-device deduplication, offline startup fallback, authentication or production-backend idempotency/exactly-once guarantees. Database reopen is not process recovery, and a bounded no-replay observation is not an unlimited guarantee.
