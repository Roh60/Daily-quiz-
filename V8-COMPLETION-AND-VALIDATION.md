# GK Swal v8 — Completion work and validation

## Changes in this revision
- Corrected the in-app Privacy Information dialog: it no longer incorrectly says ads are not configured; it explains the current UMP consent gate and the need to match disclosures to production configuration.
- Added optional public HTTPS links for the Privacy Policy and account-deletion webpage in the profile privacy dialog. They are injected through `PUBLIC_PRIVACY_POLICY_URL` and `PUBLIC_ACCOUNT_DELETION_URL`; if unset, the app clearly tells users the public page is not configured.
- Added a signed-release gate requiring both public HTTPS legal URLs, alongside the existing legal placeholder, production AdMob ID, and signing checks.
- Added a GitHub Pages workflow that refuses to publish the legal pages while placeholders/draft warnings remain. After developer details and final data practices are completed, it can publish the Privacy Policy and account-deletion page.
- Replaced 15 duplicate question stems in the Facts category with distinct, stable GK questions. Added exact normalized question-stem duplicate detection to the question-bank audit. The replacement questions remain marked unverified until a human checks reliable sources.
- Updated the privacy policy template with a consent choices disclosure and added setup instructions.

## Checks run in this environment
- `python3 tools/audit_question_bank.py`: PASS — 4,800 questions, 400 per each of 12 categories, unique IDs, no exact duplicate normalized question stems, non-empty four-option structure and answer index bounds.
- `python3 tools/audit_question_factcheck_tracker.py`: PASS — tracker covers all 4,800 questions; 0 marked verified.
- `python3 tools/validate_project_config.py`: PASS — Android package, Firebase client and database configuration checks.
- `node --check functions/index.js`, `firestore.test.js`, `functions.integration.test.js`: PASS.
- JSON parsing for Firebase config, Functions package and google-services config: PASS.
- Android XML parsing: PASS.
- YAML parsing for both GitHub Actions workflows: PASS.
- `python3 tools/audit_release_readiness.py`: expected BLOCKERS — legal page templates still contain developer contact placeholders.

## Still not completed from this environment
- Android Gradle compile, Robolectric/unit tests, and APK install tests: Gradle/Android SDK are not installed here and the distribution host is not reachable from this environment. Use the hosted Android workflow.
- Firebase Emulator tests and production deployment: not run; no deployed Firebase project access.
- Public legal page publishing: deliberately blocked until real developer/business name, support email, and accurate final disclosures are entered.
- Production AdMob IDs, live UMP consent tests, signed AAB: require the developer's own AdMob account, GitHub repository variables/secrets and upload keystore.
- Factual review of all 4,800 questions: not complete. All tracker rows remain `UNVERIFIED`; structural checks do not prove factual accuracy.

This revision does not claim Play Store readiness or successful Android compilation.
