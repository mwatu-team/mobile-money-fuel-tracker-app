package com.mobilemoneyfueltracker.gateway.parser

import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class FictionalSmsParser(private val stationZone: ZoneId = ZoneId.of("Africa/Kampala")) : SmsParser {
    private val payment = Regex(
        """^You\s+have\s+received\s+([A-Z]{3})\s+([\d,]+(?:\.\d{1,2})?)\s+from\s+(.+?)\s+((?:\+?256|0)\d{9})\.\s*Transaction\s+ID:\s*([A-Z0-9-]+)\.\s*Date:\s*(\d{2}/\d{2}/\d{4}\s+\d{2}:\d{2})\.?$""",
        RegexOption.IGNORE_CASE,
    )
    private val reversal = Regex(
        """^Payment\s+([A-Z0-9-]+)\s+of\s+([A-Z]{3})\s+([\d,]+(?:\.\d{1,2})?)\s+has\s+been\s+reversed\.?$""",
        RegexOption.IGNORE_CASE,
    )
    private val dateFormat = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")

    override fun parse(sender: String, body: String, receivedAt: Instant): ParsingResult {
        val normalized = body.trim().split(Regex("\\s+")).joinToString(" ")
        payment.matchEntire(normalized)?.let { match ->
            return runCatching {
                ParsingResult(
                    originalSender = sender,
                    originalBody = body,
                    receivedAt = receivedAt,
                    transactionType = ParsedTransactionType.CUSTOMER_PAYMENT,
                    successful = true,
                    currency = match.groupValues[1].uppercase(),
                    amount = BigDecimal(match.groupValues[2].replace(",", "")),
                    customerName = match.groupValues[3].trim(),
                    customerPhone = match.groupValues[4],
                    externalTransactionId = match.groupValues[5].uppercase(),
                    transactionTime = LocalDateTime.parse(match.groupValues[6], dateFormat)
                        .atZone(stationZone).toInstant(),
                )
            }.getOrElse { unknown(sender, body, receivedAt, "Invalid amount or date") }
        }
        reversal.matchEntire(normalized)?.let { match ->
            return runCatching {
                ParsingResult(
                    originalSender = sender,
                    originalBody = body,
                    receivedAt = receivedAt,
                    transactionType = ParsedTransactionType.REVERSAL,
                    successful = true,
                    externalTransactionId = match.groupValues[1].uppercase(),
                    currency = match.groupValues[2].uppercase(),
                    amount = BigDecimal(match.groupValues[3].replace(",", "")),
                    transactionTime = receivedAt,
                )
            }.getOrElse { unknown(sender, body, receivedAt, "Invalid reversal amount") }
        }
        val error = if (
            normalized.contains("you have received", ignoreCase = true) &&
            !normalized.contains("transaction id", ignoreCase = true)
        ) "Missing transaction ID" else "Unrecognised fictional SMS format"
        return unknown(sender, body, receivedAt, error)
    }

    private fun unknown(sender: String, body: String, receivedAt: Instant, error: String) =
        ParsingResult(
            originalSender = sender,
            originalBody = body,
            receivedAt = receivedAt,
            transactionType = ParsedTransactionType.UNKNOWN,
            successful = false,
            transactionTime = receivedAt,
            error = error,
        )
}
