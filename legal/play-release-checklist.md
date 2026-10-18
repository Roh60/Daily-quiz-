# GK Swal — release blockers checklist

This checklist is intentionally not marked complete until each item is verified against the final build.

- [ ] Set a permanent, developer-owned Android `applicationId`; changing it after publishing creates a different Play app.
- [ ] Set `versionCode` higher than every previously uploaded artifact and set the release version name.
- [ ] Fix Firestore rules and deploy them to the exact named database configured in the app.
- [ ] Test Google sign-in on a physical device with the production Firebase OAuth SHA fingerprints.
- [ ] Verify account deletion actually removes account-linked Firestore data and Firebase Authentication identity; do not publish with a partial deletion flow.
- [ ] Add a real public privacy policy URL with the placeholders in `privacy-policy-template.html` completed.
- [ ] If AdMob is enabled: add the SDK, production App ID and ad unit IDs, consent flow where required, test ads during development, and accurate data-safety disclosures.
- [ ] Verify the current PIB RSS integration on real Android devices; confirm dates, source attribution, empty/error/offline states, refresh behaviour and the feed terms before release.
- [ ] Fact-check the current 4,800-question bank (12 categories × 400) against reliable sources, replace repetitive/weak questions, and record provenance for each checked item. If the product requirement is later raised to 10,000+, add independently written and reviewed questions before claiming that count. Do not bulk-copy copyrighted books.
- [ ] Run unit tests, Firestore rules tests, lint, release build and on-device regression tests.
- [ ] Create a signed AAB with a secure upload key. Keep the keystore and passwords out of the ZIP/repository.
- [ ] Complete Play Console store listing, screenshots, app access instructions, content rating, target audience, Data safety, ads declaration, app access, privacy URL and any applicable account-deletion URL.
- [ ] Test on the required Play testing track and address policy/pre-launch report issues before production rollout.
