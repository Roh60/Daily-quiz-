# GK Swal — Android project

Native Android app built with Kotlin, Jetpack Compose, Room, Firebase Authentication and Cloud Firestore.

## Current project status

- Existing areas: Library, Feed & Polls, Bookmarks, Profile, and Current Affairs source directory.
- Added a mixed-topic Daily GK Challenge card that presents up to 5 questions per device-local calendar day and saves its result in study history. This is not an account-wide server-enforced limit across all practice modes/devices.
- Added a local study-streak calculation and phone share-sheet invite card in Profile to support organic discovery. Referral attribution/rewards are not implemented; the Play Store invite URL becomes useful only after the app is published.
- Added an in-app Current Affairs headline list loaded from the official PIB press-release RSS feed, including source-provided publication dates and links to original releases. It loads when the screen opens and shows an error/fallback state offline. It is not an independently fact-checked digest and does not guarantee every item has a publication date.
- Firestore rules prevent normal users from assigning themselves privileged roles, restrict question-bank writes to server-claimed admins, and deny direct client writes to likes, poll votes, comments and aggregate counters. The app now calls server-side transactional functions for these social actions; deploy the functions before testing social writes.
- Added a callable Firebase Function for account deletion. The function must be deployed and tested before the in-app delete action is production-ready. Account deletion also removes the user’s likes, votes and comments on other users’ posts.
- `legal/privacy-policy-template.html` and `legal/delete-account.html` are templates; replace all placeholders and publish them at stable public URLs before Play submission.
- The bundled local question bank has 4,800 structurally audited draft questions (12 categories × 400); factual verification is still required and the bank is not represented as 10,000 verified questions.
- AdMob and Google UMP SDK integration is present in source. Debug builds use Google sample test IDs; production ads remain disabled until real publisher IDs are configured and consent behaviour is verified on-device. Do not declare ads active in Play Console until the final production configuration and data disclosures have been reviewed.

## Firebase setup

1. Confirm that `app/google-services.json` belongs to the Firebase project you own and that Google sign-in is configured for the Android package.
2. Deploy the Firestore rules to the exact named database in `firebase.json`:
   `firebase deploy --only firestore`
3. Account deletion uses a Firebase callable function in `functions/index.js`. Install Firebase CLI, run `firebase login`, then `cd functions && npm install` and from the project root run `firebase deploy --only functions`.
4. Cloud Functions deployment generally requires the Firebase/Google Cloud project to be on the Blaze billing plan. Review budgets and billing alerts first. Do not deploy without understanding this requirement.
5. The function uses a server-assigned Firebase Auth custom claim (`admin: true`) to allow question-bank publishing. Never let the Android client assign this claim. Use a trusted Admin SDK environment for any future admin account setup.

## Fixes included in this revision

- The launcher label and main screen branding use `GK Swal`.
- Removed Room's destructive migration fallback. If a future schema change has no migration, startup now fails visibly rather than silently wiping local quiz history, bookmarks, and progress. Add and test an explicit migration before changing the database version.
- Added a question-bank integrity unit test covering unique IDs, non-empty question/options/explanations, and valid answer indexes. This is a structural check only; it does not certify factual accuracy or eliminate repetitive questions.

## Android build and signed AAB

This ZIP does not contain the Gradle wrapper scripts/JAR and the build has not been verified in this environment. The Current Affairs screen was updated to load the official PIB RSS feed, but the Android build still needs to be run on a configured Gradle/Android environment. The GitHub Actions workflow installs Gradle on its hosted runner, runs Android unit/build checks and Firestore Rules emulator tests, and only then attempts a signed AAB. A successful workflow run is still required before treating the build as verified.

Before a release:

1. Decide and freeze the permanent `applicationId` before the first Play upload.
2. Increment `versionCode` for each new upload.
3. Create and safely back up an upload keystore. Never place the keystore or passwords in source control or share them in a public ZIP.
4. Provide `KEYSTORE_PATH`, `STORE_PASSWORD` and `KEY_PASSWORD` to the release build environment; the current release signing config reads these from environment variables.
5. Build a signed Android App Bundle (`bundleRelease`) and verify it on a device/internal test track before production.

See `legal/play-release-checklist.md` for remaining release gates. Social like/vote/comment writes require deployment of the callable functions before they can work against Firestore Rules.

## Phone-only AAB build using GitHub Actions

A workflow is included at `.github/workflows/android-release.yml`. It runs checks on a hosted Linux runner, so a local Android SDK is not required on the phone. The Android verification job uploads a `gk-swal-debug-apk` artifact for device testing. The signed AAB job is intentionally disabled by default and runs only after the repository variable `ENABLE_SIGNED_RELEASE=true` is set; it also blocks if public legal pages still contain placeholders. Upload the project to GitHub before using Actions.

1. In Termux, from the project root, create an upload key and keep it backed up privately:
   `keytool -genkeypair -v -keystore my-upload-key.jks -keyalg RSA -keysize 2048 -validity 10000 -alias upload`
2. Encode the keystore without sharing the file itself: `base64 -w 0 my-upload-key.jks` (copy the output privately; do not paste it into chat or commit it).
3. In GitHub repository **Settings → Secrets and variables → Actions**, add secrets named `UPLOAD_KEYSTORE_BASE64`, `STORE_PASSWORD`, and `KEY_PASSWORD`. Use the exact passwords from key creation. The key alias must remain `upload` unless the Gradle signing config and workflow are updated together.
4. Upload/push the project to a private GitHub repository. First run **Actions → Verify and build GK Swal → Run workflow** and download `gk-swal-debug-apk` for phone testing.
5. For a signed AAB only after legal pages are complete, add repository variable `ENABLE_SIGNED_RELEASE` with value `true`, then run the workflow again. If the job succeeds, download `gk-swal-release-aab`. If it fails, inspect the first error; never share signing secrets in chat.
6. Back up the upload keystore and passwords safely. Losing the key can complicate future updates. Never commit the keystore or its passwords.

The workflow has not been run from this environment; the first hosted build may reveal Android SDK or Gradle compatibility issues that must be fixed before release.


## New starter topics
The Library includes Sports, Entertainment, Kids, and Currency topic filters, with 1,200 topic questions (300 per topic) plus 100 Facts questions and the initial GK starter set. Existing installs receive missing questions on startup without intentionally clearing quiz history or bookmarks. Content is independently drafted and marked for editorial review; the question bank contains repetitive patterns and has not been fully fact-checked, so do not claim 10,000 verified questions or rely on it as an authoritative exam source. Currency currently covers educational currency facts; live exchange rates/conversion are not included.

## Server-managed social actions (important)

Deploy `functions/` before testing likes, polls, comments, follow/unfollow, post deletion, or account deletion. These actions are handled by authenticated callable functions: `togglePostLike`, `voteOnPoll`, `addPostComment`, `toggleFollowAuthor`, `deleteMyPost`, and `deleteMyAccount`. Firestore Rules intentionally deny direct client writes to these interaction and follow records. Review the deployment and launch limitations in `COMPLETE-FIXES-REPORT.md`.


## App Check and deletion-lock hardening

- App Check is initialized before Firebase clients. Debug builds use the Debug provider; release builds use Play Integrity. In Firebase Console, register this Android app for App Check, configure Play Integrity, and register the debug token printed by a debug build. Do not enable production enforcement until the real app has been registered and verified.
- Deletion lock rules prevent direct client writes to profiles, quiz history, posts and reports while an account deletion job exists. The callable uses a lease and resumes from the persisted stage.
- Production release remains gated on successful GitHub Actions runs, a Firebase Emulator pass, real Firebase project/database verification, and signed AAB/device tests.


## 4,800-question category target (2026-10-18)

The local question bank now includes a supplemental category bank intended to bring a fresh installation to 400 questions in each of 12 categories: History, Indian Polity, Geography, General Science, Indian Economy, Static GK, Sports, Entertainment, Kids, Currency, Current Affairs, and Facts. Questions are four-option MCQs with an answer index and explanation. Editorial fact-checking is still required; current-affairs items must be date/source checked before release. Android build and Firebase emulator tests were not run in this environment.


### Question bank review file

`question-bank-counts.json` lists the expected category counts. `question-bank-supplement-review.csv` contains the 3,483 supplemental rows added on top of the 1,300 earlier topic-bank rows and 17 starter questions, resulting in 4,800 records total (400 per category) on a fresh install. These are draft practice questions and require human fact-checking; the Current Affairs category particularly needs dated source review.

### Question-bank quality status
The bundled bank is structurally organized as 12 categories × 400 questions (4,800 total). Supplemental answer choices have been reworked to use same-category distractors where possible, and a unit test checks distinct options. This does not establish that every answer is factually verified. Before public release, editorially verify each question and explanation, and attach dates plus official sources to current-affairs items. See `QUESTION-BANK-QUALITY-REPORT.md`.


## Automated question-bank structure audit

Run `python3 tools/audit_question_bank.py` from the project root. It parses the Kotlin `QuestionEntity` seed data and checks the 4,800-record / 12-category target, duplicate or missing IDs, four distinct non-empty options, answer indexes, and explanations. The GitHub Actions Android verification job runs this audit before Gradle. This is a structural check only; it does not verify facts, current-affairs dates, or whether every distractor is pedagogically appropriate.

## Extra CI configuration guard

The workflow now runs `python tools/validate_project_config.py` before Gradle. This checks that the Android `applicationId` is registered in `google-services.json`, the Cloud Functions database ID matches a database declared in `firebase.json`, the manifest has internet access, and Functions runtime configuration matches CI. It also runs Node syntax checks for Functions and emulator tests, plus parses key JSON files. These checks catch configuration mistakes early; they do not prove that the Firebase project belongs to you or that production services are deployed.

### Local preflight

Run `bash tools/verify_project.sh` from the project root to check question-bank structure, Android/Firebase configuration, release blockers, Cloud Functions JavaScript syntax, and important JSON files. It is non-destructive. Android compilation runs only if Gradle or a complete Gradle wrapper is available; otherwise use the included GitHub Actions workflow.


## Remaining owner-only setup

See `REMAINING-WORK-HINDI.md` for exact remaining steps. The privacy and deletion pages intentionally retain placeholders until the owner supplies real developer contact details. AdMob and Google UMP source integration is present; production IDs, public legal URLs, on-device consent verification and accurate Play Console disclosures remain required before ads/release are enabled.


### Ads and consent status
The project now includes AdMob and Google UMP SDK dependencies. UMP is requested at launch, and the banner is shown only when consent allows ads and a banner ad-unit ID is configured. Debug builds use Google's sample test IDs only. For a signed release, configure GitHub Actions secrets `ADMOB_APP_ID` and `ADMOB_BANNER_AD_UNIT_ID` from your own AdMob account; never use test IDs for monetization. The release audit blocks when these IDs or legal-page publication details are missing.


## Public legal pages and in-app links
The profile privacy dialog opens the configured public Privacy Policy and account-deletion webpage when valid HTTPS URLs are provided. Set `PUBLIC_PRIVACY_POLICY_URL` and `PUBLIC_ACCOUNT_DELETION_URL` in the build environment. The `Publish GK Swal legal pages` GitHub Actions workflow can publish the HTML after real developer contact details and policy content are complete. Configure repository variables with the resulting HTTPS URLs. The workflow deliberately refuses to publish pages with unfinished placeholders.


## v8 completion update
See `V8-COMPLETION-AND-VALIDATION.md` for the exact changes and checks. The structural question audit now rejects duplicate normalized question stems as well as duplicate IDs. It still does not verify facts.


## Wallet and profile features (v9)
- Profile editing supports display name and bio; target exam preferences persist to Firestore.
- Profile contains an earning-wallet card, server-authoritative balance, transaction ledger, and UPI withdrawal request UI.
- Withdrawals are ₹100–₹10,000 per request and reserve the balance transactionally.
- Wallet credits are admin-only; user clients cannot change balances or transaction history.
- UPI transfer is not automated in this release: a trusted operator must complete the transfer externally and provide a payment reference when marking the request paid. Rejected requests are refunded by the backend.
- Deploy Firebase Functions and Firestore Rules and pass emulator tests before production.
## Latest revision

See `V11-COMPLETION-AND-VALIDATION.md` for the latest test-coverage additions and the exact items that still require Firebase/GitHub/developer-account access. Reward claims are review-only; no client-reported quiz score automatically creates money.

## Full feature inventory (v12)
See [`FULL-FEATURE-INVENTORY-HINDI.md`](FULL-FEATURE-INVENTORY-HINDI.md) for the full list of user app features, Firebase backend functions, tests, and features that still require deployment, device verification, account setup, or implementation. Do not treat source presence as proof of a successful live deployment.

## v13 additions
See `V13-IMPLEMENTATION-AND-SETUP-HINDI.md` for setup and limitations. New source includes server-verified quiz reward sessions, referral rewards, indicative currency conversion, RazorpayX payout submission/status sync, a standalone owner dashboard, and a local question fact-check workbench. None of the external services are considered live until configured and tested in your Firebase/AdMob/RazorpayX accounts.


## Global Currency GK Quiz (v15)

- Android asset: `app/src/main/assets/global_currency_gk_questions.json` contains 2,003 four-option questions on country/currency names and ISO codes.
- Dedicated CSV and unverified fact-check tracker are included at project root.
- The $1 = ₹96 and €1 = ₹108 items are explicitly labeled user-supplied practice examples, not current rates.
- Question-bank structure is validated; factual verification of every row is still pending.


## 10,000 quiz entries (v16)

The app source now includes 7,997 database question records plus 2,003 Global Currency GK asset questions. The 3,197 expansion entries cover the 11 non-currency categories and are alternate-wording revision variants of existing items; do not represent them as 3,197 independently new facts. Review `expansion-question-review.csv` before release.

## Daily activity policy (v17)
- Daily Challenge presents up to 5 questions per day. The question selection is stored locally on the device and rotates by date.
- Community poll creation has no daily post-count limit in the current Firestore rules or app flow. Each poll still requires a valid question and 2–4 options, and remains subject to account moderation/deletion controls.
- Note: this is a device-local Daily Challenge selection, not a server-enforced account-wide cap across every offline practice quiz or multiple devices.


## Current v17 status and GitHub publication

See `APP-STATUS-AND-FULL-FEATURE-INVENTORY-V17-HINDI.md` for the full implemented-vs-pending inventory, and `GITHUB-PUBLISH-CHECKLIST-HINDI.md` before uploading. The current count is 10,000 quiz entries (7,997 local database records + 2,003 global-currency asset questions); 3,197 expansion entries are revision variants, not independently new verified facts. Poll creation has no daily post-count limit in the current flow. Daily Challenge is up to 5 questions per device-local day. Android build, Firebase deployment, real-device testing, fact-checking, production ads, payouts, legal pages and signed release remain unverified/incomplete as described in the inventory.
