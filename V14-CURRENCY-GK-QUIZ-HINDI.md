# GK Swal v14 — Currency GK Quiz (Hindi)

## बदलाव
- Earn & Tools स्क्रीन से Live Currency Converter UI हटाया गया।
- उसकी जगह Currency GK Quiz जोड़ा गया है।
- 4-option multiple choice, answer checking, correct answer/explanation, score, next question और restart शामिल हैं।
- सवालों में भारत की मुद्रा (INR), अभ्यास के अनुसार $1 = ₹96, अभ्यास के अनुसार €1 = ₹108, चीन की मुद्रा की इकाई (yuan), और जापान की मुद्रा (yen) शामिल हैं।

## जरूरी तथ्य
₹96/$1 और ₹108/€1 यूज़र द्वारा दिए गए अभ्यास उदाहरण हैं। इन्हें live/current exchange rates या वित्तीय सलाह के रूप में न दिखाएँ। स्क्रीन पर स्पष्ट सूचना दी गई है कि ये अभ्यास values हैं, live rates नहीं।

## Verification
Source change made in `app/src/main/java/com/example/ui/profile/EarnToolsScreen.kt`. ZIP integrity and repository static checks should be run before release. Android Gradle build was not available in the current environment, so this change has not been compile-tested on Android.
