package com.mobilemoneyfueltracker.gateway.data

import android.content.Context
import android.provider.Settings
import com.mobilemoneyfueltracker.gateway.BuildConfig
import com.mobilemoneyfueltracker.gateway.sms.TrustedSenderMatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class GatewaySettings(
    val backendBaseUrl: String = BuildConfig.DEFAULT_BACKEND_BASE_URL,
    val stationId: String = "",
    val deviceIdentifier: String = "",
    val developmentDeviceToken: String = BuildConfig.DEFAULT_GATEWAY_DEVICE_TOKEN,
    val trustedSenders: Set<String> = emptySet(),
    val parserDebugMode: Boolean = false,
    val lastSuccessfulSyncEpochMillis: Long? = null,
)

class GatewaySettingsRepository(context: Context) {
    private val preferences = context.getSharedPreferences("mmft_gateway_settings", Context.MODE_PRIVATE)
    private val defaultDeviceId = BuildConfig.DEFAULT_GATEWAY_DEVICE_IDENTIFIER.ifBlank {
        "android-${Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)}"
    }
    private val mutable = MutableStateFlow(read())
    val settings: StateFlow<GatewaySettings> = mutable.asStateFlow()

    fun update(value: GatewaySettings) {
        require(value.backendBaseUrl.startsWith("http://") || value.backendBaseUrl.startsWith("https://"))
        preferences.edit()
            .putString("backend_url", value.backendBaseUrl.trim().let { if (it.endsWith('/')) it else "$it/" })
            .putString("station_id", value.stationId.trim())
            .putString("device_identifier", value.deviceIdentifier.trim())
            .putString("development_device_token", value.developmentDeviceToken)
            .putStringSet(
                "trusted_senders",
                TrustedSenderMatcher.normalizeEntries(value.trustedSenders),
            )
            .putBoolean("parser_debug", value.parserDebugMode)
            .apply()
        mutable.value = read()
    }

    fun recordSuccessfulSync(atEpochMillis: Long = System.currentTimeMillis()) {
        preferences.edit().putLong("last_successful_sync", atEpochMillis).apply()
        mutable.value = read()
    }

    private fun read() = GatewaySettings(
        backendBaseUrl = preferences.getString("backend_url", BuildConfig.DEFAULT_BACKEND_BASE_URL)!!,
        stationId = preferences.getString("station_id", "")!!,
        deviceIdentifier = preferences.getString("device_identifier", defaultDeviceId)!!,
        developmentDeviceToken = preferences.getString("development_device_token", BuildConfig.DEFAULT_GATEWAY_DEVICE_TOKEN)!!,
        trustedSenders = preferences.getStringSet("trusted_senders", emptySet())!!.toSet(),
        parserDebugMode = preferences.getBoolean("parser_debug", false),
        lastSuccessfulSyncEpochMillis = preferences.getLong("last_successful_sync", -1).takeIf { it >= 0 },
    )
}
