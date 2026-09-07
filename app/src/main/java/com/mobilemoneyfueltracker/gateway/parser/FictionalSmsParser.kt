package com.mobilemoneyfueltracker.gateway.parser

import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class FictionalSmsParser(private val stationZone: ZoneId = ZoneId.of("Africa/Kampala")) : SmsParser {
    private val payment = Regex(
        """^You\s+have\s+received\s+([A-Z]{3})\s+([\d,]+(?:\.\d{1,2})?)\s+from\s+(.+?)\s+at\s+(\d{4}-\d{2}-\d{2}\s+\d{2}:\d{2}:\d{2})\.\s*Fee:\s*([\d,]+(?:\.\d{1,2})?)\.\s*Message:\s*(.*?)\s*Till:\s*(\d+)\.\s*New\s+balance\s+is:\s*([A-Z]{3})\s+([\d,]+(?:\.\d{1,2})?)\.\s*Transaction\s+ID:\s*([A-Z0-9-]+)\.(?:\s*Download\s+MoMo\s+App\s+\S+\s+to\s+get\s+[^.]+\.)?$""",
        RegexOption.IGNORE_CASE,
    )
    private val paymentWithPhone = Regex(
        """^You\s+have\s+received\s+([A-Z]{3})\s+([\d,]+(?:\.\d{1,2})?)\s+from\s+(.+?),\s*(\+?\d+)\s+on\s+(\d{4}-\d{2}-\d{2}\s+\d{2}:\d{2}:\d{2})\.\s*Fee:\s*([\d,]+(?:\.\d{1,2})?)\.\s*Reason:\s*(.*?)\.\s*New\s+balance:\s*([A-Z]{3})\s+([\d,]+(?:\.\d{1,2})?)\.\s*ID:\s*([A-Z0-9-]+)\.(?:\s*Download\s+MoMo\s+App\s+\S+\s+to\s+get\s+[^.]+\.)?$""",
        RegexOption.IGNORE_CASE,
    )
    private val reversal = Regex(
        """^Payment\s+([A-Z0-9-]+)\s+of\s+([A-Z]{3})\s+([\d,]+(?:\.\d{1,2})?)\s+has\s+been\s+reversed\.?$""",
        RegexOption.IGNORE_CASE,
    )
    private val dateFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")

    override fun parse(sender: String, body: String, receivedAt: Instant): ParsingResult {
        val normalized = body.trim().split(Regex("\\s+")).joinToString(" ")
        payment.matchEntire(normalized)?.let { match ->
            return runCatching {
                val currency = match.groupValues[1].uppercase()
                require(match.groupValues[8].uppercase() == currency) {
                    "Payment and balance currencies do not match"
                }
                BigDecimal(match.groupValues[5].replace(",", ""))
                BigDecimal(match.groupValues[9].replace(",", ""))
                ParsingResult(
                    originalSender = sender,
                    originalBody = body,
                    receivedAt = receivedAt,
                    transactionType = ParsedTransactionType.CUSTOMER_PAYMENT,
                    successful = true,
                    currency = currency,
                    amount = BigDecimal(match.groupValues[2].replace(",", "")),
                    customerName = match.groupValues[3].trim(),
                    paymentMessage = match.groupValues[6].trim().ifBlank { null },
                    tillNumber = match.groupValues[7],
                    externalTransactionId = match.groupValues[10].uppercase(),
                    transactionTime = LocalDateTime.parse(match.groupValues[4], dateFormat)
                        .atZone(stationZone).toInstant(),
                )
            }.getOrElse { unknown(sender, body, receivedAt, "Invalid amount or date") }
        }
        paymentWithPhone.matchEntire(normalized)?.let { match ->
            return runCatching {
                val currency = match.groupValues[1].uppercase()
                require(match.groupValues[8].uppercase() == currency) {
                    "Payment and balance currencies do not match"
                }
                BigDecimal(match.groupValues[6].replace(",", ""))
                BigDecimal(match.groupValues[9].replace(",", ""))
                ParsingResult(
                    originalSender = sender,
                    originalBody = body,
                    receivedAt = receivedAt,
                    transactionType = ParsedTransactionType.CUSTOMER_PAYMENT,
                    successful = true,
                    currency = currency,
                    amount = BigDecimal(match.groupValues[2].replace(",", "")),
                    customerName = match.groupValues[3].trim(),
                    customerPhone = match.groupValues[4],
                    paymentMessage = match.groupValues[7].trim().ifBlank { null },
                    externalTransactionId = match.groupValues[10].uppercase(),
                    transactionTime = LocalDateTime.parse(match.groupValues[5], dateFormat)
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
