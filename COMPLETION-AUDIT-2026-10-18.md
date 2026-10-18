# GK Swal completion audit — 18 October 2026

## Changes in this revision
- Updated visible Android app branding from “Daily Quiz” to “GK Swal”, including the app label, visible copy, and the matching app-name unit-test expectation.
- Added a manual “Refresh headlines” control to the official PIB RSS Current Affairs screen, with loading/disabled state and retry after a feed error.
- Kept existing question data, social features, Firebase functions/rules, and build configuration in place.

## Static checks performed
- ZIP archive integrity check passed.
- `node --check` passed for `functions/index.js`, `firestore.test.js`, and `functions.integration.test.js`.
- Source-bank inventory: ExpandedTopicQuestionBank 1,200; FactsQuestionBank 100; CategorySupplementQuestionBank 3,483; initial starter questions 17. Intended fresh-install total: 4,800.
- Question-bank CSVs were checked for readable CSV structure. The fact-check tracker is a tracking sheet; empty verification fields mean questions are NOT fact-checked.

## Not verified / requires owner access
- Android Gradle compile and unit tests were not run here (no installed Gradle executable / Android SDK build environment in this workspace).
- Firebase project ownership/configuration, Firestore rules deployment, callable Cloud Functions deployment, emulator integration tests, App Check enforcement, and account deletion were not verified against the live project.
- No production AdMob or UMP consent integration is claimed in this build.
- Privacy and account-deletion HTML are templates with placeholders. They must not be published as final until developer identity/contact, actual data practices, and deletion behavior are confirmed. Google Play requires an accessible privacy policy and, for apps with account creation, both in-app and external account-deletion routes.
- No signed AAB was generated or device-tested.
- The question bank is a draft practice bank, not a professionally fact-checked dataset. Review all answers/explanations, with special attention to current-affairs dates and sources, before public release.

## Release gate
Do not submit to Google Play until a clean hosted Gradle build, Firebase rules/function tests, signed AAB installation test, privacy/deletion URL verification, AdMob/consent decision, and Play Console Data safety declarations have been completed.


## Follow-up improvement: repeatable question-bank audit

Added `tools/audit_question_bank.py` and wired it into the GitHub Actions Android verification job. Local result on 2026-10-18: 4,800 records parsed, 4,800 unique non-empty IDs, exactly 400 per target category, all four options distinct/non-empty, valid answer indexes, and explanations present. This does not prove factual correctness.

## Follow-up CI hardening

- Added `tools/validate_project_config.py` to check Android application ID registration, Firebase database ID consistency, Android internet permission, and Functions Node runtime alignment.
- Updated GitHub Actions to run that validator, check JavaScript syntax for Cloud Functions and emulator tests, and parse critical JSON configuration before Android compilation.
- These are static checks only. They do not authenticate to Firebase, deploy functions, verify ownership of the Firebase project, or produce a signed AAB.
