# GK Swal v11 — Completion pass (18 October 2026)

## Added in this pass

- Added an emulator integration test proving reward claims cannot be approved by a normal user.
- Added an emulator integration test proving an admin approval credits the wallet ledger exactly once and repeat approval does not duplicate money.
- Extended account-deletion integration coverage to verify both reward claims and daily claim-limit records are removed.
- Kept reward claims review-only: the app's current quiz scoring is client-side, so user-submitted scores are not treated as verified proof and submission itself never credits money.

## Checks actually run in this environment

- `node --check functions/index.js` — passed.
- `node --check functions.integration.test.js` — passed.
- `node --check firestore.test.js` — passed.
- `bash tools/verify_project.sh` — structural question-bank/config/JS/JSON checks passed; legal publication placeholders remain blockers.
- ZIP archive integrity check is run before distributing this revision.

## Checks not completed

- The integration tests could not execute here because `firebase-admin` and the Firebase Emulator are not installed. An install attempt timed out, so no emulator test is claimed as passing.
- Android compilation/unit tests and a real-device installation were not run because this environment lacks the Android SDK and a complete Gradle wrapper.
- Firebase Functions/Rules have not been deployed. Confirm project ownership, billing, App Check/Play Integrity and database configuration before deployment.
- The 4,800 questions have structural coverage only; 0 are marked fact-verified in the current tracker. Human source-based review is still required.
- Public privacy/account-deletion URLs, developer contact details, production AdMob IDs and signing keystore secrets cannot be invented or supplied from this environment.

## Final steps that require the developer's accounts

1. Upload this ZIP to the intended private GitHub repository and run **Verify and build GK Swal**.
2. Inspect both Android and Firebase emulator jobs. Fix any failures before distributing the APK.
3. Configure the correct Firebase project, deploy reviewed rules/functions, register App Check and test on a real device.
4. Complete and publish legal pages using real contact details, and set production AdMob IDs only after consent testing.
5. Review every question against reliable sources before presenting the bank as verified.
6. Enable signed release only after all legal, AdMob and signing gates pass.
