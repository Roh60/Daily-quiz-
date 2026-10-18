# GK Swal — next-step audit (18 October 2026)

## Changes in this revision
- Corrected stale README/report text that still described the app as having only ~1,300 questions and only static Current Affairs links.
- Corrected the documented GitHub Actions artifact name to `gk-swal-release-aab`.
- Added `tools/audit_release_readiness.py`, an informational check for privacy/deletion-page placeholders, artifact-name consistency, wrapper absence, and the App Check dependency alias.
- Added that audit to the Android CI job and added job timeouts to make a hung emulator/build easier to diagnose.

## Local checks performed
- `python tools/audit_question_bank.py`: passed — 4,800 records, 12 categories × 400, unique IDs, option structure and answer-index checks.
- `python tools/validate_project_config.py`: passed — Android package registered in `google-services.json`, database ID aligned, INTERNET permission present, Functions Node runtime set to 20.
- `node --check functions/index.js`, `node --check firestore.test.js`, `node --check functions.integration.test.js`: passed.
- ZIP integrity: passed (`ZipFile.testzip()` returned no error).

## Not verified here
- Android Gradle compilation/unit tests; no Gradle executable or Android SDK is installed in this editing environment.
- Firebase emulator tests/deployment, production App Check attestation, AdMob/UMP, public legal-page hosting, signed AAB, and device installation.
- Question factual accuracy. Structural tests cannot verify answers or explanations.

The release-readiness audit is informational in default mode so an internal-test build is not blocked solely by legal placeholders. Before public release, resolve every blocker and confirm the actual app behavior and Play Console disclosures.

## Additional preflight fixes
- Added an explicit Node.js 20 setup step to the Android verification job, so JavaScript syntax checks do not depend on runner-preinstalled Node versions.
- Updated the release checklist to distinguish the current 4,800-question target from any future 10,000-question expansion; factual review remains required.
- Strengthened legal-page placeholder detection to catch common `YOUR_*`, `TODO`, and example-domain placeholders.
- Added `tools/verify_project.sh` as a single, non-destructive local preflight command. It runs question/config audits, release blocker reporting, JavaScript syntax checks, and JSON parsing; it only attempts Gradle tests when Gradle or a complete wrapper is available.
