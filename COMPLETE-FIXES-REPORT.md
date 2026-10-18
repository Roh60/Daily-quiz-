# GK Swal — code review fixes and release gates

## Applied in this revision

1. **Community counters protected:** clients can no longer directly edit `likesCount`, `commentsCount`, `pollOptions`, or `totalVotes` on posts.
2. **Server-owned social writes:** `togglePostLike`, `voteOnPoll`, and `addPostComment` callable Cloud Functions update interaction documents and aggregate counts together in Firestore transactions.
3. **Poll validation:** the server checks that a post exists, is a poll, has 2–4 options, is not closed/expired, the requested option exists, and vote changes comply with the poll setting.
4. **Comment validation:** comments are limited to 1–500 characters; replies must point to an existing comment; comment creation and the count update are atomic.
5. **Firestore Rules tightened:** client writes to like, vote, and comment subcollections are denied. These operations require deployed callable functions.
6. **Account deletion cleanup expanded:** the deletion function removes the account's likes, votes, and comments on other people's posts, submitted reports, owned posts, follow references, profile data, and Auth identity. Interaction counters are decremented as the account's likes/comments/votes are removed, and follower/following counters are updated for other affected profiles.
7. **Recursive post deletion:** `deleteMyPost` verifies post ownership and recursively deletes the post plus nested likes, comments, and votes. Android now calls this function instead of directly deleting the parent document.
8. **Atomic follow/unfollow:** `toggleFollowAuthor` creates/deletes both sides of the relationship in one transaction, rejects self-follow and missing profiles, and updates follower/following counts. Android uses the callable function.
9. **Direct deletion and follow writes blocked:** Firestore rules deny client-side profile deletion, post deletion, and follow-edge writes. New posts cannot start with forged engagement counters; poll option text and zero initial vote counts are validated.
10. **CI expanded:** the GitHub Actions workflow now runs Android unit tests/debug build and Firestore Rules emulator tests before attempting a signed AAB build.
11. **Room data protection retained:** no destructive migration fallback is used. Add an explicit migration for every future schema change.
12. **Question-bank structure test retained:** IDs, question text, option text, explanations, and answer index ranges are checked by a unit test.

## Required deployment order

1. Confirm `app/google-services.json`, package ID, and the configured named Firestore database belong to the Firebase project you own.
2. Read and review `firestore.rules` and `functions/index.js` before deployment.
3. Deploy rules and functions to the intended Firebase project/database. Cloud Functions generally require the Blaze billing plan; review budgets and billing alerts first.
4. Test sign-in, creating a post, liking/unliking, voting/changing a vote, commenting/replying, following, reports, and account deletion on a test account.
5. Push the project to your private GitHub repository and configure `UPLOAD_KEYSTORE_BASE64`, `STORE_PASSWORD`, and `KEY_PASSWORD` repository Actions secrets. Never commit the keystore or passwords.
6. Wait for both verification jobs and the signed-AAB job to pass. Download the `gk-swal-release-aab` artifact and install it in an internal testing track before production.

## Not represented as complete

- Node syntax checks passed for `functions/index.js` and `firestore.test.js`. New privacy regression tests are included, but Firestore emulator tests were not executed in this editing environment; rule compilation and integration still need CI/emulator confirmation.
- No Android/Gradle build was run in the editing environment because Gradle and the Android SDK are unavailable here. The workflow installs Gradle directly, so it does not require the absent Gradle wrapper JAR.
- The Firebase Functions have not been deployed or run against a live Firebase project. Deploy them before using server-managed social actions; until then like/vote/comment/follow/post-delete actions using callables will fail.
- AdMob and Google UMP consent are not integrated. Ad unit IDs and a publisher account are needed before ads can be tested.
- Current Affairs attempts to load the official PIB RSS feed in-app; the feed has not been verified on a physical device and is not a curated exam digest.
- The bundled question bank contains 4,800 structurally audited draft items (12 categories × 400), contains repetitive patterns, and needs editorial/factual review. It is not represented as 10,000 verified questions.
- Privacy policy and account-deletion HTML are templates that must be completed and hosted at public URLs.
- Cloud Function App Check enforcement and production abuse/rate limits need configuration before a public launch. Callable functions currently require Firebase Authentication but have App Check enforcement disabled to avoid breaking an app that has not yet initialized a production App Check provider.
- Account deletion now uses a durable lock/job record, stage markers, bounded pages, and retry-safe cleanup. If a single very large account still exceeds the 540-second invocation window, an external scheduled/background queue and monitoring are the next production hardening step.

## Additional fixes in the latest review

13. **Recursive post deletion is race-aware:** `deleteMyPost` first marks an owned post as deleting in a transaction; like/vote/comment callables reject new interactions against posts being deleted; then the function recursively deletes the post tree. Client-side edits are blocked after the deleting flag is set.
14. **Follow graph protected:** direct client create/update/delete of `followers` and `following` documents is denied, and direct client profile deletion is denied. Follow/unfollow must use the callable function so both edge documents and profile counts change together.
15. **Initial counter/poll abuse reduced:** new posts cannot start with nonzero likes/comments/votes. New polls must have 2–4 options with non-empty bounded text and zero initial votes.
16. **Private like/vote records:** Firestore rules now allow each signed-in user to read only the like/vote document whose ID matches their UID. Public UI should use aggregate counts on the post, not enumerate personal records. Regression tests were added for direct reads and collection listing by another user.
17. **Account-wide deletion lock:** a durable `account_deletions/{uid}` job/lock is created before cleanup. Like, vote, comment, follow, and post-delete callables check the lock transactionally; following an account being deleted is blocked too. A failed cleanup leaves the lock in place to prevent new activity from recreating orphaned data.
18. **Chunked, retryable deletion:** cleanup uses bounded pages for interactions, reports, owned-post deletion, and post marking; the job records its current stage. Re-running the callable repeats idempotent stages, skips already-deleted interaction documents, and only adjusts counters when the interaction still exists. The callable timeout is increased to 540 seconds and memory to 1 GiB. Very large accounts may still need a scheduled/background queue if one invocation repeatedly times out.
19. **Privacy regression tests added:** `firestore.test.js` includes tests that one user cannot read another user's individual like/vote document or list the subcollection.
20. **Functions integration suite added to CI:** `functions.integration.test.js` exercises callable endpoints in the Firebase Emulator for concurrent like toggles, poll vote changes, account-deletion cleanup/counter/follow-edge behavior, and rejection of social actions during account deletion. The workflow now starts Auth, Firestore, and Functions emulators and runs both rules and callable integration tests. These CI tests have not been executed from this editing environment, so their first GitHub Actions run must be reviewed before release.


## Final hardening pass (v4)

21. **Deletion lock now covers direct client writes:** profile create/update, quiz-history create/delete, post create/update, and report creation check the durable `account_deletions/{uid}` lock in Firestore Rules. Rules regression tests cover blocked writes during deletion and allowed behavior without a lock.
22. **App Check integrated:** Android initializes App Check before Firebase SDK use, using the debug provider in debug builds and Play Integrity in release builds. Deployed callable functions enforce App Check; Functions Emulator runs bypass enforcement for local integration tests. Before production, register the Android app in Firebase App Check, register the debug token for local testing, configure Play Integrity in Firebase Console, and verify the package/SHA fingerprints.
23. **Deletion execution lease and stage resume:** the callable uses a short-lived lease to reduce concurrent cleanup runs, resumes from the last recorded stage, and releases the lease on failure while retaining the deletion lock. Each current stage must still be rerun after interruption, so stage operations remain retry-safe. Very large accounts still need a background queue/monitoring if one invocation repeatedly hits the 540-second limit.
24. **Verification limitation:** syntax and package/archive checks can be performed locally, but Firebase Emulator integration tests, Android compilation, App Check attestation, Firebase deployment and a signed AAB must pass in GitHub Actions/Firebase before release.


## 4,800-question category target (2026-10-18)

The local question bank now includes a supplemental category bank intended to bring a fresh installation to 400 questions in each of 12 categories: History, Indian Polity, Geography, General Science, Indian Economy, Static GK, Sports, Entertainment, Kids, Currency, Current Affairs, and Facts. Questions are four-option MCQs with an answer index and explanation. Editorial fact-checking is still required; current-affairs items must be date/source checked before release. Android build and Firebase emulator tests were not run in this environment.

## Question bank quality pass (18 October 2026)
- Rebuilt answer choices for all 3,483 supplemental questions using same-category answer pools and inferred question type where possible, to reduce unrelated distractors.
- Confirmed by source scan that the app's question constructors contain 4,800 unique IDs across the 12 requested categories, 400 each. This is a structural count, not a factual accuracy guarantee.
- Added a unit-test assertion requiring four distinct options per question.
- Full editorial verification is still required; in particular, current-affairs items need dated official sources and periodic refresh. Android compilation and test execution have not been completed in this environment.

## Final preflight revision (v5)

23. Added explicit Node.js 20 setup in the Android CI job before Node syntax checks.
24. Added `tools/verify_project.sh`, a non-destructive local preflight for question-bank structure, Android/Firebase config, release blockers, JavaScript syntax, and JSON validity. It reports when Android build tests cannot run because Gradle/wrapper is unavailable.
25. Strengthened release-readiness placeholder detection for common `YOUR_*`, `TODO`, and example-domain strings in legal pages.
26. Corrected the release checklist so the active target is documented as 4,800 questions (12 categories × 400) with factual review required; 10,000+ is clearly marked as a future target only if product requirements change.

Validation performed for this revision: `bash tools/verify_project.sh` passed question-bank structure, Firebase/package configuration, JavaScript syntax, and JSON checks. The audit correctly reports that privacy/deletion pages still need real developer contact details and public hosting, and that Gradle build tests did not run in this environment. No Firebase deployment, signed AAB, or device test is claimed.
