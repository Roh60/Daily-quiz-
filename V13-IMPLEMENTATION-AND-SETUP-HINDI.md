# GK Swal v13 — नए फीचर, सेटअप और वास्तविक सीमाएँ

## इस build में source-code additions

1. **Server-verified quiz reward backend**: `startVerifiedQuizSession` server-owned `quiz_questions` से प्रश्न चुनता है और answer keys client को शुरू में नहीं भेजता। `submitVerifiedQuizSession` score server पर निकालता है, 30-minute session expiry और 5-second minimum लागू करता है, एक user को UTC day में अधिकतम एक quiz reward देता है, और wallet/ledger में transactionally credit करता है। Reward per quiz `0–₹100` और threshold admin-imported quiz config में है।
2. **Earn & Tools Android screen**: नया tab secure quiz session, referral code/redeem और currency converter callables इस्तेमाल करता है। Default quiz ID `daily-general` है; पहले verified quiz bank import और publish करना अनिवार्य है।
3. **Referral system**: server-issued code, self-referral रोकना, प्रति referred account एक redemption, pending review और admin approval पर referrer + referred user को default ₹20 each (maximum ₹20 each) idempotent ledger credits। Fraud/eligibility checks फिर भी admin को करने हैं।
4. **Currency conversion**: authenticated `convertCurrency` callable Frankfurter/ECB reference rates इस्तेमाल करता है। यह bank/card payout rate नहीं है; external service unavailable हो तो error लौटता है।
5. **RazorpayX UPI payout adapter**: `adminInitiateUpiPayout` server-side secret credentials, UPI fund account और provider payout endpoint का उपयोग करता है। Payout request को `processing` करता है; provider status की confirmation के बिना इसे `paid` न mark करें। Network errors में provider dashboard जाँचें—manual duplicate transfer न करें। Production से पहले RazorpayX account activation, payout permissions, provider test और webhook/status reconciliation आवश्यक हैं।
6. **Owner dashboard source**: `owner-dashboard/index.html` moderation reports, reward claims, referral requests और withdrawals के लिए है। इसमें Firebase Web config तथा App Check reCAPTCHA v3 site key भरें, HTTPS पर host करें, फिर `admin:true` custom claim वाले account से sign in करें। यह अभी standalone static dashboard source है; आपके Firebase project में deployed नहीं है।
7. **Question fact-check workbench**: `tools/fact-check-review/index.html` tracker CSV को local browser में खोलता है और source/reviewer/date सहित review status save करके updated CSV download करता है। यह facts को अपने आप सत्यापित नहीं करता।
8. **Server quiz importer**: `tools/import_verified_quiz_bank.js` सिर्फ उन questions को import करता है जिनमें `factCheckStatus=VERIFIED`, HTTPS `sourceUrl`, `verifiedBy` और `verifiedOn` मौजूद हों। इससे गलत answers अपने आप सही नहीं हो जाते—reviewer को primary source पढ़कर जाँच करनी है।
9. **Rules और deletion cleanup**: client को quiz answer keys/session docs और referral accounting collections का direct access नहीं; account deletion cleanup में quiz sessions और referral records भी शामिल किए गए हैं।
10. **Legal text updated**: privacy/deletion draft में referral, server quiz, currency rates और provider payout data practices का उल्लेख जोड़ा है। Developer identity/contact अभी भी जानबूझकर placeholder है; वास्तविक विवरण के बिना legal pages publish नहीं होंगे।

## Verified quiz bank import — आवश्यक schema

JSON input का format:

```json
{
  "questions": [
    {
      "id": "question-001",
      "question": "Question text",
      "options": ["A", "B", "C", "D"],
      "correctIndex": 0,
      "explanation": "Evidence-backed explanation",
      "factCheckStatus": "VERIFIED",
      "sourceUrl": "https://official-source.example/page",
      "verifiedBy": "reviewer initials",
      "verifiedOn": "2026-10-18"
    }
  ],
  "quizzes": [
    {
      "id": "daily-general",
      "title": "Daily General GK",
      "questionIds": ["question-001", "question-002", "question-003", "question-004", "question-005"],
      "questionCount": 5,
      "rewardPaise": 100,
      "rewardMinPercent": 80,
      "published": true
    }
  ]
}
```

`questionCount` 5–100 होना चाहिए और हर ID import file में मौजूद होना चाहिए। उदाहरण में दिखाए गए `official-source.example` को असली official source से बदलें; placeholder URL import validation पास नहीं करेगा। Service-account JSON को ZIP/GitHub में commit न करें। Secure machine/CI secret में रखें और command चलाएँ:

```bash
GOOGLE_APPLICATION_CREDENTIALS=/secure/service-account.json node tools/import_verified_quiz_bank.js vetted-quiz-bank.json
```

Import command तभी चलाएँ जब प्रश्नों और answers का human fact-check पूरा हो चुका हो। 4,800 प्रश्नों का पूरा verified bank इस ZIP में स्वतः नहीं बन जाता। मौजूदा tracker के सभी 4,800 entries अभी भी स्वतंत्र factual review माँगते हैं।

## Firebase Functions deploy

पहले Firebase CLI से उसी project में login करें और `firebase.json`/project configuration की पुष्टि करें। Functions deploy करने से पहले dependencies install करें। RazorpayX secrets (कभी source file में न डालें):

```bash
firebase functions:secrets:set RAZORPAYX_KEY_ID
firebase functions:secrets:set RAZORPAYX_KEY_SECRET
firebase functions:secrets:set RAZORPAYX_ACCOUNT_NUMBER
firebase deploy --only functions,firestore:rules
```

ये commands अभी इस environment से आपके Firebase पर नहीं चलाई गईं। Production payout से पहले RazorpayX account, API access, UPI payout permissions, limits, webhook/provider status reconciliation और legal obligations जाँचें।

## Owner dashboard publish

`owner-dashboard/index.html` को HTTPS hosting पर deploy करें। Firebase Console में web app config लें, reCAPTCHA v3 site key बनाएं और App Check में web provider register करें। फिर owner dashboard से sign-in करें। Custom claim केवल trusted owner UID को Admin SDK/script से set करें; client profile field से admin role कभी न लें।

## Real payout

- Provider keys सिर्फ Firebase Secret Manager में रखें।
- First use से पहले RazorpayX test/live environment और account-number settings verify करें।
- Dashboard में UPI ID और amount verify करने के बाद payout submit करें।
- Provider success confirmation/webhook और bank/UPI reconciliation अभी अलग से implement/test करना बाकी है; function response `processing/queued` को paid receipt न मानें।
- Existing manual admin review callable अभी भी मौजूद है; operational policy में provider confirmation के बिना paid mark करने से रोकें।

## Production AdMob and signed AAB

Workflow में `ENABLE_SIGNED_RELEASE=true` repository variable और ये GitHub Actions secrets चाहिए:

- `ADMOB_APP_ID`
- `ADMOB_BANNER_AD_UNIT_ID`
- `UPLOAD_KEYSTORE_BASE64`
- `STORE_PASSWORD`
- `KEY_PASSWORD`

और variables:
- `PUBLIC_PRIVACY_POLICY_URL`
- `PUBLIC_ACCOUNT_DELETION_URL`

AdMob IDs अपने AdMob account से लें। Keystore अपनी ownership में बनाकर सुरक्षित offline backup रखें; keystore/password कभी ZIP/repository में commit न करें। Legal URLs तभी भरें जब public pages में वास्तविक developer name/contact और final production data practices हों। GitHub Actions से AAB artifact तभी बनेगा जब Android tests, Firestore emulator tests और strict release audit पास होंगे।

## अभी क्या सत्यापित नहीं है

- इस runtime में Android Gradle build, Kotlin compile और Firebase Emulator tests नहीं चलाए गए।
- Firebase deployment, RazorpayX live payout, App Check production enforcement, AdMob live ad requests, public legal pages और signed AAB की पुष्टि नहीं है।
- 4,800 question records structurally present हैं, लेकिन पूरे bank की factual accuracy प्रमाणित नहीं है।
- Currency rates third-party reference source पर निर्भर हैं।
