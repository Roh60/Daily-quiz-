# GK Swal — बाकी काम और अंतिम सेटअप (Hindi)

यह दस्तावेज़ उन कामों को अलग करता है जिन्हें कोड में तैयार किया जा सकता है और जिन्हें मालिक के वास्तविक विवरण / Google या Firebase Console access के बिना सुरक्षित रूप से पूरा नहीं किया जा सकता।

## इस ZIP में कोड स्तर पर तैयार

- प्रश्न बैंक संरचना audit: 4,800 रिकॉर्ड, 12 श्रेणियाँ, प्रति श्रेणी 400, unique IDs और विकल्प/answer index checks। यह तथ्य-जाँच नहीं है।
- Android/Firebase configuration preflight और Cloud Functions JavaScript syntax checks।
- GitHub Actions में debug APK artifact upload, Android unit tests और Firestore emulator test job।
- Signed release job डिफ़ॉल्ट रूप से बंद है। उसे केवल public legal pages भरने और signing secrets तैयार करने के बाद enable करें।

## मालिक से जरूरी जानकारी / Console actions

1. **Privacy Policy:** कानूनी developer/business name, support email और public website (यदि उपलब्ध हो) भरें। अपने वास्तविक डेटा व्यवहार के अनुसार पॉलिसी जाँचें और HTTPS URL पर प्रकाशित करें।
2. **Account deletion page:** वही support email भरें, page सार्वजनिक HTTPS URL पर publish करें, और deployed Firebase callable से वास्तविक deletion test करें।
3. **Firebase:** पुष्टि करें कि `app/google-services.json` आपके अपने Firebase project का है। App Check में Android app और Play Integrity register करें; debug token register करें। पहले test करें, फिर ही production enforcement करें।
4. **Cloud Functions:** billing, budget alerts और database ID की पुष्टि के बाद deploy करें; functions integration tests पास होने चाहिए।
5. **AdMob/UMP:** अभी विज्ञापन SDK और consent SDK integrate नहीं हैं। अपने AdMob app ID/ad-unit IDs मिलने और consent/privacy flow परीक्षण के बाद ही ads enable करें। इस ZIP में कोई विज्ञापन चलने का दावा नहीं है।
6. **Signed AAB:** निजी upload keystore बनाएँ और सुरक्षित रखें। GitHub Actions secrets `UPLOAD_KEYSTORE_BASE64`, `STORE_PASSWORD`, `KEY_PASSWORD` सेट करें। Repository variable `ENABLE_SIGNED_RELEASE=true` केवल तभी रखें जब legal pages में कोई placeholder न हो और release gate पास हो।
7. **Question factual review:** `question-bank-fact-check-tracker.csv` / `question-bank-supplement-review.csv` में प्रत्येक प्रश्न के लिए विश्वसनीय source, reviewer और verification status दर्ज करें। Current Affairs के लिए publication date और original official source जरूरी हैं।

## फोन से APK कैसे लें

1. ZIP को GitHub repository में upload/push करें।
2. GitHub → Actions → **Verify and build GK Swal** खोलें और workflow चलाएँ।
3. `verify-android` job सफल होने पर `gk-swal-debug-apk` artifact डाउनलोड करें।
4. APK को पहले अपने फोन/test devices पर जाँचें। यह Play Store production release नहीं है।

## ईमानदार release status

इस source archive से Android compilation, emulator tests, Firebase deployment, AdMob consent flow, factual review या production AAB को इस वातावरण में वास्तविक रूप से सफल घोषित नहीं किया गया है। इनकी पुष्टि GitHub Actions / Firebase Console / test device से करनी होगी।


## AdMob + UMP update (v6)
- AdMob SDK and Google UMP SDK dependencies are now included.
- The privacy-policy template now discloses the SDK and consent-gated ad behaviour; publisher contact placeholders still must be filled.
- UMP consent is requested on app launch; the banner is hidden unless UMP permits ad requests and an ad unit ID is configured.
- Debug uses Google's official sample App ID and banner ad unit. These are test IDs, not monetization IDs.
- Before a signed release, add GitHub Actions secrets `ADMOB_APP_ID` and `ADMOB_BANNER_AD_UNIT_ID` from your own AdMob account. Do not publish with test IDs.
- The signed-release audit still blocks until legal pages no longer contain placeholders and signing secrets are set.
- Android compile and on-device consent verification must be run in GitHub Actions / on a phone; this environment does not have Gradle/Android SDK available.


## Wallet/profile update (v9 code changes)
- Profile name and bio editing now saves to Firebase Auth + Firestore.
- Target exam selections now load/save to Firestore.
- Profile includes wallet balance and withdrawal request UI.
- Cloud Functions added: `requestWalletWithdrawal`, `adminCreditWallet`, `adminReviewWithdrawal`.
- Wallet balances and ledger are server-write-only in Firestore Rules; owner can read their own records.
- Wallet and UPI withdrawal records are included in account deletion cleanup.
- These features require deploying Functions and Rules and running the Firebase Emulator tests before production use. UPI payout remains a manual operator action; no payment provider is connected.


## Wallet/Profile (v9)
- Profile display name और bio editing Firestore/Auth में save होती है; target exam preferences Firestore में persist होती हैं।
- Profile के भीतर wallet balance और UPI withdrawal request UI है। ₹100–₹10,000 request amount server-side validate होता है और balance transaction में reserve होता है।
- `requestWalletWithdrawal`, `adminCreditWallet`, `adminReviewWithdrawal` Functions जोड़े गए हैं। Wallet/ledger writes केवल trusted Functions/Admin SDK कर सकते हैं।
- Admin claims trusted environment से ही grant करें: `ADMIN_UID=... ADMIN_ACTION=grant GCLOUD_PROJECT=... npm --prefix functions run set-admin-claim`. यह production credential वाला command है; mobile client से न चलाएँ।
- Payout auto-transfer नहीं है; operator को UPI payment अपने authorised workflow से करके ही request को `paid` mark करना चाहिए। Functions/Rules deploy और emulator tests अभी चलाना बाकी है।
