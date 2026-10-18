# GK Swal v6 — changes and validation

## Implemented in source
- Added Google Mobile Ads and Google UMP SDK dependencies.
- Added a consent manager that requests UMP consent before enabling ad requests and exposes a privacy-options entry point when UMP says it is required.
- Added a banner composable gated by both UMP consent and a configured banner ad-unit ID.
- Debug uses Google's official sample test IDs; release needs `ADMOB_APP_ID` and `ADMOB_BANNER_AD_UNIT_ID` environment values.
- Updated GitHub release workflow and strict audit to require production AdMob IDs and non-placeholder legal pages before signed AAB creation.

## Checks performed
- Question bank structural audit: expected 4,800 questions, 12 categories with 400 each, unique IDs, valid option/answer structure. This is not a factual accuracy review.
- Android/Firebase config audit: passed.
- JavaScript syntax checks: passed.
- JSON parsing: passed.
- Release audit: expected to report blockers until actual public legal URLs/contact details are supplied.
- ZIP integrity: checked when packaging.

## Not claimed as complete
- Android compile / unit tests / emulator tests were not run in this environment because Gradle and Android SDK are not installed.
- Production AdMob IDs, public privacy/account deletion URLs, Firebase deployment/App Check enforcement, signed AAB and physical-device consent tests still require the owner's accounts and action.
- Question-bank factual accuracy still requires human/source verification.
