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
    val stationName: String = "",
    val deviceIdentifier: String = "",
    /** Held encrypted at rest. Empty until the phone is paired (or set manually in debug builds). */
    val deviceToken: String = BuildConfig.DEFAULT_GATEWAY_DEVICE_TOKEN,
    val trustedSenders: Set<String> = emptySet(),
    val parserDebugMode: Boolean = false,
    val lastSuccessfulSyncEpochMillis: Long? = null,
) {
    val isPaired: Boolean get() = deviceToken.isNotBlank() && deviceIdentifier.isNotBlank()
}

class GatewaySettingsRepository(
    context: Context,
    private val allowBackendOverride: Boolean = BuildConfig.DEBUG,
) {
    private val preferences = context.getSharedPreferences("mmft_gateway_settings", Context.MODE_PRIVATE)
    private val tokenStore = SecureTokenStore(preferences, AndroidKeystoreTokenCipher())
    private val defaultDeviceId = BuildConfig.DEFAULT_GATEWAY_DEVICE_IDENTIFIER.ifBlank {
        "android-${Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)}"
    }
    private val mutable: MutableStateFlow<GatewaySettings>
    val settings: StateFlow<GatewaySettings>

    init {
        tokenStore.migrateLegacyPlaintextToken()
        mutable = MutableStateFlow(read())
        settings = mutable.asStateFlow()
    }

    /**
     * Saves editable settings. Throws [IllegalArgumentException] for an unusable backend URL.
     * Release builds ignore the URL entirely: it is fixed at build time (see [BackendUrl.resolve]).
     */
    fun update(value: GatewaySettings) {
        val editor = preferences.edit()
        if (allowBackendOverride) {
            editor.putString("backend_url", BackendUrl.normalize(value.backendBaseUrl, allowCleartext = true))
        }
        if (value.deviceToken.isBlank()) {
            tokenStore.clear()
        } else if (value.deviceToken != mutable.value.deviceToken) {
            tokenStore.write(value.deviceToken.trim())
        }
        editor
            .putString("station_id", value.stationId.trim())
            .putString("device_identifier", value.deviceIdentifier.trim())
            .putStringSet(
                "trusted_senders",
                TrustedSenderMatcher.normalizeEntries(value.trustedSenders),
            )
            .putBoolean("parser_debug", value.parserDebugMode)
            .apply()
        mutable.value = read()
    }

    /** Stores the credentials returned by pairing, leaving every other setting untouched. */
    fun savePairing(deviceIdentifier: String, deviceToken: String, stationName: String) {
        tokenStore.write(deviceToken.trim())
        preferences.edit()
            .putString("device_identifier", deviceIdentifier.trim())
            .putString("station_name", stationName.trim())
            .apply()
        mutable.value = read()
    }

    fun recordSuccessfulSync(atEpochMillis: Long = System.currentTimeMillis()) {
        preferences.edit().putLong("last_successful_sync", atEpochMillis).apply()
        mutable.value = read()
    }

    private fun read() = GatewaySettings(
        backendBaseUrl = BackendUrl.resolve(
            stored = preferences.getString("backend_url", null),
            compiledDefault = BuildConfig.DEFAULT_BACKEND_BASE_URL,
            allowOverride = allowBackendOverride,
        ),
        stationId = preferences.getString("station_id", "")!!,
        stationName = preferences.getString("station_name", "")!!,
        deviceIdentifier = preferences.getString("device_identifier", defaultDeviceId)!!,
        deviceToken = tokenStore.read() ?: BuildConfig.DEFAULT_GATEWAY_DEVICE_TOKEN,
        trustedSenders = preferences.getStringSet("trusted_senders", emptySet())!!.toSet(),
        parserDebugMode = preferences.getBoolean("parser_debug", false),
        lastSuccessfulSyncEpochMillis = preferences.getLong("last_successful_sync", -1).takeIf { it >= 0 },
    )
}
