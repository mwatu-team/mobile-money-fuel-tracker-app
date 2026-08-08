package com.mobilemoneyfueltracker.gateway.data

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.mobilemoneyfueltracker.gateway.sync.SmsSyncWorker
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class SmsQueueRepository(
    private val context: Context,
    private val dao: SmsMessageDao,
) {
    val messages = dao.observeAll()
    val pendingCount = dao.observePendingCount()
    val lastReceivedAt = dao.observeLastReceivedAt()

    suspend fun persistIncoming(sender: String, originalBody: String, receivedAtEpochMillis: Long): Long =
        withContext(Dispatchers.IO) {
            val hash = deterministicHash(sender, originalBody, receivedAtEpochMillis)
            val id = dao.insert(
                LocalSmsMessage(
                    sender = sender,
                    originalBody = originalBody,
                    receivedAtEpochMillis = receivedAtEpochMillis,
                    bodyHash = hash,
                ),
            )
            enqueueSync()
            id
        }

    fun enqueueSync() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
        val request = OneTimeWorkRequestBuilder<SmsSyncWorker>()
            .setConstraints(constraints)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            SmsSyncWorker.UNIQUE_WORK_NAME,
            ExistingWorkPolicy.APPEND_OR_REPLACE,
            request,
        )
    }

    companion object {
        fun deterministicHash(sender: String, body: String, receivedAtEpochMillis: Long): String {
            val material = "$sender\u001f$body\u001f$receivedAtEpochMillis"
            return MessageDigest.getInstance("SHA-256")
                .digest(material.toByteArray())
                .joinToString("") { "%02x".format(it) }
        }
    }
}
