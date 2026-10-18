package com.example.ads

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.unit.dp
import com.example.BuildConfig
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView

/** Banner appears only after UMP says ad requests are permitted and an ad-unit ID is configured. */
@Composable
fun AdBanner() {
    if (!AdsConsentManager.canRequestAds.value || BuildConfig.ADMOB_BANNER_AD_UNIT_ID.isBlank()) return
    val context = LocalContext.current
    val adView = remember(context) {
        AdView(context).apply {
            setAdSize(AdSize.BANNER)
            adUnitId = BuildConfig.ADMOB_BANNER_AD_UNIT_ID
            loadAd(AdRequest.Builder().build())
        }
    }
    DisposableEffect(adView) { onDispose { adView.destroy() } }
    Box(Modifier.fillMaxWidth().height(50.dp)) {
        AndroidView(factory = { adView }, modifier = Modifier.fillMaxWidth())
    }
}
