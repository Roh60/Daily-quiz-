# GK Swal — Full Feature Inventory (v12)

यह दस्तावेज़ source code में मौजूद सुविधाओं और अभी बाकी verification/deployment को अलग-अलग बताता है। Code मौजूद होने का अर्थ यह नहीं कि live Firebase पर deploy या physical phone पर test हो चुका है।

## Android user app — code मौजूद
- Firebase Authentication / Google sign-in flow.
- User profile screen; display name, bio और target-exam preference save/load.
- Local Room question bank; 12 categories के 4,800 unique MCQ records (हर category में 400 structural records).
- Quiz/practice, question explanations, bookmarks/library.
- Daily GK Challenge और local study streak.
- Current Affairs screen जो PIB RSS feed और original links दिखाती है; यह fact-checked exam digest नहीं है.
- Social feed: post list, text posts, poll creation/options, likes, comments/replies, follow/unfollow, reporting UI.
- Profile wallet balance/ledger display और UPI withdrawal request form.
- Invite/share sheet; referral attribution या referral cash rewards नहीं.
- AdMob + Google UMP consent integration code; production IDs और device consent verification बाकी.
- Privacy/account deletion links खोलने का UI, यदि real public HTTPS URLs build configuration में दिए गए हों.

## Firebase backend — source code मौजूद; deployment अलग से करना है
- Callable functions: post like toggle, poll vote, comment/reply, follow/unfollow, delete own post.
- Account deletion workflow और profile/posts/interactions/follows/wallet/withdrawal/reward claim cleanup.
- Server-controlled wallet withdrawal requests, admin wallet credit, withdrawal review.
- Practice reward claim submission with daily limit; submitted score is untrusted and submission itself never credits money.
- Admin approval/rejection of reward claims with transaction ledger idempotency.
- v12: content report submission via callable and admin review/dismiss decision.
- Firestore rules deny direct client writes for sensitive social counters, wallet/ledger, withdrawal requests, reward claims and reports.
- Admin functions depend on a trusted Firebase custom claim `admin: true`; never use a user-editable profile role for access control.

## Tests/tooling included
- Question bank structural audit and fact-check tracker coverage audit.
- Project/Firebase configuration validation and release-readiness audit.
- JavaScript syntax checks and JSON/XML parsing checks.
- Android GitHub Actions workflow to compile, run unit tests and upload a debug APK.
- Firebase emulator workflow for Firestore Rules and callable integration tests, including wallet reward claims and report moderation.
- Rules tests now explicitly target the configured named Firestore database ID.

## Important missing / not verified
1. **Build/device verification:** Android compile, unit tests, install and real-device flows have not been confirmed from this editing environment.
2. **Firebase deployment:** Functions and rules are source only until deployed to the intended project; App Check/Play Integrity must be configured and tested.
3. **Question fact checking:** all 4,800 records have structural checks, but the tracker currently marks 0 as source-verified. Review each against reliable sources before claiming factual verification.
4. **Cash rewards:** no secure automatic quiz reward yet. To enable that safely, questions/answer keys and attempt scoring must be server-authoritative; client-reported correct counts are not proof.
5. **UPI payout:** requests are recorded, but no payment gateway/UPI payout API is connected. An authorized operator must transfer money and then update status through the protected admin flow.
6. **Admin UI:** backend admin callable functions exist, but a dedicated owner/moderation dashboard UI is not included in the Android user app.
7. **Referral system:** share-sheet invite only; no referral attribution, fraud checks or referral rewards.
8. **Ads:** real AdMob app/ad unit IDs and on-device UMP consent/no-consent/privacy-options testing are required.
9. **Legal/store:** privacy policy and deletion page still need real developer contact details, accurate data practices, public HTTPS hosting and Play Console declarations.
10. **Release signing:** owner must supply a secure upload keystore and GitHub Actions secrets; signed AAB is intentionally gated.
11. **Live current affairs:** the screen fetches official source links/RSS; it does not create or independently fact-check a full current-affairs summary feed.
12. **Other external services:** live currency conversion, automated payouts, verified referral earnings and production owner dashboard are not implemented.

## Checks to run after uploading to GitHub
1. Actions → **Verify and build GK Swal** → Run workflow.
2. Both `Android compile and unit tests` and `Firestore security rules tests` must pass.
3. Download `gk-swal-debug-apk` and install on a test phone.
4. Test sign-in, offline quiz, bookmark, post, poll, like, comment, follow, report, wallet claim, withdrawal request and account deletion.
5. Deploy rules/functions to the correct Firebase project only after reviewing project ID, database ID, App Check and billing.

## v13 additions (implementation code; not yet deployed/live-verified)
- Server-owned quiz sessions and server-side scoring with one qualifying cash reward per UTC day, provided the published question bank was human fact-checked and imported.
- New Android Earn & Tools tab for secure quiz sessions, referral-code actions and currency conversion.
- Server-issued referral codes; one redemption per account; admin-reviewed, idempotent wallet rewards.
- Currency conversion callable using Frankfurter/ECB reference rates (indicative only).
- RazorpayX UPI payout submission adapter and admin status-sync callable. Requires RazorpayX activation and Firebase Secret Manager credentials; provider/webhook reconciliation is not live-verified.
- Standalone owner/moderation dashboard source under `owner-dashboard/`.
- Local CSV question fact-check workbench under `tools/fact-check-review/` and strict verified-question import script `tools/import_verified_quiz_bank.js`.
- Additional Firestore rules tests and integration tests for server-verified quiz and referrals.

These are code additions. The app still requires a verified server quiz bank, Firebase deployment, RazorpayX/AdMob account setup, real legal contact details/public URLs, and CI build/emulator tests. The 4,800 shipped question records are not all factually verified.


## Global Currency GK Quiz (v15)

- Android asset: `app/src/main/assets/global_currency_gk_questions.json` contains 2,003 four-option questions on country/currency names and ISO codes.
- Dedicated CSV and unverified fact-check tracker are included at project root.
- The $1 = ₹96 and €1 = ₹108 items are explicitly labeled user-supplied practice examples, not current rates.
- Question-bank structure is validated; factual verification of every row is still pending.


## v16 — 10,000 quiz-entry expansion (2026-10-18)

- 4,800 existing database questions preserved.
- Added 3,197 revision MCQ entries across the 11 non-currency categories (291 each for History, Indian Polity, Geography, General Science, Indian Economy, Static GK, Sports; 290 each for Entertainment, Kids, Current Affairs, Facts).
- Existing 2,003 Global Currency GK questions remain in the asset bank. Total quiz entries: 10,000 (7,997 database + 2,003 currency asset questions).
- Important limitation: the 3,197 additions are alternate-wording/revision variants of existing source questions, not 3,197 independently new facts. They are marked UNVERIFIED in `expansion-question-review.csv`; human fact-checking is required.

## Daily activity policy (v17)
- Daily Challenge presents up to 5 questions per day. The question selection is stored locally on the device and rotates by date.
- Community poll creation has no daily post-count limit in the current Firestore rules or app flow. Each poll still requires a valid question and 2–4 options, and remains subject to account moderation/deletion controls.
- Note: this is a device-local Daily Challenge selection, not a server-enforced account-wide cap across every offline practice quiz or multiple devices.
