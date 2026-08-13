package com.mobilemoneyfueltracker.gateway.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

enum class LocalSyncStatus { PENDING, SYNCHRONISED, FAILED }

@Entity(
    tableName = "sms_messages",
    indices = [Index(value = ["bodyHash"], unique = true)],
)
data class LocalSmsMessage(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sender: String,
    val originalBody: String,
    val receivedAtEpochMillis: Long,
    val bodyHash: String,
    val parserVersion: String? = null,
    val parsedTransactionType: String? = null,
    val externalTransactionId: String? = null,
    val parsedAmount: String? = null,
    val customerName: String? = null,
    val customerPhone: String? = null,
    val parseError: String? = null,
    val syncStatus: LocalSyncStatus = LocalSyncStatus.PENDING,
    val serverRawSmsId: String? = null,
    val serverTransactionId: String? = null,
    val lastUploadError: String? = null,
    val createdAtEpochMillis: Long = System.currentTimeMillis(),
)
