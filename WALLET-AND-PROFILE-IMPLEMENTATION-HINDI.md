# GK Swal — Wallet और Profile सुधार

## जो code जोड़ा गया
- Profile में display name और bio edit करके Firebase Auth + Firestore में save करना।
- Target exam preferences को Firestore में load/save करना।
- Profile के भीतर wallet balance, lifetime credited amount और recent withdrawal requests दिखाना।
- UPI withdrawal request: ₹100 minimum, ₹10,000 maximum; amount integer paise में server पर validate होता है।
- Request बनाते समय balance atomically reserve होता है; insufficient balance पर request reject होती है।
- Admin-only `adminCreditWallet` callable, idempotent reference ID के साथ।
- Admin-only `adminReviewWithdrawal` callable; `paid` केवल transfer पूरा होने के बाद mark करें, `rejected` होने पर balance refund होता है।
- Firestore rules wallet, ledger और withdrawal writes को client से block करती हैं।
- Account deletion में wallet ledger और UPI वाले withdrawal request documents भी delete होते हैं।
- Emulator tests में wallet rules और withdrawal validation/reservation cases जोड़े गए।

## जरूरी सुरक्षा/व्यावसायिक सीमाएँ
- कोई client-side "earn money" button wallet में पैसा नहीं डाल सकता। Earnings को वास्तविक, सत्यापित reward source के बाद trusted server/Admin claim से credit करें।
- यह code UPI transfer अपने-आप नहीं करता। Operator को वास्तविक UPI transfer अपने authorised bank/payment workflow से करना होगा और उसके बाद `adminReviewWithdrawal` को `action: "paid"` से call करना होगा।
- Admin callables केवल Firebase Auth custom claim `admin: true` वाले accounts के लिए हैं। Public user accounts को यह claim न दें।
- Withdrawal request जमा होने पर amount balance से reserve हो जाता है। Rejected request पर refund स्वतः होता है।
- Production deployment के लिए Firebase Blaze billing/Functions eligibility, App Check enforcement, Firestore indexes/rules और actual emulator tests verify करें।

## Deployment commands (अपने project owner environment में)
```bash
npm --prefix functions install
firebase deploy --only functions,firestore:rules --project YOUR_FIREBASE_PROJECT_ID
```

यह ZIP किसी Firebase project में खुद deployment नहीं कर सकती; deployment owner के Firebase credentials और billing settings से करना होगा।
