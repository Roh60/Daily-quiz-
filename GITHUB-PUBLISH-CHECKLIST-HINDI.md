# GitHub Publish Checklist — GK Swal (Mobile-friendly)

## Recommended first publication
1. Create a **Private** repository named `gk-swal-android` on GitHub.
2. Upload the contents of this ZIP's `gkswal-work/` directory (not the outer ZIP itself).
3. Confirm `.gitignore` is included and verify no `.jks`, `.keystore`, service-account JSON, `.env`, passwords or production secrets are being uploaded.
4. Keep the repository private until Firebase project configuration, Firestore rules, legal placeholders and ownership are reviewed.
5. Open the **Actions** tab and run **Verify and build GK Swal**.
6. Only after the workflow succeeds, download the `gk-swal-debug-apk` artifact and test it on a real Android phone.
7. Fix any compile/test errors before setting up a signed release. Do not enable signed release while legal placeholders remain.

## Secrets must stay in GitHub Actions Secrets
For signed AAB workflow only, create a private upload keystore and add the expected values under repository **Settings → Secrets and variables → Actions**. Never add the keystore or passwords to the repository, ZIP, README or chat.

## Before public visibility or Play Store release
- Review `app/google-services.json` and Firebase project ownership/security.
- Deploy and test Firestore rules/functions only against the correct Firebase project.
- Complete `legal/privacy-policy-template.html` and `legal/delete-account.html` with truthful information and public HTTPS hosting.
- Set real AdMob IDs and verify consent behaviour on-device.
- Human fact-check quiz content and mark review status accurately.
- Test sign-in, offline quiz, bookmarks, poll create/vote, likes/comments/follow/report, wallet/reward flows and account deletion.
- Verify signed AAB on an internal testing track before production.

## Important feature status
Daily Challenge currently shows up to 5 questions per device-local day; it is not a cross-device account-wide daily cap. Poll creation has no daily count cap in the current app/rules flow, but moderation and service quotas still apply. The project counts 10,000 quiz entries, but the 3,197 expansion rows are revision variants and all content needs factual review.
