# GK Swal v12 — completion pass (18 October 2026)

## Added in v12
- Content reports now submit through the authenticated `submitContentReport` callable instead of direct client Firestore writes.
- Report targets are validated; invalid target types/IDs, self-reporting, missing post/user/comment targets, oversized notes and account-deletion-locked submissions are rejected.
- Added admin-only `adminReviewContentReport` callable to mark reports `reviewed` or `dismissed`, with moderator UID and notes recorded.
- Firestore rules make report creation/modification server-only, allow a reporter to read their own report, and allow users with the trusted `admin: true` custom claim to list/read reports.
- Updated Android repository to use the report callable while preserving the existing report UI.
- Added Firestore Rules tests for private reports and denied client moderation, plus callable integration coverage for submission and admin review.
- Corrected Firestore Rules test configuration to explicitly target the same named database ID used by Firebase Functions.
- Added `FULL-FEATURE-INVENTORY-HINDI.md` with the complete implemented-vs-missing checklist.

## Checks run here
- `node --check functions/index.js` — passed.
- `node --check firestore.test.js` — passed.
- `node --check functions.integration.test.js` — passed.
- XML/JSON/question-bank/config and ZIP integrity checks are to be run in the final packaging pass.

## Not claimed as complete
- Emulator tests were not executed in this environment because Firebase dependencies/emulators are not installed.
- Android compile/unit tests and real-device testing are not verified.
- Functions and Firestore rules are not deployed to the user's live Firebase project.
- 4,800 questions are structurally validated but 0 are marked fact-verified.
- Real UPI payout, server-authoritative quiz scoring, referral attribution/rewards, production AdMob IDs, final legal pages, owner dashboard UI and signed AAB remain outstanding.
