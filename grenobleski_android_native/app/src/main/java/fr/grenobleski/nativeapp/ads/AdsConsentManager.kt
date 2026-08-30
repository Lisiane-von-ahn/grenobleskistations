package fr.grenobleski.nativeapp.ads

import android.content.Context

class AdsConsentManager(context: Context) {
    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    enum class Status {
        UNKNOWN,
        ACCEPTED,
        REJECTED,
    }

    fun currentStatus(): Status {
        val raw = prefs.getString(KEY_ADS_CONSENT, Status.UNKNOWN.name) ?: Status.UNKNOWN.name
        return runCatching { Status.valueOf(raw) }.getOrDefault(Status.UNKNOWN)
    }

    fun canRequestAds(): Boolean = currentStatus() == Status.ACCEPTED

    fun shouldPromptConsent(): Boolean = currentStatus() == Status.UNKNOWN

    fun setConsentAccepted() {
        setStatus(Status.ACCEPTED)
    }

    fun setConsentRejected() {
        setStatus(Status.REJECTED)
    }

    private fun setStatus(status: Status) {
        prefs.edit().putString(KEY_ADS_CONSENT, status.name).apply()
    }

    companion object {
        private const val PREFS_NAME = "grenobleski_ads"
        private const val KEY_ADS_CONSENT = "ads_consent_status"
    }
}
