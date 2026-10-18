# GK Swal — Reward review flow (v10)

## What was added
- Wallet screen now lets a signed-in user submit a practice reward claim with quiz title, question count, correct count and duration.
- Claim is stored by `submitPracticeRewardClaim` as `pending_review`; one claim per UTC day is allowed.
- A claim does **not** add money automatically. The current question bank and answer checking run locally, so a client-reported score cannot safely be trusted for automatic cash rewards.
- An admin with Firebase Auth custom claim `admin: true` can use `adminReviewPracticeRewardClaim` with `{claimId, action:"approve", amountPaise, note}` or `{claimId, action:"reject", note}`. Approval is capped at ₹100 and creates an idempotent ledger entry.
- Reward claim documents are owner-readable, not client-writable. Account deletion removes the user's claim and daily limit records.
- Added integration/rules tests for claim validation, one-per-day limit, no immediate wallet credit, owner privacy and client-write denial.

## Important remaining limitation
This is a manual review workflow, not an automated verified-quiz reward system. To safely automate cash rewards, move question selection and answer validation to a trusted backend (server-owned question IDs/answer keys and signed attempt sessions). Never trust `correctAnswers` supplied by the Android client for direct money credit.

## Required deployment/testing
1. Deploy Firestore rules and Functions to the intended Firebase project.
2. Run `firebase emulators:exec --only auth,firestore,functions --project demo-no-project "FIRESTORE_EMULATOR_HOST=127.0.0.1:8085 FIREBASE_AUTH_EMULATOR_HOST=127.0.0.1:9099 node --test functions.integration.test.js"` from `functions/` after installing dependencies and starting configured emulators.
3. Keep the admin custom claim restricted to the trusted owner account. Do not place service account keys in the app or repository.
4. Real UPI transfers are still manual. Mark a withdrawal paid only after a real transfer is confirmed; no payout provider is connected.
