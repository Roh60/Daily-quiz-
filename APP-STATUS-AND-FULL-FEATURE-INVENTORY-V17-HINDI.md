# GK Swal — App Status & Full Feature Inventory (v17)

> Status date: 18 October 2026. This document distinguishes source-code features from features that have actually been deployed or tested. Code presence alone does not mean production-ready.

## 1. App overview
- Android app: **GK Swal**
- Native UI: Kotlin + Jetpack Compose
- Local question/progress data: Room database
- Sign-in/social backend: Firebase Authentication, Cloud Firestore and callable Cloud Functions (deployment required)
- Goal: general knowledge practice, daily activity, community quiz polls and learning progress.

## 2. Quiz and learning
- Existing local database bank: 4,800 structurally audited MCQ records across 12 categories (400 each in the original bank).
- Expansion: 3,197 revision/alternate-wording entries across 11 categories. These are not 3,197 independently new facts and need editorial review.
- Global Currency GK: 2,003 asset-loaded MCQs on country currency names and ISO currency codes.
- Total counted quiz entries across these banks: **10,000** (7,997 local database records + 2,003 currency asset questions).
- Multiple-choice practice, answer explanations, topic/library filters and bookmarks.
- Daily Challenge: up to **5 questions per device-local calendar day**, selected locally and rotated by date.
- Study history and a locally calculated study streak.
- Question-bank structural audit scripts and fact-check tracking CSVs.
- Currency sample values $1 = ₹96 and €1 = ₹108 are explicitly practice examples supplied by the user, not live exchange rates.

### Question-bank warning
The 10,000 figure is a count of entries, not 10,000 human-verified unique facts. The 3,197 expansion rows are revision variants. The question bank and explanations require human fact-checking; the fact-check tracker has not established all questions as source-verified. Do not market this as a fully verified exam bank yet.

## 3. Current Affairs
- Screen attempts to load official PIB press-release RSS headlines and original links.
- Offline/feed-error fallback is included.
- This is a source/headline list, not a complete independently fact-checked current-affairs digest. Some source items may not have publication dates.

## 4. Community and polls
- Social feed and text-post flow.
- Poll creation with a question and 2–4 options.
- **No daily poll-post count limit** in the current app/rules flow.
- Like/unlike, vote, comments/replies, follow/unfollow, report content and delete own post flows have client UI and server-callable function source.
- Normal users cannot directly write sensitive counters/interactions when the intended Firestore rules are deployed; interactions depend on deployed callable functions.
- Unlimited does not mean exempt from moderation, account restrictions, abuse prevention or Firebase quotas/costs.

## 5. Account and profile
- Firebase Authentication / Google sign-in flow.
- Profile display name, bio and target-exam preference save/load flow.
- Bookmarks and learning history.
- Phone share-sheet invite card. Referral attribution/rewards are not considered live until backend deployment and testing.
- Account deletion UI/function source; deployment, real account test and public web deletion page are required before release.

## 6. Wallet, earning and referral source
- Wallet balance/ledger display and withdrawal-request form.
- Server-side wallet/ledger transaction logic, admin credit/review functions, reward claim review and referral code/redeem source exist.
- Verified quiz-session/reward function source exists, with server-side scoring/daily reward controls.
- **Not a live payment product yet:** Firebase functions must be deployed and tested; quiz bank needs verification; admin review must be secured; actual UPI payout provider integration/credentials and settlement reconciliation are not confirmed live. A withdrawal request is not proof that money has been transferred.
- No promise of guaranteed earnings should be shown to users until reviewed and legally/commercially configured.

## 7. Ads and consent
- AdMob SDK and Google UMP consent integration source is present.
- Debug uses Google sample test IDs. Production publisher/ad-unit IDs and on-device consent/no-consent/privacy-options testing remain required. Do not claim production ads are live.

## 8. Firebase security and admin
- Firestore rules and callable Cloud Functions source are included.
- Intended rules restrict privileged actions to trusted server-assigned `admin: true` custom claims; client-editable profile roles must not grant admin access.
- Social actions, wallet operations, moderation, referrals and account deletion depend on deploying the correct rules/functions to the intended Firebase project.
- A standalone `owner-dashboard/` source folder is present, but it is not a production-hosted admin dashboard. Configure access and test it before use.
- App Check is present in source; enforcement and production configuration must be verified against the actual app/project.

## 9. Legal, release and automation
- Privacy policy and account deletion HTML templates exist, but contain placeholders/setup notices that must be replaced with accurate developer/contact/data-practice details.
- GitHub Actions workflow source for Android build/test/debug APK and a gated signed AAB flow is included.
- Workflow for publishing legal pages is included; it must not be used until legal content is complete.
- Audit scripts cover project configuration, release readiness, question-bank structure and fact-check tracking.

## 10. Explicitly not verified / not complete
1. Android build and installation on a physical device have not been confirmed for this ZIP.
2. GitHub Actions has not been confirmed passing for this revision.
3. Firebase Firestore rules and functions have not been confirmed deployed to the intended live project.
4. All quiz questions have not been human fact-checked.
5. UPI payouts, production AdMob ads, referral rewards and live wallet earning are not confirmed operational.
6. Public privacy/deletion URLs and Play Store declarations are not complete.
7. Signed AAB and Play Store release/approval are not complete.
8. Daily 5-question limit is device-local, not a server-enforced account-wide limit across all offline practice or multiple devices.

## 11. Before public GitHub publication
- Prefer a **private repository initially** until the project owner confirms the Firebase project/configuration can be exposed and has reviewed all source files.
- Never commit upload keystores, signing passwords, service-account JSON, real secrets, or `.env` values.
- `app/google-services.json` is a Firebase client configuration file, not a service-account private key, but it identifies the Firebase project. Review Firebase Security Rules, API key restrictions and quotas before public exposure.
- Replace legal placeholders before publishing legal pages or preparing a release.
- Run GitHub Actions, install the generated debug APK and test main user flows before calling it verified.
