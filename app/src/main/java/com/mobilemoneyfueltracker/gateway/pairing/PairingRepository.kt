package com.mobilemoneyfueltracker.gateway.pairing

import com.mobilemoneyfueltracker.gateway.network.EnrollRequest
import com.mobilemoneyfueltracker.gateway.network.GatewayApi
import com.mobilemoneyfueltracker.gateway.network.GatewayApiFactory
import java.io.IOException
import kotlinx.coroutines.CancellationException
import retrofit2.HttpException

sealed interface PairingResult {
    data class Success(val stationName: String) : PairingResult

    /** Wrong, expired, already used or revoked code. The server gives one message for all of them. */
    data object InvalidCode : PairingResult

    data object TooManyAttempts : PairingResult

    data object NetworkError : PairingResult

    data object ServerError : PairingResult
}

object PairingCode {
    const val LENGTH = 8

    /** Strips spaces and dashes and upper-cases. Returns null unless exactly [LENGTH] characters remain. */
    fun normalize(raw: String): String? {
        val cleaned = raw.filter { it.isLetterOrDigit() }.uppercase()
        return cleaned.takeIf { it.length == LENGTH }
    }
}

class PairingRepository(
    private val backendBaseUrl: () -> String,
    private val savePairing: (deviceIdentifier: String, deviceToken: String, stationName: String) -> Unit,
    private val apiFactory: (String) -> GatewayApi = GatewayApiFactory::create,
) {
    suspend fun pair(rawCode: String): PairingResult {
        val code = PairingCode.normalize(rawCode) ?: return PairingResult.InvalidCode
        return try {
            val response = apiFactory(backendBaseUrl()).enroll(EnrollRequest(code))
            savePairing(response.deviceIdentifier, response.deviceToken, response.stationName)
            PairingResult.Success(response.stationName)
        } catch (error: CancellationException) {
            throw error
        } catch (error: HttpException) {
            when (error.code()) {
                400 -> PairingResult.InvalidCode
                429 -> PairingResult.TooManyAttempts
                else -> PairingResult.ServerError
            }
        } catch (_: IOException) {
            PairingResult.NetworkError
        } catch (_: Exception) {
            // Includes malformed responses and failures to store the token securely.
            PairingResult.ServerError
        }
    }
}
