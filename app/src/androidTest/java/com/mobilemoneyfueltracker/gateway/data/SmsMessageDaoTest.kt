package com.mobilemoneyfueltracker.gateway.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SmsMessageDaoTest {
    private lateinit var database: GatewayDatabase
    private lateinit var dao: SmsMessageDao

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, GatewayDatabase::class.java).build()
        dao = database.smsMessageDao()
    }

    @After
    fun tearDown() = database.close()

    @Test
    fun originalMessagePersistsAndDuplicateHashIsIgnored() = runTest {
        val message = LocalSmsMessage(
            sender = "FICTIONAL-MOMO",
            originalBody = "Original fictional body",
            receivedAtEpochMillis = 1234L,
            bodyHash = "hash",
        )
        val id = dao.insert(message)
        assertThat(id).isGreaterThan(0)
        assertThat(dao.insert(message)).isEqualTo(-1)
        assertThat(dao.observeAll().first().single().originalBody)
            .isEqualTo("Original fictional body")
        assertThat(dao.observePendingCount().first()).isEqualTo(1)
    }

    @Test
    fun parsedCustomerNameIsStoredForReceiptsWithoutPhoneNumbers() = runTest {
        val id = dao.insert(
            LocalSmsMessage(
                sender = "FICTIONAL-MOMO",
                originalBody = "Original fictional body",
                receivedAtEpochMillis = 1234L,
                bodyHash = "name-hash",
            ),
        )

        dao.updateParsing(
            id = id,
            parserVersion = "momo-receipt-v4",
            transactionType = "CUSTOMER_PAYMENT",
            externalId = "42670000001",
            amount = "690000",
            customerName = "ALICE SAMPLE",
            customerPhone = null,
            parseError = null,
        )

        assertThat(dao.observeAll().first().single().customerName).isEqualTo("ALICE SAMPLE")
    }
}
