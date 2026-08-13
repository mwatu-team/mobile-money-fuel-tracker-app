package com.mobilemoneyfueltracker.gateway.parser

import com.google.common.truth.Truth.assertThat
import java.math.BigDecimal
import java.time.Instant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.junit.Test

@Serializable
private data class ParserFixture(
    val name: String,
    val sender: String,
    val body: String,
    @SerialName("received_at") val receivedAt: String,
    @SerialName("expected_type") val expectedType: String,
    val successful: Boolean,
    @SerialName("transaction_id") val transactionId: String? = null,
    val amount: String? = null,
    @SerialName("customer_name") val customerName: String? = null,
    @SerialName("customer_phone") val customerPhone: String? = null,
    @SerialName("payment_message") val paymentMessage: String? = null,
    @SerialName("till_number") val tillNumber: String? = null,
    @SerialName("transaction_time") val transactionTime: String? = null,
)

class FictionalSmsParserTest {
    private val parser = FictionalSmsParser()
    private val fixtures: List<ParserFixture> = javaClass.classLoader!!
        .getResourceAsStream("parser-fixtures.json")!!
        .bufferedReader()
        .use { Json.decodeFromString(it.readText()) }

    @Test
    fun sharedFixturesHaveTheSameExpectedBehaviourAsTheBackend() {
        fixtures.forEach { fixture ->
            val result = parser.parse(
                fixture.sender,
                fixture.body,
                Instant.parse(fixture.receivedAt),
            )
            assertThat(result.transactionType.name).isEqualTo(fixture.expectedType)
            assertThat(result.successful).isEqualTo(fixture.successful)
            assertThat(result.originalBody).isEqualTo(fixture.body)
            fixture.transactionId?.let {
                assertThat(result.externalTransactionId).isEqualTo(it)
                assertThat(result.amount).isEqualTo(BigDecimal(fixture.amount!!))
            }
            fixture.customerName?.let {
                assertThat(result.customerName).isEqualTo(it)
                assertThat(result.customerPhone).isEqualTo(fixture.customerPhone)
                assertThat(result.paymentMessage).isEqualTo(fixture.paymentMessage)
                assertThat(result.tillNumber).isEqualTo(fixture.tillNumber)
                assertThat(result.transactionTime).isEqualTo(Instant.parse(fixture.transactionTime))
            }
        }
    }

    @Test
    fun duplicateInputProducesIdenticalResult() {
        val fixture = fixtures.first()
        val first = parser.parse(fixture.sender, fixture.body, Instant.parse(fixture.receivedAt))
        val second = parser.parse(fixture.sender, fixture.body, Instant.parse(fixture.receivedAt))
        assertThat(second).isEqualTo(first)
    }

    @Test
    fun multipartBodyIsParsedAfterSegmentsAreJoined() {
        val multipart = fixtures.first { it.name.contains("multipart") }
        assertThat(
            parser.parse(multipart.sender, multipart.body, Instant.parse(multipart.receivedAt)).successful,
        ).isTrue()
    }
}
