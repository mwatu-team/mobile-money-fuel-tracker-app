package com.mobilemoneyfueltracker.gateway.pairing

import com.google.common.truth.Truth.assertThat
import com.mobilemoneyfueltracker.gateway.network.EnrollRequest
import com.mobilemoneyfueltracker.gateway.network.EnrollResponse
import com.mobilemoneyfueltracker.gateway.network.GatewayApi
import com.mobilemoneyfueltracker.gateway.network.SmsUploadRequest
import com.mobilemoneyfueltracker.gateway.network.SmsUploadResponse
import java.io.IOException
import kotlinx.coroutines.test.runTest
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response

class PairingCodeTest {
    @Test
    fun stripsSeparatorsAndUppercases() {
        assertThat(PairingCode.normalize("abcd-2345")).isEqualTo("ABCD2345")
        assertThat(PairingCode.normalize(" abcd 2345 ")).isEqualTo("ABCD2345")
    }

    @Test
    fun wrongLengthIsRejectedWithoutANetworkCall() {
        assertThat(PairingCode.normalize("")).isNull()
        assertThat(PairingCode.normalize("ABC-123")).isNull()
        assertThat(PairingCode.normalize("ABCD23456")).isNull()
    }
}

class PairingRepositoryTest {
    private class FakeApi(private val behaviour: () -> EnrollResponse) : GatewayApi {
        var requests = mutableListOf<EnrollRequest>()

        override suspend fun enroll(request: EnrollRequest): EnrollResponse {
            requests += request
            return behaviour()
        }

        override suspend fun uploadSms(authorization: String, request: SmsUploadRequest): SmsUploadResponse =
            error("not used")
    }

    private val saved = mutableListOf<Triple<String, String, String>>()

    private fun repository(api: FakeApi, urls: MutableList<String> = mutableListOf()) = PairingRepository(
        backendBaseUrl = { "https://api.example.test/" },
        savePairing = { id, token, station -> saved += Triple(id, token, station) },
        apiFactory = { url -> urls += url; api },
    )

    private fun http(code: Int) = HttpException(Response.error<Any>(code, "".toResponseBody()))

    @Test
    fun successSavesTheServerCredentialsAndReturnsTheStationName() = runTest {
        val api = FakeApi { EnrollResponse("device-1", "fictional-token", "Demo station") }
        val urls = mutableListOf<String>()
        val result = repository(api, urls).pair("abcd-2345")
        assertThat(result).isEqualTo(PairingResult.Success("Demo station"))
        assertThat(saved).containsExactly(Triple("device-1", "fictional-token", "Demo station"))
        assertThat(api.requests).containsExactly(EnrollRequest("ABCD2345"))
        assertThat(urls).containsExactly("https://api.example.test/")
    }

    @Test
    fun malformedCodeNeverReachesTheNetwork() = runTest {
        val api = FakeApi { error("must not be called") }
        assertThat(repository(api).pair("123")).isEqualTo(PairingResult.InvalidCode)
        assertThat(api.requests).isEmpty()
        assertThat(saved).isEmpty()
    }

    @Test
    fun serverRejectionsMapToTheRightResultAndSaveNothing() = runTest {
        assertThat(repository(FakeApi { throw http(400) }).pair("ABCD2345")).isEqualTo(PairingResult.InvalidCode)
        assertThat(repository(FakeApi { throw http(429) }).pair("ABCD2345")).isEqualTo(PairingResult.TooManyAttempts)
        assertThat(repository(FakeApi { throw http(500) }).pair("ABCD2345")).isEqualTo(PairingResult.ServerError)
        assertThat(saved).isEmpty()
    }

    @Test
    fun connectionFailureIsANetworkErrorNotAnInvalidCode() = runTest {
        assertThat(repository(FakeApi { throw IOException("offline") }).pair("ABCD2345"))
            .isEqualTo(PairingResult.NetworkError)
        assertThat(saved).isEmpty()
    }

    @Test
    fun failureToStoreTheTokenIsReportedAndNotMistakenForSuccess() = runTest {
        val api = FakeApi { EnrollResponse("device-1", "fictional-token", "Demo station") }
        val repository = PairingRepository(
            backendBaseUrl = { "https://api.example.test/" },
            savePairing = { _, _, _ -> error("keystore unavailable") },
            apiFactory = { api },
        )
        assertThat(repository.pair("ABCD2345")).isEqualTo(PairingResult.ServerError)
    }
}
