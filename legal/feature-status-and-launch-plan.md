# GK Swal — Feature status and launch plan

## Added in this update
- Daily GK Challenge: launches up to 10 mixed questions from the locally available question bank and records the session as `Daily Challenge`.
- Study streak: derives consecutive study days from local quiz history. It does not upload a streak or claim a server-verified ranking.
- Invite friends: opens Android share sheet with the current package's Play Store URL. Referral tracking, invite attribution and rewards are not enabled.
- Removed conflicting unused `MenuBook` imports that could block Kotlin compilation.

## Still required before public release
1. Run a clean Gradle `bundleRelease` build on GitHub Actions and fix any compiler/test failures.
2. Deploy Firestore rules to the correct named database and run emulator tests against all user, post, comment, like, poll, follow and report operations.
3. Deploy and test the new callable functions `togglePostLike`, `voteOnPoll`, and `addPostComment`. Client writes to likes, votes, comments, and aggregate counters are now denied by Firestore Rules; social interactions will not work until these functions are deployed to the configured Firebase project.
4. Deploy and test the `deleteMyAccount` Cloud Function; it now also cleans up the account’s likes, votes, and comments across other users’ posts. Verify deletion and sign-out on device. Cloud Functions may require the Blaze billing plan.
5. Current Affairs now loads the official PIB press-release RSS feed in-app with source-provided dates and original links. Test it on real devices/network, confirm the feed response format, and add a clear refresh/retry UX if needed. It is not an independently fact-checked exam digest.
6. The local question bank has 4,800 structural records (12 categories × 400). Fact-checking is not complete; do not call all questions verified until each has a reliable source and reviewer approval.
7. AdMob and Google UMP SDK/source integration is present. Production publisher/app and banner IDs are still owner-provided; verify consent/no-consent/privacy-options flows on a physical device before release.
8. Replace and host privacy-policy and account-deletion templates at public URLs. Complete Data safety, content rating, target audience, app access and store listing declarations accurately.
9. Verify package ID, Google sign-in SHA fingerprints, version code, upload key backup and Play App Signing configuration.
10. Run internal testing on physical Android devices, including fresh install, login, offline quiz, sharing, social post/poll, account deletion and upgrade from previous build.

## Growth feature rationale
Daily challenge encourages repeat sessions; study streak makes progress visible; friend sharing creates an organic acquisition path. This is a share-sheet invite only, not a reward/referral program. Avoid cash rewards until abuse prevention, eligibility rules, disclosures and fraud checks are implemented.


## Code review fixes
- Aggregate social writes moved to callable server functions with Firestore transactions; direct client writes to likes, votes and comments are denied, and clients cannot edit post counters.
- Poll vote callable validates post existence/type, option bounds, closed/expired status, and vote-change policy. Comment callable validates text and optional reply target.
- Account deletion now removes the account’s likes, votes and comments from other users’ posts before removing owned posts/profile.
- CI workflow now runs Android unit/build checks and Firestore Rules emulator tests before attempting signed AAB creation.
- The app-name Robolectric assertion now expects `Daily Quiz`.
- Destructive Room migration fallback was removed to reduce risk of silent local-data loss. Add an explicit migration for every future schema version change.
- A unit test now checks question IDs, required text/options, explanations, and answer-index bounds. Passing this test does not mean the question facts are editorially verified.
- A successful Android build, emulator integration test run, Firebase deployment, and signed AAB are still not verified by this ZIP-editing environment.


## 4,800-question category target (2026-10-18)

The local question bank now includes a supplemental category bank intended to bring a fresh installation to 400 questions in each of 12 categories: History, Indian Polity, Geography, General Science, Indian Economy, Static GK, Sports, Entertainment, Kids, Currency, Current Affairs, and Facts. Questions are four-option MCQs with an answer index and explanation. Editorial fact-checking is still required; current-affairs items must be date/source checked before release. Android build and Firebase emulator tests were not run in this environment.


## Wallet/profile update (v9 code changes)
- Profile name and bio editing now saves to Firebase Auth + Firestore.
- Target exam selections now load/save to Firestore.
- Profile includes wallet balance and withdrawal request UI.
- Cloud Functions added: `requestWalletWithdrawal`, `adminCreditWallet`, `adminReviewWithdrawal`.
- Wallet balances and ledger are server-write-only in Firestore Rules; owner can read their own records.
- Wallet and UPI withdrawal records are included in account deletion cleanup.
- These features require deploying Functions and Rules and running the Firebase Emulator tests before production use. UPI payout remains a manual operator action; no payment provider is connected.

## Wallet and profile additions (v9)
- Profile display name/bio edit and target-exam preference persistence are implemented in Android code.
- Wallet balance and withdrawal UI use server-side callable functions. User clients cannot set wallet balances, create ledger transactions, or mark withdrawals paid.
- Admin-only Functions: `adminCreditWallet` and `adminReviewWithdrawal`; claim helper is `functions/set-admin-claim.js`. Never grant the admin claim to an ordinary user.
- Withdrawal UPI transfer is manual, not automatic; paid status must be set only after the actual transfer. Wallet/UPI data is included in account deletion cleanup.
- Firebase Functions deployment, Emulator tests, Android compile, and device tests are not yet confirmed from this environment.
