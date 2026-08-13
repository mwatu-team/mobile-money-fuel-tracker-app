package com.mobilemoneyfueltracker.gateway.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.mobilemoneyfueltracker.gateway.GatewayApplication
import com.mobilemoneyfueltracker.gateway.network.GatewayApiFactory
import com.mobilemoneyfueltracker.gateway.network.SmsUploadRequest
import java.time.Instant

class SmsSyncWorker(
    appContext: Context,
    workerParameters: WorkerParameters,
) : CoroutineWorker(appContext, workerParameters) {
    override suspend fun doWork(): Result {
        val app = applicationContext as GatewayApplication
        val dao = app.database.smsMessageDao()
        val settings = app.settingsRepository.settings.value
        if (settings.developmentDeviceToken.isBlank() || settings.deviceIdentifier.isBlank()) {
            return Result.failure()
        }
        val api = runCatching { GatewayApiFactory.create(settings.backendBaseUrl) }
            .getOrElse { return Result.failure() }
        var shouldRetry = false
        dao.pending().forEach { message ->
            val receivedAt = Instant.ofEpochMilli(message.receivedAtEpochMillis)
            val parsed = app.smsParser.parse(message.sender, message.originalBody, receivedAt)
            dao.updateParsing(
                id = message.id,
                parserVersion = parsed.parserVersion,
                transactionType = parsed.transactionType.name,
                externalId = parsed.externalTransactionId,
                amount = parsed.amount?.toPlainString(),
                customerName = parsed.customerName,
                customerPhone = parsed.customerPhone,
                parseError = parsed.error,
            )
            runCatching {
                api.uploadSms(
                    authorization = "Bearer ${settings.developmentDeviceToken}",
                    request = SmsUploadRequest(
                        deviceIdentifier = settings.deviceIdentifier,
                        sender = message.sender,
                        body = message.originalBody,
                        receivedAt = receivedAt.toString(),
                    ),
                )
            }.onSuccess { response ->
                dao.markSynchronised(
                    id = message.id,
                    rawSmsId = response.rawSmsMessageId,
                    transactionId = response.transactionId,
                )
                app.settingsRepository.recordSuccessfulSync()
            }.onFailure { error ->
                // Never include the SMS body or token in errors shown or persisted.
                dao.markFailed(message.id, error::class.simpleName ?: "Upload failed")
                shouldRetry = true
            }
        }
        return if (shouldRetry) Result.retry() else Result.success()
    }

    companion object {
        const val UNIQUE_WORK_NAME = "mobile-money-fuel-tracker-sms-sync"
    }
}
