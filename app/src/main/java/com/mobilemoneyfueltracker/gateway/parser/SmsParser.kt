package com.mobilemoneyfueltracker.gateway.parser

import java.math.BigDecimal
import java.time.Instant

const val PARSER_VERSION = "momo-receipt-v5"

enum class ParsedTransactionType { CUSTOMER_PAYMENT, REVERSAL, UNKNOWN }

data class ParsingResult(
    val originalSender: String,
    val originalBody: String,
    val receivedAt: Instant,
    val parserVersion: String = PARSER_VERSION,
    val transactionType: ParsedTransactionType,
    val successful: Boolean,
    val externalTransactionId: String? = null,
    val amount: BigDecimal? = null,
    val currency: String = "UGX",
    val customerName: String? = null,
    val customerPhone: String? = null,
    val paymentMessage: String? = null,
    val tillNumber: String? = null,
    val transactionTime: Instant? = null,
    val error: String? = null,
)

fun interface SmsParser {
    fun parse(sender: String, body: String, receivedAt: Instant): ParsingResult
}
