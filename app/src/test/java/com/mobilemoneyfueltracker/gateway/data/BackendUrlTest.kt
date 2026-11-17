package com.mobilemoneyfueltracker.gateway.data

import com.google.common.truth.Truth.assertThat
import org.junit.Assert.assertThrows
import org.junit.Test

class BackendUrlTest {
    @Test
    fun httpsIsAcceptedAndGetsATrailingSlash() {
        assertThat(BackendUrl.normalize("  https://api.example.test  ", allowCleartext = false))
            .isEqualTo("https://api.example.test/")
    }

    @Test
    fun existingTrailingSlashIsKept() {
        assertThat(BackendUrl.normalize("https://api.example.test/", allowCleartext = false))
            .isEqualTo("https://api.example.test/")
    }

    @Test
    fun cleartextIsRejectedInReleaseMode() {
        assertThrows(IllegalArgumentException::class.java) {
            BackendUrl.normalize("http://api.example.test/", allowCleartext = false)
        }
    }

    @Test
    fun cleartextIsAllowedOnlyWhenExplicitlyPermitted() {
        assertThat(BackendUrl.normalize("http://10.0.2.2:8000", allowCleartext = true))
            .isEqualTo("http://10.0.2.2:8000/")
    }

    @Test
    fun garbageIsRejectedInBothModes() {
        for (allow in listOf(true, false)) {
            for (bad in listOf("", "   ", "api.example.test", "ftp://api.example.test/", "https://")) {
                assertThrows(IllegalArgumentException::class.java) { BackendUrl.normalize(bad, allow) }
            }
        }
    }

    @Test
    fun releaseAlwaysUsesTheCompiledUrlEvenIfAnotherWasStored() {
        assertThat(
            BackendUrl.resolve(
                stored = "https://old-staging.example.test/",
                compiledDefault = "https://prod.example.test/",
                allowOverride = false,
            ),
        ).isEqualTo("https://prod.example.test/")
    }

    @Test
    fun debugUsesTheStoredUrlWhenValid() {
        assertThat(
            BackendUrl.resolve("http://10.0.2.2:9000/", "http://10.0.2.2:8000/", allowOverride = true),
        ).isEqualTo("http://10.0.2.2:9000/")
    }

    @Test
    fun debugFallsBackToTheDefaultWhenNothingOrJunkIsStored() {
        assertThat(BackendUrl.resolve(null, "http://10.0.2.2:8000/", true)).isEqualTo("http://10.0.2.2:8000/")
        assertThat(BackendUrl.resolve("nonsense", "http://10.0.2.2:8000/", true)).isEqualTo("http://10.0.2.2:8000/")
    }
}
