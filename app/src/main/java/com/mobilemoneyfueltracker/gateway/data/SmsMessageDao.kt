package com.mobilemoneyfueltracker.gateway.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface SmsMessageDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(message: LocalSmsMessage): Long

    @Query("SELECT * FROM sms_messages ORDER BY receivedAtEpochMillis DESC")
    fun observeAll(): Flow<List<LocalSmsMessage>>

    @Query("SELECT COUNT(*) FROM sms_messages WHERE syncStatus != 'SYNCHRONISED'")
    fun observePendingCount(): Flow<Int>

    @Query("SELECT MAX(receivedAtEpochMillis) FROM sms_messages")
    fun observeLastReceivedAt(): Flow<Long?>

    @Query("SELECT * FROM sms_messages WHERE syncStatus != 'SYNCHRONISED' ORDER BY receivedAtEpochMillis LIMIT :limit")
    suspend fun pending(limit: Int = 25): List<LocalSmsMessage>

    @Query(
        """UPDATE sms_messages SET parserVersion = :parserVersion,
            parsedTransactionType = :transactionType, externalTransactionId = :externalId,
            parsedAmount = :amount, customerName = :customerName,
            customerPhone = :customerPhone, parseError = :parseError
            WHERE id = :id""",
    )
    suspend fun updateParsing(
        id: Long,
        parserVersion: String,
        transactionType: String,
        externalId: String?,
        amount: String?,
        customerName: String?,
        customerPhone: String?,
        parseError: String?,
    )

    @Query(
        """UPDATE sms_messages SET syncStatus = 'SYNCHRONISED', serverRawSmsId = :rawSmsId,
            serverTransactionId = :transactionId, lastUploadError = NULL WHERE id = :id""",
    )
    suspend fun markSynchronised(id: Long, rawSmsId: String, transactionId: String?)

    @Query("UPDATE sms_messages SET syncStatus = 'FAILED', lastUploadError = :safeError WHERE id = :id")
    suspend fun markFailed(id: Long, safeError: String)
}
