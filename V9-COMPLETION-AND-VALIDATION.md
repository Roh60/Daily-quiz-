# GK Swal v9 — Wallet and profile implementation

## Code changes in this update
- Added a profile wallet card with server-backed available balance, lifetime credited amount and recent withdrawal request status.
- Added a UPI withdrawal request dialog. UI and Cloud Function validate ₹100–₹10,000 and UPI ID format; the backend reserves balance atomically.
- Added `requestWalletWithdrawal`, `adminCreditWallet`, and `adminReviewWithdrawal` callable functions.
- Wallet balance, wallet ledger, and withdrawal request writes are denied to app clients by Firestore Rules. Owners can read only their own wallet/ledger/withdrawals.
- `adminCreditWallet` requires the Firebase Auth `admin: true` custom claim and idempotent `referenceId`.
- `adminReviewWithdrawal` requires the admin claim; rejected requests refund the reserved amount. Mark `paid` only after the real UPI transfer is completed outside the app and a payment reference/UTR is supplied.
- Account deletion now removes wallet ledger and withdrawal request documents containing UPI data.
- Added Firebase Rules tests and Functions emulator tests for wallet ownership, client write denial, invalid UPI, insufficient balance, admin-only credit and atomic withdrawal reservation.
- Added editable profile display name/bio/HTTPS photo URL and persisted target-exam preferences.

## Validation performed in this environment
- `node --check` passes for `functions/index.js`, `functions.integration.test.js`, `firestore.test.js`, and `functions/set-admin-claim.js`.
- Question bank audit passes structural checks: 4,800 IDs and 400 questions per category.
- Fact-check tracker covers all 4,800 questions; 0 are marked fact-verified. This is not factual verification.
- Android/Firebase static configuration validation passes.
- Full Gradle build and Firebase emulator tests were not run here. Installing Functions dependencies timed out, and Gradle/Android SDK are unavailable in this runtime.

## Required before production
1. Deploy `functions` and Firestore rules to the owner's Firebase project, confirm billing and budgets, then run emulator tests.
2. Register App Check / Play Integrity and test with enforcement before production release.
3. Set admin custom claims only for the actual trusted operator using `functions/set-admin-claim.js` from a trusted environment.
4. Implement the operator's real UPI transfer workflow and mark a request `paid` only after payment confirmation. No automatic payout provider is connected in this update.
5. Run Android `testDebugUnitTest assembleDebug`, install APK on a physical device, and test sign-in, profile edit, wallet listener, request, rejection/refund, and account deletion.
6. Complete factual review of all GK questions and publish accurate public legal URLs before Play Store submission.
