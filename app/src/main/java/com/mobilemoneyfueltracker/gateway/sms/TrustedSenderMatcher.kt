package com.mobilemoneyfueltracker.gateway.sms

import java.util.Locale

object TrustedSenderMatcher {
    private const val UGANDA_CALLING_CODE = "256"
    private val whitespace = Regex("\\s+")

    fun isAllowed(sender: String, trustedSenders: Set<String>): Boolean {
        if (sender.isBlank() || trustedSenders.isEmpty()) return false
        val normalizedSender = normalize(sender)
        return trustedSenders.any { normalize(it) == normalizedSender }
    }

    fun normalizeEntries(senders: Iterable<String>): Set<String> = senders
        .map(String::trim)
        .filter(String::isNotEmpty)
        .distinctBy(::normalize)
        .toSet()

    fun maskedForDisplay(sender: String): String {
        if (!isPhoneLike(sender)) return sender.trim()
        val digits = sender.filter(Char::isDigit)
        return if (digits.length < 7) "***" else "${digits.take(3)}***${digits.takeLast(3)}"
    }

    private fun normalize(sender: String): String {
        val trimmed = sender.trim()
        if (!isPhoneLike(trimmed)) {
            return "id:${whitespace.replace(trimmed, " ").uppercase(Locale.ROOT)}"
        }

        var digits = trimmed.filter(Char::isDigit)
        if (digits.startsWith("00")) digits = digits.drop(2)
        if (digits.startsWith("0") && digits.length >= 9) {
            digits = UGANDA_CALLING_CODE + digits.drop(1)
        }
        return "phone:$digits"
    }

    private fun isPhoneLike(sender: String): Boolean =
        sender.any(Char::isDigit) && sender.all { it.isDigit() || it in "+-(). " }
}
