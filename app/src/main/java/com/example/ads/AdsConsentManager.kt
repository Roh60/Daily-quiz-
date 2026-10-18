package com.example.ads

import android.app.Activity
import android.content.Context
import androidx.compose.runtime.mutableStateOf
import com.google.android.gms.ads.MobileAds
import com.google.android.ump.ConsentInformation
import com.google.android.ump.ConsentRequestParameters
import com.google.android.ump.UserMessagingPlatform

/** Handles UMP consent before enabling ad requests. AdMob requests remain disabled unless UMP allows them. */
object AdsConsentManager {
    val canRequestAds = mutableStateOf(false)
    val privacyOptionsRequired = mutableStateOf(false)
    private var initialized = false

    fun requestConsent(activity: Activity) {
        val information = UserMessagingPlatform.getConsentInformation(activity)
        val params = ConsentRequestParameters.Builder().build()
        information.requestConsentInfoUpdate(
            activity,
            params,
            {
                privacyOptionsRequired.value = information.privacyOptionsRequirementStatus ==
                    ConsentInformation.PrivacyOptionsRequirementStatus.REQUIRED
                UserMessagingPlatform.loadAndShowConsentFormIfRequired(activity) {
                    updateAdAvailability(activity, information)
                }
            },
            {
                // UMP can retain a prior valid consent state when a refresh fails offline.
                updateAdAvailability(activity, information)
            }
        )
    }

    fun showPrivacyOptions(activity: Activity) {
        UserMessagingPlatform.showPrivacyOptionsForm(activity) {
            val information = UserMessagingPlatform.getConsentInformation(activity)
            privacyOptionsRequired.value = information.privacyOptionsRequirementStatus ==
                ConsentInformation.PrivacyOptionsRequirementStatus.REQUIRED
            updateAdAvailability(activity, information)
        }
    }

    private fun updateAdAvailability(context: Context, information: ConsentInformation) {
        val allowed = information.canRequestAds()
        if (!allowed) {
            canRequestAds.value = false
            return
        }
        if (initialized) {
            canRequestAds.value = true
            return
        }
        initialized = true
        MobileAds.initialize(context.applicationContext) {
            canRequestAds.value = true
        }
    }
}
