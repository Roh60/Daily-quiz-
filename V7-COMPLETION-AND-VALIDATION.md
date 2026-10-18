# GK Swal — v7 completion pass (18 October 2026)

## Changes made in this pass

- Expanded `question-bank-fact-check-tracker.csv` from 3,483 supplemental questions to all 4,800 shipped question IDs. Existing review metadata was preserved where present. All entries without documented verification are explicitly marked `UNVERIFIED`.
- Added `tools/audit_question_factcheck_tracker.py`, which ensures the tracker covers every shipped question and refuses a verified status without source URL, reviewer, and verification date. This checks review bookkeeping, not truth.
- Added the tracker coverage audit to local preflight and GitHub Actions before Android compilation.
- Hardened signed-release checks to reject malformed or Google sample AdMob IDs and require signing secrets in the release audit step.
- Corrected README and launch-plan statements that contradicted the AdMob/UMP source integration already present in v6.

## Checks run in this environment

- `python3 tools/audit_question_bank.py`: PASS — 4,800 structurally valid records, 400 in each of 12 categories.
- `python3 tools/audit_question_factcheck_tracker.py`: PASS — all 4,800 records covered; no record is represented as verified by this tool.
- `python3 tools/validate_project_config.py`: PASS — Android package is registered in the bundled Firebase configuration and configured database matches. Owner must still confirm it is their project.
- JavaScript syntax (`node --check` on Functions and test files): PASS.
- JSON parsing (`firebase.json`, `functions/package.json`, `app/google-services.json`): PASS.
- `bash tools/verify_project.sh`: run after all edits; Android compile is skipped in this environment because Gradle/Android SDK are not installed and outbound network access is unavailable.

## Still owner-dependent / not truthfully markable as complete

1. Android compile, JVM unit tests, Firebase emulator tests, and physical-device consent/ad checks must pass on GitHub Actions/device.
2. Firebase Functions and Firestore Rules must be deployed to the owner’s project; App Check Play Integrity must be registered and tested.
3. Developer/legal name and support email are unknown, so legal-page placeholders cannot be truthfully filled. Publish the final pages at public HTTPS URLs and confirm the deletion workflow.
4. Production AdMob IDs and publisher account are owner-specific. The release audit now validates their format and rejects sample IDs, but cannot verify account ownership.
5. Signed AAB requires the owner’s private upload keystore and GitHub secrets. Do not send these secrets in chat or commit them.
6. All 4,800 question records remain `UNVERIFIED` unless a human reviewer adds a trustworthy source URL, reviewer identity, and verification date. Current-affairs records need date-specific source checks.

No production deployment, factual verification, successful Android build, or Play Store readiness is claimed by this pass.
