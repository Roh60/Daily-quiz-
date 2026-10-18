# GK Swal — अंतिम रिलीज़ से पहले जरूरी काम (Hindi)

## ZIP में कोड से किया गया काम
- Current Affairs स्क्रीन अब PIB (भारत सरकार) के आधिकारिक प्रेस-रिलीज़ RSS से headline, उपलब्ध publication date, छोटा summary और original article link लोड करने की कोशिश करती है। इंटरनेट बंद/फीड विफल होने पर fallback संदेश दिखता है। आधिकारिक feed URL: https://pib.gov.in/RssMain.aspx?ModId=6&Lang=1&Regid=1
- Question-bank integrity test मौजूद है; यह data structure जाँचता है, तथ्य-सत्यता नहीं।

## अकाउंट/डिवाइस के बिना पूरे नहीं हो सकते
1. **Firebase:** `app/google-services.json`, package ID और Google Sign-In SHA fingerprints अपने Firebase project से मिलाएँ। Firestore rules/functions deploy करें; Functions deployment से पहले billing plan/cost जाँचें। App Check enforcement को सफल device verification से पहले चालू न करें।
2. **Privacy/Account deletion:** HTML files अभी templates हैं। `[ADD ...]` placeholders बदलें, असली data collection/SDK व्यवहार के अनुसार policy लिखें, public HTTPS URLs पर host करें और Play Console में दोनों links डालें। Account deletion को production Firebase पर सचमुच चलाकर test करें। Google Play के अनुसार account बनाने वाले ऐप में in-app deletion path और web deletion request link दोनों चाहिए: https://support.google.com/googleplay/android-developer/answer/13327111
3. **AdMob + UMP:** Publisher account, app ID और ad-unit IDs आवश्यक हैं। SDK, UMP consent flow, test ads, child/age audience configuration और Data safety disclosures implement/verify होने तक ads को active न बताएं।
4. **Question fact-checking:** 4,800 का target count, 4,800 fact-verified होने के बराबर नहीं है। हर सवाल का सही उत्तर और explanation official/reliable source से verify करें; Current Affairs के लिए publication date और source link रखें। Duplicate/ambiguous questions भी human review से हटाएँ।
5. **Build/AAB:** इस ZIP में Gradle wrapper scripts/JAR नहीं हैं और यहाँ Android build run नहीं हुआ। GitHub Actions hosted build चलाएँ; सभी tests pass होने के बाद ही signed AAB को internal testing track में test करें। Upload keystore/passwords को निजी रखें; chat या repository में न भेजें।
6. **Social features:** Callable Firebase Functions deploy और real project पर test होने के बाद ही likes, votes, comments, follow/unfollow, post delete और account delete को production-ready मानें।
7. **Play Console:** Data safety, target audience/content rating, app access, ads declaration, privacy links, account deletion URL, package ID/versionCode और signing details वास्तविक app व्यवहार के मुताबिक भरें।

## ईमानदार स्थिति
यह checklist किसी Firebase deployment, public hosting, fact-checking, AdMob account setup, signed AAB या Play Store approval का दावा नहीं करती। ये आपके developer accounts/credentials और वास्तविक device tests पर निर्भर हैं।


## Legal pages publish karne ka added workflow
- Pehle `legal/privacy-policy-template.html` aur `legal/delete-account.html` mein developer ka asli naam, support email, retention details aur production SDK data practices complete karein. Placeholders ko guess karke replace na karein.
- GitHub repository Settings → Pages mein source ko **GitHub Actions** par set karein.
- `Actions → Publish GK Swal legal pages → Run workflow` chalayein. Workflow placeholder milne par publish karne se pehle fail ho jayega.
- Deploy ke baad workflow output mein `page_url` se base URL lein; Privacy URL ke end mein `privacy-policy.html` aur deletion URL ke end mein `delete-account.html` jodein. Phir repository Variables mein `PUBLIC_PRIVACY_POLICY_URL` aur `PUBLIC_ACCOUNT_DELETION_URL` set karein. Ye variables app build mein links dikhane aur signed release gate validate karne ke liye use honge.
- Signed AAB ke liye production AdMob IDs aur keystore secrets bhi alag se configure karne honge.
- Account-deletion service ko production mein test karne ke baad `legal/delete-account.html` se “Developer setup required before publication” notice hataayein; uske bina legal-pages workflow jaan-boojhkar deploy nahi karega.
