# AGENTS.md

## Mission

Build this repository as a sequence of small, independently verifiable Android reliability beads.

Android Studio is optional tooling. It is **not** part of the build trust boundary. A clean Linux environment must be able to provision the Android SDK, run Gradle, execute tests/lint, and produce build artifacts from the command line.

## Working contract for Codex / agents

1. Read the active bead / GitHub issue before changing code.
2. Stay inside that bead's scope. Do not pre-build later features.
3. Prefer the smallest dependency set that satisfies the current acceptance criteria.
4. Never claim a build, test, runtime behaviour, screenshot, emulator result, database result, HTTP effect, queued mutation, server receipt, or duplicate-effect property unless the corresponding evidence was actually produced.
5. Do not require Android Studio for any acceptance criterion.
6. Do not silently weaken a failing check. Report the failure and its boundary.
7. Keep deterministic fixtures deterministic unless the bead explicitly introduces a real external dependency.
8. Preserve the README claim ceiling: evidence first, claim second.
9. A passing UI screenshot does not prove persistence, queue durability, or remote exactly-once effect; independent database / fixture evidence is required.
10. Distinguish transport failure from an HTTP server response.
11. Stable action identity for Bead 005 must exist before the first mutation request and be reused unchanged for explicit replay of the same logical action.
12. Bind one action identity to one canonical mutation payload. Same identity + different payload must fail closed.
13. Do not clear pending intent or publish confirmation until a matching server receipt is validated and the local receipt transaction commits.
14. Do not add automatic replay, WorkManager, reconnect drains, background retry policy, process-recovery claims, or concurrent conflict handling in Bead 005.
15. Bead 005 proves the deterministic fixture contract only; do not describe it as a production-backend exactly-once guarantee.

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
- **Bead 004 — complete:** one transport-failed mutation becomes one durable pending local intent, remains distinguishable from server confirmation, survives DB close/reopen, and shows no automatic replay in a bounded runtime observation; merged through PR #10.
- **Bead 005 — active:** GitHub Issue #11 introduces retry safety through stable action identity, explicit replay, fixture-side duplicate-effect suppression, and durable receipt matching.

For Bead 005, read Issue #11 before editing. Completion requires more than sending an `Idempotency-Key` header:

- the action id must be created before the first request;
- the same id + same payload must be used on explicit replay;
- the deterministic fixture must durably record one logical effect and one stable receipt before intentionally dropping the first response;
- an explicit second request must return that same receipt without a second logical effect;
- fixture evidence must prove attempts = 2 and logical effects = 1;
- same id + different payload must fail closed;
- mismatched receipts must not clear pending state;
- receipt persistence + pending deletion must be atomic locally;
- a failed local receipt commit must leave the pending intent durable;
- receipt durability must survive a file-backed DB close/reopen boundary;
- every Bead 001–004 gate must remain intact.

Bead 005 evidence must not be described as automatic retry policy, background execution, process-death recovery, production-backend idempotency, concurrent conflict safety, or cross-device deduplication proof.
