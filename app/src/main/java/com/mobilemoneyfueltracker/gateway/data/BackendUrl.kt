package com.mobilemoneyfueltracker.gateway.data

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

object BackendUrl {
    /**
     * Returns the URL trimmed and with a trailing slash. Release builds accept only HTTPS;
     * cleartext HTTP is allowed solely when [allowCleartext] is true (debug builds).
     * Throws [IllegalArgumentException] otherwise.
     */
    fun normalize(raw: String, allowCleartext: Boolean): String {
        val trimmed = raw.trim()
        val parsed = trimmed.toHttpUrlOrNull()
        require(parsed != null) { "Enter a valid backend URL." }
        require(parsed.isHttps || allowCleartext) { "The backend URL must use HTTPS." }
        return if (trimmed.endsWith('/')) trimmed else "$trimmed/"
    }

    /**
     * Release builds always use the URL compiled into the app. A stored value must never override
     * it, or an old staging URL would outlive the release that points at production.
     */
    fun resolve(stored: String?, compiledDefault: String, allowOverride: Boolean): String {
        if (!allowOverride || stored == null) return compiledDefault
        return runCatching { normalize(stored, allowCleartext = true) }.getOrDefault(compiledDefault)
    }
}
